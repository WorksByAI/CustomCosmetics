package com.customcosmetics.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PresenceAuthClientTest {
    private static final String LAN_BASE = "http://192.168.0.3:25577";
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String CHALLENGE = "a".repeat(64);
    private static final String TOKEN = "b".repeat(64);

    @ParameterizedTest
    @ValueSource(strings = {
        LAN_BASE, "http://127.0.0.1:1", "http://255.255.255.255:65535",
        "http://localhost:25577", "http://cosmetics.example:25577",
        "http://Cosmetics.Example:25577", "http://xn--fsqu00a.xn--0zwm56d:25577",
        "https://cosmetics.example", "https://cosmetics.example:25577"
    })
    void acceptsConfiguredServiceOrigins(String url) {
        assertDoesNotThrow(() -> PresenceAuthClient.validateBaseUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/health", "/users.json", "/auth/challenge", "/register", "/report", "/auth/logout"})
    void acceptsApiPathsWithoutRelaxingTheConfiguredAddress(String path) {
        assertDoesNotThrow(() -> PresenceAuthClient.validateRequestUrl(LAN_BASE + path));
        assertDoesNotThrow(() -> PresenceAuthClient.validateRequestUrl("http://cosmetics.example:25577" + path));
        assertDoesNotThrow(() -> PresenceAuthClient.validateRequestUrl("https://cosmetics.example" + path));
        assertThrows(IOException.class, () -> PresenceAuthClient.validateBaseUrl(LAN_BASE + path));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "", "not a URL", "http://192.168.0.3", "http://192.168.0.3:0",
        "http://192.168.0.3:65536", "http://256.168.0.3:25577", "http://cosmetics.example",
        "http://cosmetics.example:0", "http://cosmetics.example:65536", "http://bad_host.example:25577",
        "http://192.168.000.3:25577", "ftp://192.168.0.3:25577", "file:///tmp/cosmetics",
        "http://user:password@192.168.0.3:25577", "https://user@cosmetics.example",
        "http://192.168.0.3:25577/", "http://192.168.0.3:25577?token=secret",
        "http://192.168.0.3:25577#fragment"
    })
    void rejectsInvalidConfiguredAddresses(String url) {
        assertThrows(IOException.class, () -> PresenceAuthClient.validateBaseUrl(url));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "", "not a URL", LAN_BASE, LAN_BASE + "/", LAN_BASE + "/unknown",
        LAN_BASE + "/users.json?token=secret", LAN_BASE + "/users.json?",
        LAN_BASE + "/users.json#fragment", LAN_BASE + "/users.json#",
        LAN_BASE + "/auth/../register", LAN_BASE + "/%75sers.json", LAN_BASE + "//users.json",
        "http://user:password@192.168.0.3:25577/report", "https://user@cosmetics.example/report",
        "http://192.168.0.3/users.json", "http://192.168.0.3:0/users.json",
        "http://192.168.0.3:65536/users.json", "http://cosmetics.example/users.json",
        "http://cosmetics.example:0/users.json", "http://cosmetics.example:65536/users.json",
        "http://user@cosmetics.example:25577/users.json", "http://bad_host.example:25577/users.json",
        "ftp://192.168.0.3:25577/users.json", "file:///users.json"
    })
    void rejectsInvalidRequestsBeforeOpeningAnyConnection(String url) {
        AtomicBoolean opened = new AtomicBoolean();
        assertThrows(IOException.class, () -> PresenceAuthClient.openServiceConnection(url, request -> {
            opened.set(true);
            throw new AssertionError("Invalid requests must never reach the connection factory");
        }));
        assertFalse(opened.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "localhost"})
    void authenticatesUploadsAndReadsThroughTheProductionConnectionGuard(String host) throws Exception {
        AtomicReference<String> joinedServerId = new AtomicReference<>();
        List<String> paths = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                paths.add(path);
                switch (path) {
                    case "/auth/challenge" -> {
                        assertEquals("POST", exchange.getRequestMethod());
                        JsonObject body = readJson(exchange);
                        assertEquals(1, body.size());
                        assertEquals("TestPlayer", body.get("name").getAsString());
                        assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
                        assertNull(joinedServerId.get());
                        respond(exchange, 200, "{\"challengeId\":\"" + CHALLENGE + "\",\"expiresAt\":"
                            + (System.currentTimeMillis() + 120_000) + ",\"authVersion\":2}");
                    }
                    case "/register" -> {
                        assertEquals("POST", exchange.getRequestMethod());
                        JsonObject body = readJson(exchange);
                        assertEquals(2, body.size());
                        assertEquals(CHALLENGE, body.get("challengeId").getAsString());
                        String nonce = body.get("clientNonce").getAsString();
                        assertTrue(nonce.matches("[0-9a-f]{64}"));
                        String proof = "customcosmetics:auth:v1\0" + CHALLENGE + "\0" + nonce;
                        String expectedServerId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                            .digest(proof.getBytes(StandardCharsets.UTF_8)));
                        assertEquals(expectedServerId, joinedServerId.get());
                        assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
                        respond(exchange, 200, "{\"token\":\"" + TOKEN + "\",\"uuid\":\"" + PLAYER
                            + "\",\"name\":\"TestPlayer\",\"expiresAt\":"
                            + (System.currentTimeMillis() + 3_600_000) + ",\"authVersion\":2}");
                    }
                    case "/report" -> {
                        assertEquals("POST", exchange.getRequestMethod());
                        assertEquals("Bearer " + TOKEN, exchange.getRequestHeaders().getFirst("Authorization"));
                        assertEquals(PLAYER.toString(), readJson(exchange).getAsJsonObject("user").get("uuid").getAsString());
                        respond(exchange, 200, "{\"ok\":true}");
                    }
                    case "/users.json" -> {
                        assertEquals("GET", exchange.getRequestMethod());
                        respond(exchange, 200, "{\"authVersion\":2,\"users\":{}}");
                    }
                    default -> respond(exchange, 404, "{}");
                }
            } catch (Throwable error) {
                failures.set(error);
                respond(exchange, 500, "{}");
            }
        });
        server.start();
        try {
            String base = "http://" + host + ":" + server.getAddress().getPort();
            PresenceAuthClient client = new PresenceAuthClient(PresenceAuthClientTest::open);
            PresenceAuthClient.Session session = client.authenticate(base, PLAYER, "TestPlayer", joinedServerId::set);
            assertTrue(session.validFor(base, PLAYER, "TestPlayer"));
            assertFalse(session.validFor(LAN_BASE, PLAYER, "TestPlayer"));
            HttpURLConnection report = open(base + "/report");
            try {
                report.setRequestMethod("POST");
                report.setDoOutput(true);
                report.setRequestProperty("Content-Type", "application/json");
                report.setRequestProperty("Authorization", "Bearer " + session.token());
                try (var output = report.getOutputStream()) {
                    output.write(("{\"user\":{\"uuid\":\"" + PLAYER + "\"}}").getBytes(StandardCharsets.UTF_8));
                }
                assertEquals(200, report.getResponseCode());
            } finally { report.disconnect(); }
            HttpURLConnection read = open(base + "/users.json");
            try {
                assertEquals(200, read.getResponseCode());
                String body = new String(read.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(2, JsonParser.parseString(body).getAsJsonObject().get("authVersion").getAsInt());
            } finally { read.disconnect(); }
            assertNull(failures.get(), () -> String.valueOf(failures.get()));
            assertEquals(List.of("/auth/challenge", "/register", "/report", "/users.json"), paths);
        } finally { server.stop(0); }
    }

    private final AtomicReference<Throwable> failures = new AtomicReference<>();

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "null", "malformed", "{\"challengeId\":null}", "{\"challengeId\":{}}"})
    void invalidChallengesProduceASpecificErrorBeforeMojangJoin(String body) throws Exception {
        AtomicBoolean joined = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/challenge", exchange -> respond(exchange, 200, body));
        server.start();
        try {
            var client = new PresenceAuthClient(PresenceAuthClientTest::open);
            var error = assertThrows(Exception.class, () -> client.authenticate(
                "http://127.0.0.1:" + server.getAddress().getPort(), PLAYER, "TestPlayer", hash -> joined.set(true)));
            assertEquals(SyncFailure.Reason.INVALID_RESPONSE, SyncFailure.fromException(error, SyncFailure.Stage.SERVER_AUTH).reason());
            assertFalse(joined.get());
        } finally { server.stop(0); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"token", "uuid", "name", "expiresAt", "authVersion"})
    void missingRegistrationFieldsProduceAnInvalidResponseError(String missing) throws Exception {
        JsonObject response = validRegistration();
        response.remove(missing);
        assertEquals(SyncFailure.Reason.INVALID_RESPONSE, rejectedRegistration(response).reason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"token", "uuid", "name", "expiresAt", "authVersion"})
    void wrongRegistrationFieldTypesProduceAnInvalidResponseError(String key) throws Exception {
        JsonObject response = validRegistration();
        response.add(key, new JsonObject());
        assertEquals(SyncFailure.Reason.INVALID_RESPONSE, rejectedRegistration(response).reason());
    }

    @Test
    void distinguishesAMalformedSessionFromADifferentVerifiedAccount() throws Exception {
        JsonObject malformed = validRegistration();
        malformed.addProperty("uuid", "not-a-uuid");
        assertEquals(SyncFailure.Reason.INVALID_RESPONSE, rejectedRegistration(malformed).reason());
        JsonObject wrongAccount = validRegistration();
        wrongAccount.addProperty("name", "OtherPlayer");
        assertEquals(SyncFailure.Reason.ACCOUNT_MISMATCH, rejectedRegistration(wrongAccount).reason());
        JsonObject fractionalExpiry = validRegistration();
        fractionalExpiry.addProperty("expiresAt", 123.5);
        assertEquals(SyncFailure.Reason.INVALID_RESPONSE, rejectedRegistration(fractionalExpiry).reason());
    }

    private static JsonObject validRegistration() {
        JsonObject response = new JsonObject();
        response.addProperty("token", TOKEN);
        response.addProperty("uuid", PLAYER.toString());
        response.addProperty("name", "TestPlayer");
        response.addProperty("expiresAt", System.currentTimeMillis() + 3_600_000);
        response.addProperty("authVersion", 2);
        return response;
    }

    private static SyncFailure rejectedRegistration(JsonObject response) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/challenge", exchange -> respond(exchange, 200,
            "{\"challengeId\":\"" + CHALLENGE + "\",\"expiresAt\":" + (System.currentTimeMillis() + 120_000) + ",\"authVersion\":2}"));
        server.createContext("/register", exchange -> respond(exchange, 200, response.toString()));
        server.start();
        try {
            var client = new PresenceAuthClient(PresenceAuthClientTest::open);
            var error = assertThrows(SyncRequestException.class, () -> client.authenticate(
                "http://127.0.0.1:" + server.getAddress().getPort(), PLAYER, "TestPlayer", hash -> {}));
            assertFalse(error.getMessage().contains(TOKEN));
            return error.failure();
        } finally { server.stop(0); }
    }

    @Test
    void doesNotFollowRedirectsToAnotherEndpoint() throws Exception {
        AtomicBoolean followed = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users.json", exchange -> {
            exchange.getResponseHeaders().set("Location", "/redirect-target");
            respond(exchange, 302, "{}");
        });
        server.createContext("/redirect-target", exchange -> {
            followed.set(true);
            respond(exchange, 200, "{}");
        });
        server.start();
        try {
            HttpURLConnection connection = open("http://127.0.0.1:" + server.getAddress().getPort() + "/users.json");
            try {
                assertFalse(connection.getInstanceFollowRedirects());
                assertEquals(302, connection.getResponseCode());
                assertFalse(followed.get());
            } finally { connection.disconnect(); }
        } finally { server.stop(0); }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = PresenceAuthClient.openServiceConnection(url,
            request -> (HttpURLConnection) URI.create(request).toURL().openConnection(Proxy.NO_PROXY));
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        return connection;
    }

    private static JsonObject readJson(HttpExchange exchange) throws IOException {
        return JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
        exchange.close();
    }
}
