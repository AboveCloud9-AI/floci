package io.github.hectorvent.floci.services.elasticache.proxy;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first-command handshake: HELLO with AUTH, as RESP3 clients such as valkey-go, go-redis
 * and Lettuce send it, and AUTH with the user {@code default}.
 */
class AbstractRedisAuthProxyHelloTest {

    private static final String PASSWORD = "app-password";
    private static final String AUTH_TOKEN = "auth-token";
    private static final String INVALID_AUTH = "-ERR invalid username-password pair or user is disabled.\r\n";

    private ServerSocket backendServer;
    private CredentialProxy proxy;
    private int proxyPort;

    @BeforeEach
    void startProxy() throws IOException {
        backendServer = new ServerSocket(0);
        backendServer.setSoTimeout(2_000);
        try (ServerSocket freePort = new ServerSocket(0)) {
            proxyPort = freePort.getLocalPort();
        }
        proxy = new CredentialProxy("127.0.0.1", backendServer.getLocalPort());
        proxy.start(proxyPort);
    }

    @AfterEach
    void stopProxy() throws IOException {
        proxy.stop();
        backendServer.close();
    }

    @Test
    void helloWithAuthAuthenticatesAndForwardsTheRestOfTheCommand() throws Exception {
        try (Socket client = connect()) {
            send(client, "HELLO", "3", "AUTH", "app", PASSWORD, "SETNAME", "worker-1");

            try (Socket backend = backendServer.accept()) {
                String forwarded = resp("HELLO", "3", "SETNAME", "worker-1");
                assertEquals(forwarded, read(backend.getInputStream(), forwarded.length()),
                        "the backend gets HELLO without the credentials");

                String helloReply = "%1\r\n+proto\r\n:3\r\n";
                backend.getOutputStream().write(helloReply.getBytes(StandardCharsets.UTF_8));
                backend.getOutputStream().flush();
                assertEquals(helloReply, read(client.getInputStream(), helloReply.length()),
                        "the client gets the backend's own HELLO reply");
            }
        }
    }

    @Test
    void helloWithAWrongPasswordIsRefusedWithoutReachingTheBackend() throws Exception {
        try (Socket client = connect()) {
            send(client, "HELLO", "3", "AUTH", "app", "wrong");

            assertEquals(INVALID_AUTH, read(client.getInputStream(), INVALID_AUTH.length()));
        }
        assertThrows(SocketTimeoutException.class, () -> backendServer.accept().close());
    }

    @Test
    void aRefusedLoginLeavesTheConnectionOpenForARetry() throws Exception {
        try (Socket client = connect()) {
            // a client that pipelines its handshake, as valkey-go does, gets an error per command
            send(client, "HELLO", "3", "AUTH", "app", "wrong");
            send(client, "CLIENT", "SETINFO", "LIB-NAME", "valkey-go");
            assertEquals(INVALID_AUTH, read(client.getInputStream(), INVALID_AUTH.length()));
            assertEquals("-NOAUTH Authentication required.\r\n", readLine(client.getInputStream()));

            send(client, "HELLO", "3", "AUTH", "app", PASSWORD);
            try (Socket backend = backendServer.accept()) {
                String forwarded = resp("HELLO", "3");
                assertEquals(forwarded, read(backend.getInputStream(), forwarded.length()));
            }
        }
    }

    @Test
    void helloWithoutAuthIsRefusedButTheConnectionStaysOpenForAuth() throws Exception {
        try (Socket client = connect()) {
            send(client, "HELLO", "3");
            String line = readLine(client.getInputStream());
            assertTrue(line.startsWith("-NOAUTH HELLO must be called with the client already authenticated"), line);

            send(client, "AUTH", "app", PASSWORD);
            assertEquals("+OK\r\n", read(client.getInputStream(), 5));
            send(client, "PING");
            try (Socket backend = backendServer.accept()) {
                String ping = resp("PING");
                assertEquals(ping, read(backend.getInputStream(), ping.length()));
            }
        }
    }

    @Test
    void helloAuthWithoutAPasswordIsASyntaxError() throws Exception {
        try (Socket client = connect()) {
            send(client, "HELLO", "3", "AUTH", "app");

            assertEquals("-ERR Syntax error in HELLO option 'auth'\r\n",
                    readLine(client.getInputStream()));
        }
    }

    @Test
    void theDefaultUserWithTheAuthTokenLogsInLikeSingleArgumentAuth() throws Exception {
        try (Socket client = connect()) {
            send(client, "AUTH", "default", AUTH_TOKEN);
            assertEquals("+OK\r\n", read(client.getInputStream(), 5));
            backendServer.accept().close();
        }
        try (Socket client = connect()) {
            send(client, "HELLO", "2", "AUTH", "default", AUTH_TOKEN);
            try (Socket backend = backendServer.accept()) {
                String forwarded = resp("HELLO", "2");
                assertEquals(forwarded, read(backend.getInputStream(), forwarded.length()));
            }
        }
    }

    private Socket connect() throws IOException {
        Socket client = new Socket("127.0.0.1", proxyPort);
        client.setSoTimeout(2_000);
        return client;
    }

    private static void send(Socket socket, String... args) throws IOException {
        socket.getOutputStream().write(resp(args).getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }

    private static String resp(String... args) {
        StringBuilder sb = new StringBuilder("*").append(args.length).append("\r\n");
        for (String arg : args) {
            sb.append('$').append(arg.getBytes(StandardCharsets.UTF_8).length).append("\r\n").append(arg).append("\r\n");
        }
        return sb.toString();
    }

    private static String read(InputStream in, int length) throws IOException {
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            sb.append((char) c);
            if (c == '\n') {
                break;
            }
        }
        return sb.toString();
    }

    /** Accepts {@code app}/{@link #PASSWORD}, and the auth token on the single-argument form. */
    private static final class CredentialProxy extends AbstractRedisAuthProxy {
        private CredentialProxy(String backendHost, int backendPort) {
            super(Logger.getLogger(CredentialProxy.class), "test", "test", "hello", backendHost, backendPort);
        }

        @Override
        protected boolean authRequired() {
            return true;
        }

        @Override
        protected boolean authenticate(String username, String password) {
            if (username == null) {
                return AUTH_TOKEN.equals(password);
            }
            return "app".equals(username) && PASSWORD.equals(password);
        }

        @Override
        protected void closeQuietly(Socket socket) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // The peer may already be closed.
            }
        }
    }
}
