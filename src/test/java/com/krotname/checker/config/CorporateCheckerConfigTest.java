package com.krotname.checker.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class CorporateCheckerConfigTest {
    private static final String DEFAULT_ENDPOINT = "https://suggestions.dadata.ru/suggestions/api/4_1/rs/findById/party";

    @Test
    void shouldReadTokenAndEndpointFromSystemProperties() {
        System.setProperty("DADATA_TOKEN", "property-token");
        System.setProperty("DADATA_ENDPOINT", "https://example.com/test");
        try {
            CorporateCheckerConfig config = CorporateCheckerConfig.fromEnvironmentOrResource();

            assertEquals("property-token", config.token());
            assertEquals("https://example.com/test", config.endpoint());
            assertEquals(Duration.ofSeconds(5), config.timeout());
        } finally {
            System.clearProperty("DADATA_TOKEN");
            System.clearProperty("DADATA_ENDPOINT");
        }
    }

    @Test
    void shouldFallbackToResourcePropertiesWhenPropertiesEmpty() {
        System.setProperty("DADATA_TOKEN", "");
        System.setProperty("DADATA_ENDPOINT", "");

        try {
            CorporateCheckerConfig config = CorporateCheckerConfig.fromEnvironmentOrResource();
            assertEquals("from-resource-token", config.token());
            assertEquals("http://example.local/resource", config.endpoint());
            assertEquals(Duration.ofSeconds(5), config.timeout());
        } finally {
            System.clearProperty("DADATA_TOKEN");
            System.clearProperty("DADATA_ENDPOINT");
        }
    }

    @Test
    void shouldLoadOnlyMissingEndpointFromResource() {
        System.setProperty("DADATA_TOKEN", "runtime-token");
        System.setProperty("DADATA_ENDPOINT", "");
        try {
            CorporateCheckerConfig config = CorporateCheckerConfig.fromEnvironmentOrResource();

            assertEquals("runtime-token", config.token());
            assertEquals("http://example.local/resource", config.endpoint());
        } finally {
            System.clearProperty("DADATA_TOKEN");
            System.clearProperty("DADATA_ENDPOINT");
        }
    }

    @Test
    void shouldLoadOnlyMissingTokenFromResource() {
        System.setProperty("DADATA_TOKEN", "");
        System.setProperty("DADATA_ENDPOINT", "https://example.com/runtime");
        try {
            CorporateCheckerConfig config = CorporateCheckerConfig.fromEnvironmentOrResource();

            assertEquals("from-resource-token", config.token());
            assertEquals("https://example.com/runtime", config.endpoint());
        } finally {
            System.clearProperty("DADATA_TOKEN");
            System.clearProperty("DADATA_ENDPOINT");
        }
    }

    @Test
    void shouldSetDefaultValuesFromTokenFactory() {
        CorporateCheckerConfig config = CorporateCheckerConfig.fromToken("token-from-api");

        assertEquals("token-from-api", config.token());
        assertEquals(DEFAULT_ENDPOINT, config.endpoint());
        assertEquals(Duration.ofSeconds(5), config.timeout());
    }

    @Test
    void shouldBeImmutableWhenConstructed() {
        CorporateCheckerConfig config = CorporateCheckerConfig.fromToken("x");
        assertNotNull(config.token());
        assertEquals("x", config.token());
    }

    @Test
    void shouldRejectBlankTokenFactoryValue() {
        assertThrows(IllegalArgumentException.class, () -> CorporateCheckerConfig.fromToken("  "));
    }

    @Test
    void shouldRejectInvalidEndpointOverride() {
        System.setProperty("DADATA_TOKEN", "token");
        System.setProperty("DADATA_ENDPOINT", "file:///tmp/not-http");
        try {
            assertThrows(IllegalArgumentException.class, CorporateCheckerConfig::fromEnvironmentOrResource);
        } finally {
            System.clearProperty("DADATA_TOKEN");
            System.clearProperty("DADATA_ENDPOINT");
        }
    }
}
