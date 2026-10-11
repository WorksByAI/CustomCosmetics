// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.features.blocking.BlockHand;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** HTTP only to the configured cosmetics endpoint; never uses a Minecraft game connection. */
public final class BlockingTransport {
    public static final int MAX_TTL_MS = 2500;
    public record Pose(BlockHand hand, long expiresAt, String itemId) {
        public Pose(BlockHand hand, long expiresAt) { this(hand, expiresAt, ""); }
    }
    private final PresenceAuthClient.ConnectionFactory connections;
    public BlockingTransport(PresenceAuthClient.ConnectionFactory connections) { this.connections = connections; }

    public Map<UUID, Pose> fetch(String base, long requestedAt) throws Exception {
        var json = request(base + "/actions", null, null);
        return parse(json, requestedAt);
    }

    public UdpPresenceClient.Session udpSession(String base, PresenceAuthClient.Session session) throws Exception {
        if (session != null && !base.equals(session.baseUrl())) throw new IOException("UDP session belongs to another endpoint");
        var response = request(base + "/actions/udp-session", session == null ? null : new JsonObject(), session == null ? null : session.token());
        try {
            String id = response.get("id").getAsString(), key = response.get("key").getAsString();
            int port = response.get("port").getAsInt(); long expires = response.get("expiresAt").getAsLong();
            if (response.get("udpVersion").getAsInt() != 2 || !id.matches("[a-f0-9]{32}") || !key.matches("[a-f0-9]{64}") || port < 1 || port > 65535 || expires <= System.currentTimeMillis()) throw new IOException("Invalid UDP session response");
            return new UdpPresenceClient.Session(id, key, port, expires, response.get("readOnly").getAsBoolean());
        } catch (RuntimeException invalid) { throw new IOException("Invalid UDP session response"); }
    }

    public void publish(String base, PresenceAuthClient.Session session, BlockHand hand, long sequence) throws Exception {
        if (!base.equals(session.baseUrl())) throw new IOException("Blocking session belongs to another endpoint");
        var body = new JsonObject();
        body.addProperty("hand", hand.wire);
        body.addProperty("sequence", sequence);
        var response = request(base + "/actions", body, session.token());
        if (!response.has("ok") || !response.get("ok").getAsBoolean() || response.get("actionVersion").getAsInt() != 1)
            throw new IOException("Invalid blocking response");
    }

    static Map<UUID, Pose> parse(JsonObject json, long requestedAt) throws IOException {
        try {
            if (json.get("actionVersion").getAsInt() != 1 || json.get("ttlMs").getAsInt() < 1
                || json.get("ttlMs").getAsInt() > MAX_TTL_MS || !json.get("users").isJsonObject())
                throw new IOException("Unsupported blocking protocol");
            var users = json.getAsJsonObject("users");
            if (users.size() > 10000) throw new IOException("Blocking response too large");
            var result = new HashMap<UUID, Pose>();
            for (var entry : users.entrySet()) {
                try {
                    if (!entry.getKey().matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) continue;
                    UUID uuid = UUID.fromString(entry.getKey());
                    var data = entry.getValue().getAsJsonObject();
                    BlockHand hand = BlockHand.fromWire(data.get("hand").getAsString());
                    int remaining = data.get("remainingMs").getAsInt();
                    if (hand != BlockHand.NONE && remaining > 0 && remaining <= json.get("ttlMs").getAsInt())
                        result.put(uuid, new Pose(hand, requestedAt + remaining));
                } catch (RuntimeException ignored) { /* Ignore invalid individual records. */ }
            }
            return Map.copyOf(result);
        } catch (RuntimeException e) { throw new IOException("Invalid blocking response"); }
    }

    private JsonObject request(String url, JsonObject body, String token) throws Exception {
        HttpURLConnection connection = PresenceAuthClient.openServiceConnection(url, connections);
        try {
            connection.setConnectTimeout(1500); connection.setReadTimeout(1500); connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (var out = connection.getOutputStream()) { out.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Blocking endpoint returned HTTP " + status);
            try (var in = connection.getInputStream()) {
                byte[] bytes = in.readNBytes(1_048_577);
                if (bytes.length > 1_048_576) throw new IOException("Blocking response too large");
                return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } finally { connection.disconnect(); }
    }
}
