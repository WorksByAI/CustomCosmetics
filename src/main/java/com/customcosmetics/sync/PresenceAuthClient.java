package com.customcosmetics.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/** No Minecraft credentials are sent to the cosmetics service. */
public final class PresenceAuthClient {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final URI MOJANG_JOIN = URI.create("https://sessionserver.mojang.com/session/minecraft/join");
    private static final Set<String> REQUEST_PATHS = Set.of(
        "/health", "/users.json", "/auth/challenge", "/register", "/report", "/auth/logout", "/actions", "/actions/udp-session", "/capes/status", "/capes/submit");

    @FunctionalInterface public interface SessionJoin { void join(String serverId) throws Exception; }
    @FunctionalInterface public interface ConnectionFactory { HttpURLConnection open(String url) throws Exception; }
    public record Session(String token, UUID uuid, String name, long expiresAt, String baseUrl) {
        public boolean validFor(String base, UUID player, String playerName) {
            return baseUrl.equals(base) && uuid.equals(player) && name.equalsIgnoreCase(playerName)
                && expiresAt > System.currentTimeMillis() + 15_000;
        }
    }
    private final ConnectionFactory connections;
    public PresenceAuthClient(ConnectionFactory connections) { this.connections = connections; }

    public Session authenticate(String baseUrl, UUID uuid, String name, SessionJoin join) throws Exception {
        validateBaseUrl(baseUrl);
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) throw new SyncRequestException(SyncFailure.of(SyncFailure.Reason.INVALID_ACCOUNT_NAME, SyncFailure.Stage.SERVER_AUTH));
        JsonObject request = new JsonObject();
        request.addProperty("name", name);
        JsonObject challenge = post(baseUrl + "/auth/challenge", request);
        String id = requiredString(challenge, "challengeId");
        long expires = requiredLong(challenge, "expiresAt");
        if (!id.matches("[0-9a-f]{64}") || expires <= System.currentTimeMillis()
                || requiredLong(challenge, "authVersion") != 2) throw invalidResponse();
        byte[] entropy = new byte[32];
        RANDOM.nextBytes(entropy); // Fresh client entropy, never supplied or predicted by the service.
        String nonce = HexFormat.of().formatHex(entropy);
        join.join(serverIdFor(id, nonce));
        JsonObject proof = new JsonObject();
        proof.addProperty("challengeId", id);
        proof.addProperty("clientNonce", nonce);
        JsonObject response = post(baseUrl + "/register", proof);
        String token = requiredString(response, "token");
        UUID verifiedUuid;
        try { verifiedUuid = UUID.fromString(requiredString(response, "uuid")); }
        catch (IllegalArgumentException e) { throw invalidResponse(); }
        String verifiedName = requiredString(response, "name");
        long expiresAt = requiredLong(response, "expiresAt");
        if (!token.matches("[0-9a-f]{64}") || requiredLong(response, "authVersion") != 2
                || expiresAt <= System.currentTimeMillis() || expiresAt > System.currentTimeMillis() + 3_660_000)
            throw invalidResponse();
        if (!uuid.equals(verifiedUuid) || !name.equalsIgnoreCase(verifiedName))
            throw new SyncRequestException(SyncFailure.of(SyncFailure.Reason.ACCOUNT_MISMATCH, SyncFailure.Stage.SERVER_AUTH));
        return new Session(token, verifiedUuid, verifiedName, expiresAt, baseUrl);
    }

    public static String serverIdFor(String challengeId, String clientNonce) throws Exception {
        String input = "customcosmetics:auth:v1\0" + challengeId + "\0" + clientNonce;
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    public static void validateBaseUrl(String baseUrl) throws IOException {
        URI uri = validateOrigin(baseUrl);
        if (!(uri.getRawPath() == null || uri.getRawPath().isEmpty()))
            throw invalidAddress();
    }

    /** API request paths are distinct from the configured, path-free service address. */
    public static void validateRequestUrl(String requestUrl) throws IOException {
        URI uri = validateOrigin(requestUrl);
        if (uri.getRawPath() == null || !REQUEST_PATHS.contains(uri.getRawPath())&&!uri.getRawPath().matches("/cape/[a-f0-9]{64}\\.png"))
            throw invalidAddress();
    }

    public static HttpURLConnection openServiceConnection(String url, ConnectionFactory connections) throws Exception {
        validateRequestUrl(url);
        HttpURLConnection connection = connections.open(url);
        connection.setInstanceFollowRedirects(false);
        return connection;
    }

    private static URI validateOrigin(String url) throws IOException {
        URI uri;
        try { uri = URI.create(url); } catch (IllegalArgumentException | NullPointerException e) {
            throw invalidAddress();
        }
        String host = SyncEndpoint.normalizeHost(uri.getHost());
        boolean httpEndpoint = host != null && !host.isEmpty() && host.equalsIgnoreCase(uri.getHost())
            && uri.getPort() >= 1 && uri.getPort() <= 65535;
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && httpEndpoint))
            throw invalidAddress();
        return uri;
    }

    /** Fixed official endpoint, default TLS validation, no redirects or service-provided URLs. */
    public static void joinMojang(UUID uuid, String accessToken, String serverId) throws Exception {
        RemoteAccess.requireOnline();
        if (accessToken == null || accessToken.isBlank() || "0".equals(accessToken))
            throw new SyncRequestException(SyncFailure.of(SyncFailure.Reason.INVALID_MINECRAFT_SESSION, SyncFailure.Stage.MINECRAFT_AUTH));
        JsonObject body = new JsonObject();
        body.addProperty("accessToken", accessToken);
        body.addProperty("selectedProfile", uuid.toString().replace("-", ""));
        body.addProperty("serverId", serverId);
        HttpURLConnection connection = RemoteAccess.manage((HttpURLConnection) MOJANG_JOIN.toURL().openConnection());
        try {
            configurePost(connection, body);
            int status = connection.getResponseCode();
            if (status != 204) throw new SyncRequestException(SyncFailure.fromHttp(SyncFailure.Stage.MINECRAFT_AUTH, status, ""));
        } finally { connection.disconnect(); }
    }

    private JsonObject post(String url, JsonObject body) throws Exception {
        HttpURLConnection connection = connections.open(url);
        try {
            configurePost(connection, body);
            int status = connection.getResponseCode();
            if (status != 200) throw new SyncRequestException(SyncFailure.fromResponse(connection, SyncFailure.Stage.SERVER_AUTH, status));
            try (var stream = connection.getInputStream()) {
                byte[] response = stream.readNBytes(8193);
                if (response.length > 8192) throw new SyncRequestException(SyncFailure.of(SyncFailure.Reason.INVALID_RESPONSE, SyncFailure.Stage.SERVER_AUTH));
                var parsed = JsonParser.parseString(new String(response, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) throw new SyncRequestException(SyncFailure.of(SyncFailure.Reason.INVALID_RESPONSE, SyncFailure.Stage.SERVER_AUTH));
                return parsed.getAsJsonObject();
            }
        } finally { connection.disconnect(); }
    }

    private static String requiredString(JsonObject response, String key) throws SyncRequestException {
        var value = response.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalidResponse();
        return value.getAsString();
    }

    private static long requiredLong(JsonObject response, String key) throws SyncRequestException {
        var value = response.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalidResponse();
        try { return value.getAsBigDecimal().longValueExact(); }
        catch (RuntimeException e) { throw invalidResponse(); }
    }

    private static SyncRequestException invalidResponse() {
        return new SyncRequestException(SyncFailure.of(SyncFailure.Reason.INVALID_RESPONSE, SyncFailure.Stage.SERVER_AUTH));
    }

    private static SyncRequestException invalidAddress() {
        return new SyncRequestException(SyncFailure.of(SyncFailure.Reason.INVALID_SERVER_ADDRESS, SyncFailure.Stage.OTHER));
    }

    private static void configurePost(HttpURLConnection connection, JsonObject body) throws IOException {
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (var output = connection.getOutputStream()) { output.write(bytes); }
    }
}
