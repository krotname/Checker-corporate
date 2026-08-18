package com.krotname.checker.ui;

import com.krotname.checker.CheckerCorporate;
import com.krotname.checker.client.DadataClient;
import com.krotname.checker.validation.InnValidator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("ui")
class CheckerUiServerIntegrationTest {
    private CheckerUiServer server;
    private final HttpClient http = HttpClient.newHttpClient();
    private int port;

    @BeforeEach
    void start() throws IOException {
        CheckerCorporate checker = new CheckerCorporate(
                new AlwaysValidInnValidator(),
                stubClient("ACTIVE")
        );
        server = new CheckerUiServer(checker, 0);
        port = server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void shouldBindLoopbackByDefault() {
        assertTrue(server.getAddress().getAddress().isLoopbackAddress());
    }

    @Test
    void shouldHonourExplicitBindAddress() throws IOException {
        CheckerUiServer wildcardServer = new CheckerUiServer(
                new CheckerCorporate(new AlwaysValidInnValidator(), stubClient("ACTIVE")),
                InetAddress.getByName("0.0.0.0"),
                0
        );
        try {
            wildcardServer.start();
            assertTrue(wildcardServer.getAddress().getAddress().isAnyLocalAddress());
        } finally {
            wildcardServer.stop();
        }
    }

    @Test
    void shouldRejectNullBindAddress() {
        assertThrows(NullPointerException.class, () -> new CheckerUiServer(
                new CheckerCorporate(new AlwaysValidInnValidator(), stubClient("ACTIVE")),
                null,
                0
        ));
    }

    @Test
    void shouldServeHealthCheck() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/health")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("application/json; charset=UTF-8", response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().contains("\"status\":\"ok\""));
    }

    @Test
    void shouldRejectUnsupportedHealthMethod() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/health"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
        assertTrue(response.body().contains("Method Not Allowed"));
    }

    @Test
    void shouldReturnNotFoundForUnknownPath() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/missing")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Not Found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/health/extra",
            "/healthz",
            "/api/check/extra?inn=9710083390",
            "/favicon.svg/extra",
            "/favicon.ico/extra"
    })
    void shouldNotTreatRoutePrefixesAsEndpoints(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    @Test
    void shouldRestartAfterStop() throws Exception {
        server.stop();
        port = server.start();

        HttpRequest request = HttpRequest.newBuilder(uri("/health")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
    }

    @Test
    void shouldRejectOutOfRangeRequestedPort() {
        CheckerCorporate checker = new CheckerCorporate(
                new AlwaysValidInnValidator(),
                stubClient("ACTIVE")
        );

        assertThrows(IllegalArgumentException.class, () -> new CheckerUiServer(checker, 65_536));
    }

    @Test
    void shouldAcceptHighestValidRequestedPort() {
        CheckerCorporate checker = new CheckerCorporate(
                new AlwaysValidInnValidator(),
                stubClient("ACTIVE")
        );

        assertDoesNotThrow(() -> new CheckerUiServer(checker, 65_535));
    }

    @Test
    void shouldReleaseListeningPortOnStop() throws IOException {
        int releasedPort = port;
        server.stop();

        HttpServer replacement = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), releasedPort),
                0
        );
        try {
            replacement.start();
        } finally {
            replacement.stop(0);
        }
    }

    @Test
    void shouldRejectMissingInnParam() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/check")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("missing_inn"));
    }

    @Test
    void shouldPreferFirstDuplicateInnParameter() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/check?inn=first%2Ddup&inn=9710083390")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"inn\": \"first-dup\""), response.body());
    }

    @Test
    void shouldDecodePercentEncodedQueryParameterName() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/check?%69nn=9710083390")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"inn\": \"9710083390\""), response.body());
    }

    @Test
    void shouldIgnoreQueryTokensWithoutValueSeparator() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/check?ignored&inn=9710083390")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"inn\": \"9710083390\""), response.body());
    }

    @Test
    void shouldCheckInnViaHttpApi() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/check?inn=9710083390")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\": \"ACTIVE\""), response.body());
        assertTrue(response.body().contains("\"inn\": \"9710083390\""));
    }

    @Test
    void shouldDecodePercentEncodedInnParameter() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/check?inn=9710%200883390")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"inn\": \"9710 0883390\""), response.body());
    }

    @Test
    void shouldKeepRequestProcessingForMalformedPercentEncoding() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/check?inn=%25ZZ")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\": \"ACTIVE\""), response.body());
    }

    @Test
    void shouldRenderIndexPage() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("text/html; charset=UTF-8", response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().contains("<h1>Checker Corporate</h1>"));
        assertTrue(response.body().contains("System health"));
        assertTrue(response.body().contains(".status-pill.unknown"));
    }

    @Test
    void shouldServeSvgFavicon() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/favicon.svg")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("image/svg+xml; charset=UTF-8", response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().contains("<svg"));
    }

    @Test
    void shouldServeLegacyFaviconForHeadWithoutBody() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/favicon.ico"))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("image/svg+xml; charset=UTF-8", response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.body().isEmpty());
    }

    @Test
    void shouldServeLegacyFaviconForGetWithBody() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/favicon.ico")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("<svg"));
    }

    @Test
    void shouldReturnMethodNotAllowedForIndexPage() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/"))
                .method("POST", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
        assertTrue(response.body().contains("Method Not Allowed"));
    }

    @Test
    void shouldReturnMethodNotAllowedForFaviconSvg() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/favicon.svg"))
                .method("POST", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
        assertTrue(response.body().contains("Method Not Allowed"));
    }

    @Test
    void shouldReturnMethodNotAllowedForLegacyFavicon() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/favicon.ico"))
                .method("POST", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
        assertTrue(response.body().contains("Method Not Allowed"));
    }

    @Test
    void shouldReturnMethodNotAllowedForApiCheck() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/check?inn=9710083390"))
                .method("POST", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
        assertTrue(response.body().contains("Method Not Allowed"));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private DadataClient stubClient(String value) {
        return inn -> Optional.of(value);
    }

    private static class AlwaysValidInnValidator extends InnValidator {
        @Override
        public boolean isValid(String inn) {
            return true;
        }
    }
}
