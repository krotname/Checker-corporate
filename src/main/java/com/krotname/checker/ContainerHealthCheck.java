package com.krotname.checker;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Minimal Docker healthcheck entry point.
 * It avoids curl/wget dependencies in the runtime image and checks the same HTTP
 * health endpoint that CI smoke tests use.
 */
public final class ContainerHealthCheck {
    static final String DEFAULT_ENDPOINT = "http://127.0.0.1:8080/health";
    static final int DEFAULT_PORT = 8080;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    private ContainerHealthCheck() {
    }

    public static void main(String[] args) throws IOException {
        String endpoint = args.length == 0 ? defaultEndpoint(System.getenv("CHECKER_PORT")) : args[0];
        if (!isHealthy(endpoint, DEFAULT_TIMEOUT)) {
            throw new IOException("Health endpoint is not ready: " + endpoint);
        }
    }

    /**
     * The container may run on a port other than 8080, so the health probe follows the
     * same CHECKER_PORT variable the server reads instead of a hard-coded 8080.
     */
    static String defaultEndpoint(String configuredPort) {
        int port = DEFAULT_PORT;
        if (configuredPort != null && !configuredPort.isBlank()) {
            try {
                int parsed = Integer.parseInt(configuredPort.trim());
                if (parsed >= 1 && parsed <= 65_535) {
                    port = parsed;
                }
            } catch (NumberFormatException e) {
                port = DEFAULT_PORT;
            }
        }
        return "http://127.0.0.1:" + port + "/health";
    }

    static boolean isHealthy(String endpoint, Duration timeout) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(timeout)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200 && hasHealthyPayload(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean hasHealthyPayload(String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                return false;
            }
            JsonElement status = root.getAsJsonObject().get("status");
            return status != null && status.isJsonPrimitive() && status.getAsJsonPrimitive().isString()
                    && "ok".equals(status.getAsString());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
