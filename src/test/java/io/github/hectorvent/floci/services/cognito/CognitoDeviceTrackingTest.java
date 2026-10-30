package io.github.hectorvent.floci.services.cognito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.InMemoryStorage;
import io.github.hectorvent.floci.services.acm.AcmService;
import io.github.hectorvent.floci.services.lambda.LambdaService;
import io.github.hectorvent.floci.services.lambda.model.InvokeResult;
import io.github.hectorvent.floci.testing.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Device tracking: NewDeviceMetadata on sign-in, ConfirmDevice, the device list/get/forget/status
 * operations, and the {@code newDeviceUsed} flag of the PostAuthentication event.
 */
class CognitoDeviceTrackingTest {

    private static final String POST_AUTH_ARN = "arn:aws:lambda:us-east-1:000000000000:function:post-auth";
    private static final String PASSWORD = "Perm1234!";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LambdaService lambdaService;
    private MutableClock clock;
    private CognitoService service;
    private CognitoJsonHandler handler;

    @BeforeEach
    void setUp() {
        lambdaService = mock(LambdaService.class);
        clock = new MutableClock();
        service = new CognitoService(
                new InMemoryStorage<>(), new InMemoryStorage<>(), new InMemoryStorage<>(),
                new InMemoryStorage<>(), new InMemoryStorage<>(),
                new InMemoryStorage<>(), new InMemoryStorage<>(), new InMemoryStorage<>(),
                "http://localhost:4566", "cloudfront.net",
                new RegionResolver("us-east-1", "000000000000"), lambdaService, mock(AcmService.class),
                null, null, null, clock);
        handler = new CognitoJsonHandler(service, MAPPER);
    }

