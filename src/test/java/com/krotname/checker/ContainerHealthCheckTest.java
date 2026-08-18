package com.krotname.checker;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class ContainerHealthCheckTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldBuildDefaultEndpointFromConfiguredPort() {
        assertEquals("http://127.0.0.1:8080/health", ContainerHealthCheck.defaultEndpoint(null));
        assertEquals("http://127.0.0.1:8080/health", ContainerHealthCheck.defaultEndpoint("  "));
        assertEquals("http://127.0.0.1:9090/health", ContainerHealthCheck.defaultEndpoint(" 9090 "));
        assertEquals("http://127.0.0.1:8080/health", ContainerHealthCheck.defaultEndpoint("not-a-port"));
        assertEquals("http://127.0.0.1:8080/health", ContainerHealthCheck.defaultEndpoint("70000"));
    }

    @Test
    void shouldPassForHealthyEndpoint() throws IOException {
        String endpoint = startHealthEndpoint(200, "{\"status\":\"ok\"}");
        assertTrue(ContainerHealthCheck.isHealthy(endpoint, Duration.ofSeconds(2)));
    }

    @Test
    void shouldPassForFormattedHealthyPayload() throws IOException {
        String endpoint = startHealthEndpoint(200, "{\n  \"status\": \"ok\"\n}");
        assertTrue(ContainerHealthCheck.isHealthy(endpoint, Duration.ofSeconds(2)));
    }

    @Test
    void shouldRejectHealthySubstringOutsideStatusField() throws IOException {
        String endpoint = startHealthEndpoint(200, "{\"message\":\"status ok\",\"status\":\"starting\"}");
        assertFalse(ContainerHealthCheck.isHealthy(endpoint, Duration.ofSeconds(2)));
    }

    @Test
    void shouldFailForUnexpectedPayload() throws IOException {
        String endpoint = startHealthEndpoint(200, "{\"status\":\"starting\"}");
        assertFalse(ContainerHealthCheck.isHealthy(endpoint, Duration.ofSeconds(2)));
    }

    @Test
    void shouldFailForNonSuccessfulStatus() throws IOException {
        String endpoint = startHealthEndpoint(500, "{\"status\":\"error\"}");
        assertFalse(ContainerHealthCheck.isHealthy(endpoint, Duration.ofSeconds(2)));
    }

    @Test
    void shouldFailForMalformedEndpoint() {
        assertFalse(ContainerHealthCheck.isHealthy("not a uri", Duration.ofSeconds(2)));
    }

    @Test
    void shouldAcceptHealthyEndpointFromMainArguments() throws IOException {
        String endpoint = startHealthEndpoint(200, "{\"status\":\"ok\"}");
        assertDoesNotThrow(() -> ContainerHealthCheck.main(new String[]{endpoint}));
    }

    @Test
    void shouldRejectUnhealthyEndpointFromMainArguments() throws IOException {
        String endpoint = startHealthEndpoint(200, "{\"status\":\"starting\"}");
        assertThrows(IOException.class, () -> ContainerHealthCheck.main(new String[]{endpoint}));
    }

    private String startHealthEndpoint(int statusCode, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        server.createContext("/health", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, payload.length);
            try (var output = exchange.getResponseBody()) {
                output.write(payload);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/health";
    }
}
