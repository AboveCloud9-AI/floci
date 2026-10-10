package io.github.hectorvent.floci.services.cognito;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.hectorvent.floci.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.github.hectorvent.floci.services.cognito.CognitoRestAssuredUtils.cognitoAction;
import static io.github.hectorvent.floci.services.cognito.CognitoRestAssuredUtils.cognitoJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * DescribeUserPool reports the pool's prefix domain as {@code Domain} and its custom domain as
 * {@code CustomDomain} (floci-io/floci#5412), following CreateUserPoolDomain and DeleteUserPoolDomain.
 */
@QuarkusTest
class CognitoDescribeUserPoolDomainIntegrationTest {

    @BeforeAll
    static void configureRestAssured() {
        RestAssuredJsonUtils.configureAwsContentTypes();
    }

    @Test
    void describeUserPoolReportsThePoolsPrefixAndCustomDomains() throws Exception {
        String poolId = createPool("DescribeDomainPool");
        String otherPoolId = createPool("DescribeDomainOtherPool");
        String prefixDomain = "floci-describe-" + UUID.randomUUID().toString().substring(0, 8);
        String customDomain = "auth-" + UUID.randomUUID().toString().substring(0, 8) + ".example.com";

        JsonNode pool = describePool(poolId);
        assertFalse(pool.has("Domain"), "a pool without a domain has no Domain");
        assertFalse(pool.has("CustomDomain"), "a pool without a domain has no CustomDomain");

        cognitoJson("CreateUserPoolDomain", """
                {
                  "Domain": "%s",
                  "UserPoolId": "%s"
                }
                """.formatted(prefixDomain, poolId));
        pool = describePool(poolId);
        assertEquals(prefixDomain, pool.path("Domain").asText());
        assertFalse(pool.has("CustomDomain"), "a prefix domain is not a custom domain");

        cognitoJson("CreateUserPoolDomain", """
                {
                  "Domain": "%s",
                  "UserPoolId": "%s",
                  "CustomDomainConfig": {"CertificateArn": "%s"}
                }
                """.formatted(customDomain, poolId, requestCertificate(customDomain)));
        pool = describePool(poolId);
        assertEquals(prefixDomain, pool.path("Domain").asText());
        assertEquals(customDomain, pool.path("CustomDomain").asText());

        JsonNode otherPool = describePool(otherPoolId);
        assertFalse(otherPool.has("Domain"), "another pool's domains are not reported");
        assertFalse(otherPool.has("CustomDomain"), "another pool's domains are not reported");

        deleteDomain(prefixDomain, poolId);
        pool = describePool(poolId);
        assertFalse(pool.has("Domain"), "a deleted prefix domain leaves the pool");
        assertEquals(customDomain, pool.path("CustomDomain").asText());

        deleteDomain(customDomain, poolId);
        pool = describePool(poolId);
        assertFalse(pool.has("Domain"), "no domain is left");
        assertFalse(pool.has("CustomDomain"), "a deleted custom domain leaves the pool");

        deletePool(poolId);
        deletePool(otherPoolId);
    }

    private static String createPool(String name) throws Exception {
        return cognitoJson("CreateUserPool", """
                {
                  "PoolName": "%s"
                }
                """.formatted(name)).path("UserPool").path("Id").asText();
    }

    private static JsonNode describePool(String poolId) throws Exception {
        return cognitoJson("DescribeUserPool", """
                {
                  "UserPoolId": "%s"
                }
                """.formatted(poolId)).path("UserPool");
    }

    private static String requestCertificate(String domainName) throws Exception {
        return RestAssuredJsonUtils.awsActionJson("CertificateManager", "RequestCertificate", """
                {
                  "DomainName": "%s",
                  "ValidationMethod": "DNS"
                }
                """.formatted(domainName)).path("CertificateArn").asText();
    }

    private static void deleteDomain(String domain, String poolId) {
        cognitoAction("DeleteUserPoolDomain", """
                {
                  "Domain": "%s",
                  "UserPoolId": "%s"
                }
                """.formatted(domain, poolId))
                .then()
                .statusCode(200);
    }

    private static void deletePool(String poolId) {
        cognitoAction("DeleteUserPool", """
                {
                  "UserPoolId": "%s"
                }
                """.formatted(poolId))
                .then()
                .statusCode(200);
    }
}
