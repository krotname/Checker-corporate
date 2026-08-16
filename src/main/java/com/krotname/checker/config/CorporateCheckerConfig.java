package com.krotname.checker.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

/**
 * Centralized runtime configuration for DaData integration.
 */
public final class CorporateCheckerConfig {

    public static final String DEFAULT_ENDPOINT = "https://suggestions.dadata.ru/suggestions/api/4_1/rs/findById/party";
    private static final String DEFAULT_RESOURCE = "checker.properties";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final String token;
    private final String endpoint;
    private final Duration timeout;

    private CorporateCheckerConfig(String token, String endpoint, Duration timeout) {
        this.token = requireNonBlank(token, "token");
        this.endpoint = validateEndpoint(endpoint);
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    /**
     * Loads runtime configuration with explicit runtime overrides first and a
     * resource fallback only as a second option.
     * The method always fails fast when a token is unavailable because token is mandatory
     * for any DaData call.
     */
    public static CorporateCheckerConfig fromEnvironmentOrResource() {
        // Resolve from explicit runtime settings first; fallback to resource only when explicitly absent.
        String token = resolveConfigValue("DADATA_TOKEN");
        String endpoint = resolveConfigValue("DADATA_ENDPOINT");
        Duration timeout = DEFAULT_TIMEOUT;

        if (isBlank(token) || isBlank(endpoint)) {
            Properties properties = loadFromResource();
            if (isBlank(token)) {
                token = properties.getProperty("token");
            }
            if (isBlank(endpoint)) {
                endpoint = properties.getProperty("api.endpoint");
            }
        }

        if (isBlank(token)) {
            throw new IllegalStateException("DADATA_TOKEN is missing. Set env var DADATA_TOKEN or create resources/checker.properties.");
        }
        if (isBlank(endpoint)) {
            endpoint = DEFAULT_ENDPOINT;
        }

        return new CorporateCheckerConfig(token, endpoint, timeout);
    }

    /**
     * Resolves value from system property, then environment variable.
     * A system property that is defined (even as an empty string) is authoritative and
     * disables the environment lookup, so an explicit {@code -DKEY=} override forces the
     * resource fallback regardless of the developer's shell environment.
     * This keeps local test overrides explicit and deployment overrides isolated.
     */
    private static String resolveConfigValue(String key) {
        String propertyValue = System.getProperty(key);
        if (propertyValue != null) {
            return isBlank(propertyValue) ? null : propertyValue;
        }
        String envValue = System.getenv(key);
        return isBlank(envValue) ? null : envValue;
    }

    public String token() {
        return token;
    }

    public String endpoint() {
        return endpoint;
    }

    public Duration timeout() {
        return timeout;
    }

    /**
     * Reads checker.properties from classpath when present.
     */
    private static Properties loadFromResource() {
        Properties props = new Properties();
        try (InputStream stream = CorporateCheckerConfig.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                return props;
            }
            props.load(stream);
            return props;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read checker.properties", e);
        }
    }

    /**
     * Explicit token constructor for tests and custom bootstrap paths.
     */
    public static CorporateCheckerConfig fromToken(String token) {
        Objects.requireNonNull(token, "token");
        return new CorporateCheckerConfig(token, DEFAULT_ENDPOINT, DEFAULT_TIMEOUT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String validateEndpoint(String value) {
        String normalized = requireNonBlank(value, "endpoint");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("endpoint must be a valid absolute HTTP(S) URI", e);
        }
        String scheme = uri.getScheme();
        if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("endpoint must be a valid absolute HTTP(S) URI");
        }
        return uri.toString();
    }
}