    private JsonNode call(String action, String json) {
        try {
            return (JsonNode) handler.handle(action, MAPPER.readTree(json), "us-east-1").getEntity();
        } catch (AwsException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AwsException callFails(String action, String json) {
        return assertThrows(AwsException.class, () -> call(action, json));
    }

    private String createPool(boolean trackDevices, boolean onlyOnUserPrompt, boolean postAuthTrigger) {
        ObjectNode req = MAPPER.createObjectNode();
        req.put("PoolName", "devices");
        if (trackDevices) {
            ObjectNode cfg = req.putObject("DeviceConfiguration");
            cfg.put("ChallengeRequiredOnNewDevice", false);
            cfg.put("DeviceOnlyRememberedOnUserPrompt", onlyOnUserPrompt);
        }
        if (postAuthTrigger) {
            req.putObject("LambdaConfig").put("PostAuthentication", POST_AUTH_ARN);
        }
        return call("CreateUserPool", req.toString()).path("UserPool").path("Id").asText();
    }

    private String createClient(String poolId) {
        return call("CreateUserPoolClient", """
                {"UserPoolId": "%s", "ClientName": "app",
                 "ExplicitAuthFlows": ["ALLOW_USER_PASSWORD_AUTH", "ALLOW_ADMIN_USER_PASSWORD_AUTH",
                                       "ALLOW_REFRESH_TOKEN_AUTH"]}
                """.formatted(poolId)).path("UserPoolClient").path("ClientId").asText();
    }

    private void createUser(String poolId, String username) {
        call("AdminCreateUser", """
                {"UserPoolId": "%s", "Username": "%s", "MessageAction": "SUPPRESS"}
                """.formatted(poolId, username));
        call("AdminSetUserPassword", """
                {"UserPoolId": "%s", "Username": "%s", "Password": "%s", "Permanent": true}
                """.formatted(poolId, username, PASSWORD));
    }

    private JsonNode signIn(String clientId, String username, String deviceKey) {
        ObjectNode req = MAPPER.createObjectNode();
        req.put("ClientId", clientId);
        req.put("AuthFlow", "USER_PASSWORD_AUTH");
        ObjectNode params = req.putObject("AuthParameters");
        params.put("USERNAME", username);
        params.put("PASSWORD", PASSWORD);
        if (deviceKey != null) {
            params.put("DEVICE_KEY", deviceKey);
        }
        return call("InitiateAuth", req.toString()).path("AuthenticationResult");
    }

    private JsonNode confirmDevice(JsonNode auth, String deviceName) {
        return call("ConfirmDevice", """
                {"AccessToken": "%s", "DeviceKey": "%s", "DeviceName": "%s",
                 "DeviceSecretVerifierConfig": {"PasswordVerifier": "dmVyaWZpZXI=", "Salt": "c2FsdA=="}}
                """.formatted(auth.path("AccessToken").asText(),
                auth.path("NewDeviceMetadata").path("DeviceKey").asText(), deviceName));
    }

    /** Signs in on a new device and confirms it, returning the new device's key. */
    private String signInOnNewDevice(String clientId, String username, String deviceName) {
        JsonNode auth = signIn(clientId, username, null);
        confirmDevice(auth, deviceName);
        return auth.path("NewDeviceMetadata").path("DeviceKey").asText();
    }

    private JsonNode adminListDevices(String poolId, String username) {
        return call("AdminListDevices", """
                {"UserPoolId": "%s", "Username": "%s"}
                """.formatted(poolId, username)).path("Devices");
    }

    private static Map<String, String> attributes(JsonNode device) {
        Map<String, String> attrs = new java.util.HashMap<>();
        device.path("DeviceAttributes").forEach(a -> attrs.put(a.path("Name").asText(), a.path("Value").asText()));
        return attrs;
    }

    private static JsonNode accessTokenClaims(JsonNode auth) throws Exception {
        String payload = auth.path("AccessToken").asText().split("\\.")[1];
        return MAPPER.readTree(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
    }

    private List<JsonNode> postAuthenticationRequests() throws Exception {
        ArgumentCaptor<byte[]> payloads = ArgumentCaptor.forClass(byte[].class);
        verify(lambdaService, atLeastOnce()).invoke(anyString(), eq(POST_AUTH_ARN), payloads.capture(), any());
        List<JsonNode> requests = new ArrayList<>();
        for (byte[] payload : payloads.getAllValues()) {
            requests.add(MAPPER.readTree(payload).path("request"));
        }
        return requests;
    }

    private static InvokeResult ok() {
        return new InvokeResult(200, null, "{\"response\":{}}".getBytes(StandardCharsets.UTF_8), null, "req-id");
    }

    private static InvokeResult lambdaError(String errorMessage) {
        byte[] payload = ("{\"errorType\":\"Error\",\"errorMessage\":\"" + errorMessage + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return new InvokeResult(200, "Unhandled", payload, null, "req-id");
    }

    @Test
    void poolWithoutDeviceConfigurationIssuesNoDeviceMetadata() throws Exception {
        String poolId = createPool(false, false, true);
        String clientId = createClient(poolId);
        createUser(poolId, "alice");
        when(lambdaService.invoke(anyString(), eq(POST_AUTH_ARN), any(byte[].class), any())).thenReturn(ok());

        JsonNode auth = signIn(clientId, "alice", null);

        assertFalse(auth.has("NewDeviceMetadata"));
        assertFalse(accessTokenClaims(auth).has("device_key"));
        assertFalse(postAuthenticationRequests().get(0).path("newDeviceUsed").asBoolean(true));
        assertEquals(0, adminListDevices(poolId, "alice").size());
    }

    @Test
    void poolWithDeviceConfigurationIssuesNewDeviceMetadata() throws Exception {
        String poolId = createPool(true, false, true);
        String clientId = createClient(poolId);
        createUser(poolId, "alice");
        when(lambdaService.invoke(anyString(), eq(POST_AUTH_ARN), any(byte[].class), any())).thenReturn(ok());

        JsonNode auth = signIn(clientId, "alice", null);

        JsonNode metadata = auth.path("NewDeviceMetadata");
        String deviceKey = metadata.path("DeviceKey").asText();
        assertTrue(deviceKey.matches("us-east-1_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                deviceKey);
        assertTrue(metadata.path("DeviceGroupKey").asText().startsWith("-"));
        assertEquals(deviceKey, accessTokenClaims(auth).path("device_key").asText());
        assertTrue(postAuthenticationRequests().get(0).path("newDeviceUsed").asBoolean(false));
        assertEquals(0, adminListDevices(poolId, "alice").size(), "a device is listed only once confirmed");
    }

    @Test
    void adminInitiateAuthAlsoIssuesNewDeviceMetadata() {
        String poolId = createPool(true, false, false);
        String clientId = createClient(poolId);
        createUser(poolId, "alice");

        JsonNode auth = call("AdminInitiateAuth", """
                {"UserPoolId": "%s", "ClientId": "%s", "AuthFlow": "ADMIN_USER_PASSWORD_AUTH",
                 "AuthParameters": {"USERNAME": "alice", "PASSWORD": "%s"}}
                """.formatted(poolId, clientId, PASSWORD)).path("AuthenticationResult");

        assertTrue(auth.path("NewDeviceMetadata").path("DeviceKey").asText().startsWith("us-east-1_"));
    }

    @Test
    void describeUserPoolReturnsDeviceConfigurationAfterUpdate() {
        String poolId = createPool(false, false, false);
        call("UpdateUserPool", """
                {"UserPoolId": "%s", "DeviceConfiguration":
                  {"ChallengeRequiredOnNewDevice": true, "DeviceOnlyRememberedOnUserPrompt": true}}
                """.formatted(poolId));

        JsonNode cfg = call("DescribeUserPool", "{\"UserPoolId\": \"%s\"}".formatted(poolId))
                .path("UserPool").path("DeviceConfiguration");

        assertTrue(cfg.path("ChallengeRequiredOnNewDevice").asBoolean());
        assertTrue(cfg.path("DeviceOnlyRememberedOnUserPrompt").asBoolean());
    }

    @Test
    void confirmedDeviceIsListedByAdminListDevicesAndListDevices() {
        String poolId = createPool(true, false, false);
        String clientId = createClient(poolId);
        createUser(poolId, "alice");
        JsonNode auth = signIn(clientId, "alice", null);
        String deviceKey = auth.path("NewDeviceMetadata").path("DeviceKey").asText();

        JsonNode confirm = confirmDevice(auth, "Chrome on Linux");

        assertFalse(confirm.path("UserConfirmationNecessary").asBoolean(true));
        JsonNode adminDevices = adminListDevices(poolId, "alice");
        assertEquals(1, adminDevices.size());
        JsonNode device = adminDevices.get(0);
        assertEquals(deviceKey, device.path("DeviceKey").asText());
        assertEquals("Chrome on Linux", attributes(device).get("device_name"));
        assertEquals("valid", attributes(device).get("device_status"));
        assertEquals("remembered", attributes(device).get("dev:device_remembered_status"));
        assertTrue(device.path("DeviceCreateDate").isIntegralNumber());
        assertTrue(device.path("DeviceLastModifiedDate").isIntegralNumber());
        assertTrue(device.path("DeviceLastAuthenticatedDate").asLong() > 0);

        JsonNode own = call("ListDevices", "{\"AccessToken\": \"%s\"}"
                .formatted(auth.path("AccessToken").asText())).path("Devices");
        assertEquals(1, own.size());
        assertEquals(deviceKey, own.get(0).path("DeviceKey").asText());

        JsonNode got = call("GetDevice", "{\"AccessToken\": \"%s\", \"DeviceKey\": \"%s\"}"
                .formatted(auth.path("AccessToken").asText(), deviceKey)).path("Device");
        assertEquals(deviceKey, got.path("DeviceKey").asText());
        JsonNode adminGot = call("AdminGetDevice", "{\"UserPoolId\": \"%s\", \"Username\": \"alice\", \"DeviceKey\": \"%s\"}"
                .formatted(poolId, deviceKey)).path("Device");
        assertEquals(deviceKey, adminGot.path("DeviceKey").asText());
    }

    @Test
    void userPromptPoolNeedsUpdateDeviceStatusToRemember() {
        String poolId = createPool(true, true, false);
        String clientId = createClient(poolId);
        createUser(poolId, "alice");
        JsonNode auth = signIn(clientId, "alice", null);
        String deviceKey = auth.path("NewDeviceMetadata").path("DeviceKey").asText();

        assertTrue(confirmDevice(auth, "phone").path("UserConfirmationNecessary").asBoolean(false));
        assertEquals("not_remembered", attributes(adminListDevices(poolId, "alice").get(0))
                .get("dev:device_remembered_status"));

        call("UpdateDeviceStatus", """
                {"AccessToken": "%s", "DeviceKey": "%s", "DeviceRememberedStatus": "remembered"}
                """.formatted(auth.path("AccessToken").asText(), deviceKey));
        assertEquals("remembered", attributes(adminListDevices(poolId, "alice").get(0))
                .get("dev:device_remembered_status"));

        call("AdminUpdateDeviceStatus", """
                {"UserPoolId": "%s", "Username": "alice", "DeviceKey": "%s", "DeviceRememberedStatus": "not_remembered"}
                """.formatted(poolId, deviceKey));
        assertEquals("not_remembered", attributes(adminListDevices(poolId, "alice").get(0))
                .get("dev:device_remembered_status"));

        AwsException invalid = callFails("AdminUpdateDeviceStatus", """
                {"UserPoolId": "%s", "Username": "alice", "DeviceKey": "%s", "DeviceRememberedStatus": "forever"}
                """.formatted(poolId, deviceKey));
        assertEquals("InvalidParameterException", invalid.getErrorCode());
    }

    @Test
    void signInWithKnownDeviceKeyUpdatesLastAuthenticatedDate() throws Exception {
        String poolId = createPool(true, false, true);
        String clientId = createClient(poolId);
        createUser(poolId, "alice");
        when(lambdaService.invoke(anyString(), eq(POST_AUTH_ARN), any(byte[].class), any())).thenReturn(ok());
        String deviceKey = signInOnNewDevice(clientId, "alice", "laptop");
        long firstAuthenticated = adminListDevices(poolId, "alice").get(0).path("DeviceLastAuthenticatedDate").asLong();

        clock.advance(Duration.ofHours(2));
        JsonNode auth = signIn(clientId, "alice", deviceKey);

        assertFalse(auth.has("NewDeviceMetadata"));
        assertEquals(deviceKey, accessTokenClaims(auth).path("device_key").asText());
        List<JsonNode> events = postAuthenticationRequests();
        assertTrue(events.get(0).path("newDeviceUsed").asBoolean(false));
        assertFalse(events.get(1).path("newDeviceUsed").asBoolean(true));
        JsonNode devices = adminListDevices(poolId, "alice");
        assertEquals(1, devices.size());
        assertEquals(firstAuthenticated + 7200, devices.get(0).path("DeviceLastAuthenticatedDate").asLong());
    }

    @Test
    void unknownDeviceKeyAtSignInCountsAsNewDevice() {
        String poolId = createPool(true, false, false);
        String clientId = createClient(poolId);
        createUser(poolId, "alice");

        JsonNode auth = signIn(clientId, "alice", "us-east-1_00000000-0000-0000-0000-000000000000");

        assertTrue(auth.has("NewDeviceMetadata"));
    }

    @Test
    void refreshWithKnownDeviceKeyUpdatesLastAuthenticatedDate() throws Exception {
        String poolId = createPool(true, false, false);
        String clientId = createClient(poolId);
        createUser(poolId, "alice");
        JsonNode first = signIn(clientId, "alice", null);
        confirmDevice(first, "laptop");
        String deviceKey = first.path("NewDeviceMetadata").path("DeviceKey").asText();
        long before = adminListDevices(poolId, "alice").get(0).path("DeviceLastAuthenticatedDate").asLong();

        clock.advance(Duration.ofMinutes(10));
        JsonNode refreshed = call("InitiateAuth", """
                {"ClientId": "%s", "AuthFlow": "REFRESH_TOKEN_AUTH",
                 "AuthParameters": {"REFRESH_TOKEN": "%s", "DEVICE_KEY": "%s"}}
                """.formatted(clientId, first.path("RefreshToken").asText(), deviceKey))
                .path("AuthenticationResult");

        assertFalse(refreshed.has("NewDeviceMetadata"));
        assertEquals(deviceKey, accessTokenClaims(refreshed).path("device_key").asText());
        assertEquals(before + 600, adminListDevices(poolId, "alice").get(0).path("DeviceLastAuthenticatedDate").asLong());
    }

    @Test
    void forgetDeviceRemovesIt() {
        String poolId = createPool(true, false, false);
        String clientId = createClient(poolId);
        createUser(poolId, "alice");
        JsonNode auth = signIn(clientId, "alice", null);
        confirmDevice(auth, "laptop");
        String first = auth.path("NewDeviceMetadata").path("DeviceKey").asText();
        String second = signInOnNewDevice(clientId, "alice", "phone");
        assertEquals(2, adminListDevices(poolId, "alice").size());

        call("ForgetDevice", "{\"AccessToken\": \"%s\", \"DeviceKey\": \"%s\"}"
                .formatted(auth.path("AccessToken").asText(), first));
        call("AdminForgetDevice", "{\"UserPoolId\": \"%s\", \"Username\": \"alice\", \"DeviceKey\": \"%s\"}"
                .formatted(poolId, second));

        assertEquals(0, adminListDevices(poolId, "alice").size());
        AwsException gone = callFails("AdminGetDevice", "{\"UserPoolId\": \"%s\", \"Username\": \"alice\", \"DeviceKey\": \"%s\"}"
                .formatted(poolId, first));
        assertEquals("ResourceNotFoundException", gone.getErrorCode());
        assertTrue(signIn(clientId, "alice", first).has("NewDeviceMetadata"),
                "a forgotten device signs in as a new one");
    }

    @Test
    void unknownDeviceAndBadTokenAreRejected() {
        String poolId = createPool(true, false, false);
        String clientId = createClient(poolId);
        createUser(poolId, "alice");
        JsonNode auth = signIn(clientId, "alice", null);
        String accessToken = auth.path("AccessToken").asText();
        String bogus = "us-east-1_11111111-1111-1111-1111-111111111111";

        assertEquals("ResourceNotFoundException", callFails("ConfirmDevice",
                "{\"AccessToken\": \"%s\", \"DeviceKey\": \"%s\"}".formatted(accessToken, bogus)).getErrorCode());
        assertEquals("ResourceNotFoundException", callFails("GetDevice",
                "{\"AccessToken\": \"%s\", \"DeviceKey\": \"%s\"}".formatted(accessToken, bogus)).getErrorCode());
        assertEquals("ResourceNotFoundException", callFails("AdminGetDevice",
                "{\"UserPoolId\": \"%s\", \"Username\": \"alice\", \"DeviceKey\": \"%s\"}".formatted(poolId, bogus))
                .getErrorCode());
        assertEquals("ResourceNotFoundException", callFails("AdminForgetDevice",
                "{\"UserPoolId\": \"%s\", \"Username\": \"alice\", \"DeviceKey\": \"%s\"}".formatted(poolId, bogus))
                .getErrorCode());
        assertEquals("ResourceNotFoundException", callFails("AdminGetDevice",
                "{\"UserPoolId\": \"%s\", \"Username\": \"alice\", \"DeviceKey\": \"%s\"}".formatted(poolId,
                        auth.path("NewDeviceMetadata").path("DeviceKey").asText())).getErrorCode(),
                "an unconfirmed device is not visible");
        assertEquals("NotAuthorizedException", callFails("ListDevices",
                "{\"AccessToken\": \"not-a-token\"}").getErrorCode());
        assertEquals("UserNotFoundException", callFails("AdminListDevices",
                "{\"UserPoolId\": \"%s\", \"Username\": \"nobody\"}".formatted(poolId)).getErrorCode());
    }

    @Test
    void devicesAreGoneAfterAdminDeleteUser() {
        String poolId = createPool(true, false, false);
        String clientId = createClient(poolId);
        createUser(poolId, "alice");
        signInOnNewDevice(clientId, "alice", "laptop");
        assertEquals(1, adminListDevices(poolId, "alice").size());

        call("AdminDeleteUser", "{\"UserPoolId\": \"%s\", \"Username\": \"alice\"}".formatted(poolId));
        createUser(poolId, "alice");

        assertEquals(0, adminListDevices(poolId, "alice").size());
    }

    @Test
    void adminListDevicesPaginates() {
        String poolId = createPool(true, false, false);
        String clientId = createClient(poolId);
        createUser(poolId, "alice");
        Set<String> created = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            created.add(signInOnNewDevice(clientId, "alice", "device-" + i));
        }

        JsonNode firstPage = call("AdminListDevices", """
                {"UserPoolId": "%s", "Username": "alice", "Limit": 2}
                """.formatted(poolId));
        assertEquals(2, firstPage.path("Devices").size());
        String token = firstPage.path("PaginationToken").asText();
        assertFalse(token.isEmpty());

        JsonNode secondPage = call("AdminListDevices", """
                {"UserPoolId": "%s", "Username": "alice", "Limit": 2, "PaginationToken": "%s"}
                """.formatted(poolId, token));
        assertEquals(1, secondPage.path("Devices").size());
        assertFalse(secondPage.has("PaginationToken"));

        Set<String> listed = new HashSet<>();
        firstPage.path("Devices").forEach(d -> listed.add(d.path("DeviceKey").asText()));
        secondPage.path("Devices").forEach(d -> listed.add(d.path("DeviceKey").asText()));
        assertEquals(created, listed);

        assertEquals("InvalidParameterException", callFails("AdminListDevices", """
                {"UserPoolId": "%s", "Username": "alice", "Limit": 61}
                """.formatted(poolId)).getErrorCode());
    }

    /**
     * The device limit a PostAuthentication function enforces: it rejects a sign-in on a new device
     * once the user already has three confirmed devices, which blocks that sign-in.
     */
    @Test
    void postAuthenticationErrorBlocksSignInOnFourthDevice() {
        String poolId = createPool(true, false, true);
        String clientId = createClient(poolId);
        createUser(poolId, "alice");
        when(lambdaService.invoke(anyString(), eq(POST_AUTH_ARN), any(byte[].class), any())).thenAnswer(inv -> {
            JsonNode event = MAPPER.readTree((byte[]) inv.getArgument(2));
            if (!event.path("request").path("newDeviceUsed").asBoolean()) {
                return ok();
            }
            int devices = service.adminListDevices(event.path("userPoolId").asText(),
                    event.path("userName").asText(), null, null).items().size();
            return devices >= 3 ? lambdaError("device limit reached") : ok();
        });

        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            keys.add(signInOnNewDevice(clientId, "alice", "device-" + i));
        }
        assertEquals(3, adminListDevices(poolId, "alice").size());

        AwsException blocked = assertThrows(AwsException.class, () -> signIn(clientId, "alice", null));
        assertEquals("UserLambdaValidationException", blocked.getErrorCode());
        assertEquals("PostAuthentication failed with error device limit reached.", blocked.getMessage());
        assertNotNull(signIn(clientId, "alice", keys.get(0)).path("AccessToken").asText(null),
                "a known device still signs in");

        call("AdminForgetDevice", "{\"UserPoolId\": \"%s\", \"Username\": \"alice\", \"DeviceKey\": \"%s\"}"
                .formatted(poolId, keys.get(1)));
        JsonNode allowed = signIn(clientId, "alice", null);
        assertTrue(allowed.has("NewDeviceMetadata"));
    }
}
