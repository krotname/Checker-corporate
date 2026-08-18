package com.krotname.checker.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.krotname.checker.CheckerCorporate;
import com.krotname.checker.model.CheckResult;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class CheckerUiServer {
    private static final String INDEX_RESOURCE = "static/index.html";
    private static final String FAVICON_RESOURCE = "static/favicon.svg";
    private final CheckerCorporate checker;
    private final InetAddress bindAddress;
    private final int requestedPort;
    private final Gson gson;
    private HttpServer server;
    private ExecutorService executor;

    /**
     * Binds to the loopback interface only. This is the safe default: /api/check is
     * unauthenticated and every accepted request spends the operator's DaData quota,
     * so the endpoint must not be reachable from the network unless explicitly asked for.
     */
    public CheckerUiServer(CheckerCorporate checker, int requestedPort) {
        this(checker, InetAddress.getLoopbackAddress(), requestedPort);
    }

    /**
     * Binds to an explicit address. Wildcard binding is meant for container runtimes,
     * where the isolation boundary is the published port rather than the listen address.
     */
    public CheckerUiServer(CheckerCorporate checker, InetAddress bindAddress, int requestedPort) {
        this.checker = Objects.requireNonNull(checker, "checker");
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        if (requestedPort < 0 || requestedPort > 65_535) {
            throw new IllegalArgumentException("requestedPort must be between 0 and 65535");
        }
        this.requestedPort = requestedPort;
        this.gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    }

    public synchronized int start() throws IOException {
        if (server != null) {
            return getPort();
        }
        HttpServer createdServer = HttpServer.create(new InetSocketAddress(bindAddress, requestedPort), 0);
        ExecutorService createdExecutor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            createdServer.createContext("/", this::handleIndex);
            createdServer.createContext("/api/check", this::handleCheck);
            createdServer.createContext("/health", this::handleHealth);
            createdServer.createContext("/favicon.svg", this::handleFavicon);
            createdServer.createContext("/favicon.ico", this::handleLegacyFavicon);
            createdServer.setExecutor(createdExecutor);
            createdServer.start();
        } catch (RuntimeException e) {
            createdServer.stop(0);
            createdExecutor.shutdownNow();
            throw new IOException("Cannot start HTTP server", e);
        }
        server = createdServer;
        executor = createdExecutor;
        return createdServer.getAddress().getPort();
    }

    public synchronized int getPort() {
        return getAddress().getPort();
    }

    public synchronized InetSocketAddress getAddress() {
        HttpServer currentServer = Objects.requireNonNull(server, "server");
        return currentServer.getAddress();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!hasPath(exchange, "/")) {
            writeStatus(exchange, 404, "Not Found");
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeStatus(exchange, 405, "Method Not Allowed");
            return;
        }
        byte[] payload = loadResourcePayload(INDEX_RESOURCE);
        if (payload == null) {
            writeStatus(exchange, 500, "Index resource missing");
            return;
        }
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private void handleFavicon(HttpExchange exchange) throws IOException {
        if (!hasPath(exchange, "/favicon.svg")) {
            writeStatus(exchange, 404, "Not Found");
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeStatus(exchange, 405, "Method Not Allowed");
            return;
        }
        byte[] payload = loadResourcePayload(FAVICON_RESOURCE);
        if (payload == null) {
            writeStatus(exchange, 404, "Not Found");
            return;
        }
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "image/svg+xml; charset=UTF-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private void handleLegacyFavicon(HttpExchange exchange) throws IOException {
        if (!hasPath(exchange, "/favicon.ico")) {
            writeStatus(exchange, 404, "Not Found");
            return;
        }
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            writeStatus(exchange, 405, "Method Not Allowed");
            return;
        }
        byte[] payload = loadResourcePayload(FAVICON_RESOURCE);
        if (payload == null) {
            writeStatus(exchange, 404, "Not Found");
            return;
        }
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "image/svg+xml; charset=UTF-8");
        if ("HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!hasPath(exchange, "/health")) {
            writeStatus(exchange, 404, "Not Found");
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeStatus(exchange, 405, "Method Not Allowed");
            return;
        }
        writeJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    /**
     * Handles /api/check endpoint and delegates domain validation to CheckerCorporate.
     * Missing query params are returned as a 400 payload, all other logic stays in one place.
     */
    private void handleCheck(HttpExchange exchange) throws IOException {
        if (!hasPath(exchange, "/api/check")) {
            writeStatus(exchange, 404, "Not Found");
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeStatus(exchange, 405, "Method Not Allowed");
            return;
        }
        URI uri = exchange.getRequestURI();
        Map<String, String> query = queryParams(uri.getRawQuery());
        String inn = query.getOrDefault("inn", "").trim();
        if (inn.isEmpty()) {
            writeJson(exchange, 400, gson.toJson(Map.of("error", "missing_inn")));
            return;
        }
        CheckResult result = checker.check(inn);
        writeJson(exchange, 200, gson.toJson(result));
    }

    /**
     * Parses simple query strings used by the UI without introducing framework
     * dependencies; keeps endpoint resilient to malformed parameters.
     */
    private Map<String, String> queryParams(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        return java.util.Arrays.stream(rawQuery.split("&"))
                .map(entry -> entry.split("=", 2))
                .filter(parts -> parts.length == 2 && !decode(parts[0]).isBlank())
                .collect(Collectors.toMap(
                        parts -> decode(parts[0]),
                        parts -> decode(parts[1]),
                        (existing, replacement) -> existing,
                        java.util.LinkedHashMap::new
                ));
    }

    private void writeStatus(HttpExchange exchange, int statusCode, String message) throws IOException {
        writeJson(exchange, statusCode, gson.toJson(Map.of("error", message)));
    }

    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "application/json; charset=UTF-8");
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private byte[] loadResourcePayload(String resource) throws IOException {
        // Keep endpoints resilient when static resources are temporarily unavailable.
        try (InputStream in = CheckerUiServer.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        }
    }

    private String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private boolean hasPath(HttpExchange exchange, String expectedPath) {
        return expectedPath.equals(exchange.getRequestURI().getPath());
    }

}
