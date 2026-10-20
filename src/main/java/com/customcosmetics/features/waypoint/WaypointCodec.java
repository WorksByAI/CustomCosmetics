// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import static com.customcosmetics.features.waypoint.WaypointData.*;

/** Independent implementation of Skyblocker's documented V1 share format; no runtime dependency. */
public final class WaypointCodec {
    public static final String PREFIX = "[Skyblocker-Waypoint-Data-V1]";
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private WaypointCodec() {}

    public static String encode(Snapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("enabled", snapshot.enabled());
        JsonArray groups = new JsonArray();
        for (Group group : snapshot.groups()) {
            JsonObject value = groupJson(group);
            value.addProperty("id", group.id());
            value.addProperty("scope", group.scope());
            value.addProperty("enabled", group.enabled());
            groups.add(value);
        }
        root.add("groups", groups);
        return GSON.toJson(root);
    }

    public static Snapshot decode(String text) {
        checkLength(text);
        checkDepth(text);
        JsonObject root = object(JsonParser.parseString(text));
        if (integer(root, "version") != 1) throw new IllegalArgumentException("Unsupported waypoint file version.");
        return new Snapshot(bool(root, "enabled", true), parseGroups(array(root, "groups"), true, ""));
    }

    public static String exportGroups(List<Group> groups) throws IOException {
        new Snapshot(true, groups);
        JsonArray array = new JsonArray();
        for (Group group : groups) {
            JsonObject exported = groupJson(group);
            if (!group.enabled()) for (JsonElement point : exported.getAsJsonArray("waypoints"))
                point.getAsJsonObject().addProperty("shouldRender", false);
            array.add(exported);
        }
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) {
            gzip.write(GSON.toJson(array).getBytes(StandardCharsets.UTF_8));
        }
        return PREFIX + Base64.getEncoder().encodeToString(output.toByteArray());
    }

    public static List<Group> importGroups(String text, String currentScope) throws IOException {
        checkLength(text);
        text = text.strip();
        if (!text.startsWith(PREFIX)) throw new IllegalArgumentException("Paste a Skyblocker V1 waypoint share string.");
        byte[] compressed;
        try { compressed = Base64.getDecoder().decode(text.substring(PREFIX.length()).strip()); }
        catch (IllegalArgumentException bad) { throw new IllegalArgumentException("Invalid Base64 waypoint data.", bad); }
        byte[] bytes;
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            bytes = gzip.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Decompressed waypoint data exceeds 2 MiB.");
        String decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        return importConfig(decoded, currentScope);
    }

    /** Skyblocker's config/skyblocker/waypoints.json uses the same group JSON as a V1 share payload. */
    public static List<Group> importConfig(String json, String currentScope) {
        checkLength(json);
        checkDepth(json);
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonArray()) throw new IllegalArgumentException("Expected Skyblocker waypoint groups in a JSON array.");
        return parseGroups(parsed.getAsJsonArray(), false, currentScope);
    }

    private static JsonObject groupJson(Group group) {
        JsonObject value = new JsonObject();
        value.addProperty("name", group.name());
        String[] scopeParts = group.scope().split(":", 3);
        value.addProperty("island", scopeParts.length >= 2 && scopeParts[0].equals("hypixel") ? scopeParts[1] : "unknown");
        value.addProperty("ordered", group.ordered());
        value.addProperty("renderThroughWalls", group.throughWalls());
        value.addProperty("waypointType", "waypoint");
        JsonArray points = new JsonArray();
        for (Point point : group.points()) {
            JsonObject p = new JsonObject();
            JsonArray pos = new JsonArray();
            pos.add(point.x()); pos.add(point.y()); pos.add(point.z());
            p.add("pos", pos);
            p.addProperty("name", point.name());
            JsonArray colors = new JsonArray();
            colors.add(((point.color() >> 16) & 255) / 255f);
            colors.add(((point.color() >> 8) & 255) / 255f);
            colors.add((point.color() & 255) / 255f);
            p.add("colorComponents", colors);
            p.addProperty("alpha", 0.5);
            p.addProperty("shouldRender", point.enabled());
            points.add(p);
        }
        value.add("waypoints", points);
        return value;
    }

    private static List<Group> parseGroups(JsonArray input, boolean local, String currentScope) {
        if (input.size() > MAX_GROUPS) throw new IllegalArgumentException("Too many waypoint groups.");
        var groups = new ArrayList<Group>();
        int count = 0;
        for (JsonElement element : input) {
            JsonObject group = object(element);
            JsonArray points = array(group, "waypoints");
            if ((count += points.size()) > MAX_POINTS) throw new IllegalArgumentException("Too many waypoints.");
            var decoded = new ArrayList<Point>();
            for (JsonElement p : points) {
                JsonObject point = object(p);
                JsonArray pos = array(point, "pos"), colors = array(point, "colorComponents");
                if (pos.size() != 3 || colors.size() != 3) throw new IllegalArgumentException("Position and color need 3 components.");
                int color = channel(colors.get(0)) << 16 | channel(colors.get(1)) << 8 | channel(colors.get(2));
                int x = exactInt(pos.get(0)), y = exactInt(pos.get(1)), z = exactInt(pos.get(2));
                String name = componentText(point.get("name"));
                if (name.isBlank()) name = "Waypoint (" + x + ", " + y + ", " + z + ")";
                decoded.add(new Point(name, x, y, z, color, bool(point, "shouldRender", true)));
            }
            String scope;
            if (local) scope = string(group, "scope");
            else {
                String island = string(group, "island");
                if (island.equals("unknown")) scope = currentScope.isBlank() ? "unbound" : currentScope;
                else if (instanceScoped(island) && currentScope.startsWith("hypixel:" + island + ":")) scope = currentScope;
                else scope = hypixelScope(island, "");
            }
            groups.add(new Group(local ? string(group, "id") : UUID.randomUUID().toString(), string(group, "name"),
                    scope, local ? bool(group, "enabled", true) : true, bool(group, "ordered", false),
                    bool(group, "renderThroughWalls", true), decoded));
        }
        return new Snapshot(true, groups).groups();
    }

    private static String componentText(JsonElement value) {
        if (value == null) throw new IllegalArgumentException("Missing waypoint name.");
        if (value.isJsonPrimitive()) return value.getAsString();
        StringBuilder result = new StringBuilder();
        flattenText(value, result, 0);
        return result.isEmpty() ? "Waypoint" : result.toString();
    }
    private static void flattenText(JsonElement value, StringBuilder result, int depth) {
        if (depth > 16 || result.length() > 80) throw new IllegalArgumentException("Waypoint name is too complex.");
        if (value.isJsonPrimitive()) result.append(value.getAsString());
        else if (value.isJsonArray()) for (JsonElement part : value.getAsJsonArray()) flattenText(part, result, depth + 1);
        else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("text")) result.append(object.get("text").getAsString());
            if (object.has("extra")) flattenText(object.get("extra"), result, depth + 1);
        }
    }
    private static int channel(JsonElement value) {
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number < 0 || number > 1) throw new IllegalArgumentException("Color components must be between 0 and 1.");
        return (int) Math.round(number * 255);
    }
    private static int exactInt(JsonElement value) {
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Coordinates must be whole numbers.", invalid); }
    }
    private static void checkLength(String text) {
        if (text == null || text.length() > MAX_BYTES) throw new IllegalArgumentException("Waypoint data exceeds 2 MiB.");
    }
    private static void checkDepth(String json) {
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') {
                if (++depth > 32) throw new IllegalArgumentException("Waypoint data nesting is too deep.");
            } else if (c == '}' || c == ']') depth--;
        }
    }
    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Invalid waypoint object.");
        return value.getAsJsonObject();
    }
    private static JsonArray array(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) throw new IllegalArgumentException("Missing list: " + key);
        return object.getAsJsonArray(key);
    }
    private static String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) throw new IllegalArgumentException("Missing text: " + key);
        return object.get(key).getAsString();
    }
    private static int integer(JsonObject object, String key) { return exactInt(object.get(key)); }
    private static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Invalid boolean: " + key);
        return value.getAsBoolean();
    }
}
