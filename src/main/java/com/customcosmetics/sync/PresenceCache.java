package com.customcosmetics.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** UUIDs are map keys; the filename isolates each service without extra JSON metadata. */
final class PresenceCache {
    private PresenceCache() {}

    static JsonObject clean(String json) {
        JsonObject inputRoot = JsonParser.parseString(json).getAsJsonObject();
        JsonObject root = new JsonObject();
        for (String key : new String[]{"version", "authVersion", "updatedAt", "source", "users"})
            if (inputRoot.has(key)) root.add(key,inputRoot.get(key));
        JsonObject users = root.getAsJsonObject("users");
        if (users == null) return root;
        for (var entry : users.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject input = entry.getValue().getAsJsonObject();
            JsonObject user = new JsonObject();
            for (String key : new String[] {"name", "features", "meta"})
                if (input.has(key)) user.add(key, input.get(key));
            entry.setValue(user);
        }
        return root;
    }
    static Path path(Path directory, String endpoint) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8)),0,12);
            return directory.resolve("remote-users-" + hash + "-cache.json");
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
