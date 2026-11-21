package com.railsafe.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accepts the short connection URI that Aiven shows in its dashboard
 * (for example {@code mysql://user:password@host:port/db?ssl-mode=REQUIRED}) and
 * turns it into the JDBC URL the MySQL driver expects. This lets deployments set
 * DB_URL to the value copied straight from Aiven while still using the bundled
 * CA truststore. A value that is already a {@code jdbc:} URL is left untouched.
 *
 * <p>Credentials embedded in the short URI are never written back into the JDBC
 * URL. They are exposed as DB_USERNAME/DB_PASSWORD only when those variables are
 * not already configured, so dashboard secrets always win.</p>
 */
public class AivenJdbcUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "railsafeAivenJdbcUrl";

    private static final Map<String, String> REQUIRED_DRIVER_PARAMETERS = new LinkedHashMap<>();

    static {
        REQUIRED_DRIVER_PARAMETERS.put("sslMode", "VERIFY_CA");
        REQUIRED_DRIVER_PARAMETERS.put("trustCertificateKeyStoreUrl", "classpath:aiven-truststore.p12");
        REQUIRED_DRIVER_PARAMETERS.put("trustCertificateKeyStoreType", "PKCS12");
        REQUIRED_DRIVER_PARAMETERS.put("serverTimezone", "UTC");
        REQUIRED_DRIVER_PARAMETERS.put("useUnicode", "true");
        REQUIRED_DRIVER_PARAMETERS.put("characterEncoding", "utf8");
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = environment.getProperty("DB_URL");
        if (raw == null || raw.isBlank()) {
            return;
        }

        String value = raw.trim();
        if (value.regionMatches(true, 0, "jdbc:", 0, 5)) {
            return;
        }

        NormalizedUrl normalized = normalize(value);
        if (normalized == null) {
            return;
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", normalized.url());
        if (normalized.username() != null && !hasText(environment.getProperty("DB_USERNAME"))) {
            properties.put("DB_USERNAME", normalized.username());
        }
        if (normalized.password() != null && !hasText(environment.getProperty("DB_PASSWORD"))) {
            properties.put("DB_PASSWORD", normalized.password());
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    /**
     * @return the JDBC URL plus any credentials found in the service URI, or
     *         {@code null} when the value is not an Aiven-style URI.
     */
    public static NormalizedUrl normalize(String value) {
        int schemeEnd = value.indexOf("://");
        if (schemeEnd <= 0) {
            return null;
        }
        String scheme = value.substring(0, schemeEnd).toLowerCase();
        if (!"mysql".equals(scheme) && !"mysqlx".equals(scheme)) {
            return null;
        }

        String remainder = value.substring(schemeEnd + 3);
        String query = "";
        int questionMark = remainder.indexOf('?');
        if (questionMark >= 0) {
            query = remainder.substring(questionMark + 1);
            remainder = remainder.substring(0, questionMark);
        }

        String userInfo = "";
        String authority = remainder;
        int at = remainder.lastIndexOf('@');
        if (at >= 0) {
            userInfo = remainder.substring(0, at);
            authority = remainder.substring(at + 1);
        }

        String database = "";
        int slash = authority.indexOf('/');
        if (slash >= 0) {
            database = authority.substring(slash + 1);
            authority = authority.substring(0, slash);
        }
        if (authority.isEmpty()) {
            return null;
        }

        Map<String, String> parameters = parseParameters(query);
        REQUIRED_DRIVER_PARAMETERS.forEach(parameters::putIfAbsent);

        StringBuilder url = new StringBuilder("jdbc:mysql://").append(authority);
        if (!database.isEmpty()) {
            url.append('/').append(database);
        }
        url.append('?');
        boolean first = true;
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            if (!first) {
                url.append('&');
            }
            url.append(parameter.getKey()).append('=').append(parameter.getValue());
            first = false;
        }

        String username = null;
        String password = null;
        if (!userInfo.isEmpty()) {
            int colon = userInfo.indexOf(':');
            username = decode(colon < 0 ? userInfo : userInfo.substring(0, colon));
            if (colon >= 0) {
                password = decode(userInfo.substring(colon + 1));
            }
        }
        return new NormalizedUrl(url.toString(), username, password);
    }

    private static Map<String, String> parseParameters(String query) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (query.isEmpty()) {
            return parameters;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            key = normalizeParameterName(key);
            if (key.isEmpty() || "trustCertificateKeyStorePassword".equals(key)) {
                // The truststore password is supplied as a Hikari driver property, not in the URL.
                continue;
            }
            if ("sslMode".equals(key) && "REQUIRED".equalsIgnoreCase(value)) {
                value = "VERIFY_CA";
            }
            parameters.put(key, value);
        }
        return parameters;
    }

    private static String normalizeParameterName(String key) {
        return switch (key.toLowerCase()) {
            case "ssl-mode", "ssl_mode", "sslmode" -> "sslMode";
            case "servertimezone" -> "serverTimezone";
            case "useunicode" -> "useUnicode";
            case "characterencoding" -> "characterEncoding";
            case "trustcertificatekeystoreurl" -> "trustCertificateKeyStoreUrl";
            case "trustcertificatekeystoretype" -> "trustCertificateKeyStoreType";
            case "trustcertificatekeystorepassword" -> "trustCertificateKeyStorePassword";
            default -> key;
        };
    }

    private static String decode(String value) {
        if (value.indexOf('%') < 0 && value.indexOf('+') < 0) {
            return value;
        }
        // Keep a literal '+' as '+' rather than letting URLDecoder turn it into a space.
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    /** The converted URL and optional credentials recovered from the service URI. */
    public record NormalizedUrl(String url, String username, String password) {
    }
}
