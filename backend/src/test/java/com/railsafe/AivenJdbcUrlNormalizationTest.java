package com.railsafe;

import com.railsafe.config.AivenJdbcUrlEnvironmentPostProcessor;
import com.railsafe.config.AivenJdbcUrlEnvironmentPostProcessor.NormalizedUrl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the Aiven service-URI conversion used when DB_URL comes from the environment. */
class AivenJdbcUrlNormalizationTest {

    @Test
    void convertsShortAivenUriAndKeepsCredentialsOutOfTheJdbcUrl() {
        NormalizedUrl result = AivenJdbcUrlEnvironmentPostProcessor.normalize(
                "mysql://example_user:example_password@db.example.aivencloud.com:17447/defaultdb?ssl-mode=REQUIRED");

        assertNotNull(result);
        assertTrue(result.url().startsWith("jdbc:mysql://db.example.aivencloud.com:17447/defaultdb?"));
        assertFalse(result.url().contains("example_user"));
        assertFalse(result.url().contains("example_password"));
        assertTrue(result.url().contains("sslMode=VERIFY_CA"));
        assertTrue(result.url().contains("trustCertificateKeyStoreUrl=classpath:aiven-truststore.p12"));
        assertTrue(result.url().contains("trustCertificateKeyStoreType=PKCS12"));
        assertEquals("example_user", result.username());
        assertEquals("example_password", result.password());
    }

    @Test
    void leavesAnExistingJdbcUrlAlone() {
        assertNull(AivenJdbcUrlEnvironmentPostProcessor.normalize(
                "jdbc:mysql://db.example.aivencloud.com:17447/defaultdb?sslMode=VERIFY_CA"));
    }

    @Test
    void ignoresUnrelatedSchemes() {
        assertNull(AivenJdbcUrlEnvironmentPostProcessor.normalize("https://example.com/defaultdb"));
    }
}
