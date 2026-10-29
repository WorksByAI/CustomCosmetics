// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/** Online item metadata supplies the complete head list rather than a fixed helmet list. */
public final class HeadSkinCatalog {
    public record Entry(String id, String name, String texture) {}
    public record Animation(int ticks, List<String> frames) {
        public Animation {
            frames = List.copyOf(frames);
            if (ticks < 1 || frames.isEmpty()) throw new IllegalArgumentException("Invalid animation cadence or frames.");
        }
        public int frame(long tick) { return (int) Math.floorMod(Math.floorDiv(tick, ticks), frames.size()); }
    }
    public record Data(List<Entry> entries, Map<String, Animation> animations) {
        public Data { entries = List.copyOf(entries); animations = Map.copyOf(animations); }
    }
    private static final Pattern TEXTURE = Pattern.compile("(?:Value|value):\\s*\"([A-Za-z0-9+/=]+)\"");
    private static final Pattern ID = Pattern.compile("[A-Z0-9_;.-]{1,120}");
    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final Pattern LEVEL = Pattern.compile("\\[Lvl[^]]*]");
    public static final Data EMPTY = new Data(List.of(), Map.of());
    private HeadSkinCatalog() {}
    public static List<Entry> parse(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("success") && !root.get("success").getAsBoolean())
            throw new IllegalArgumentException("The head item feed reported a failure.");
        var items = root.getAsJsonArray("items");
        if (items == null || items.size() > 30000) throw new IllegalArgumentException("Invalid head item feed.");
        var entries = new TreeMap<String, Entry>();
        for (var element : items) try {
            var item = element.getAsJsonObject();
            String material = item.get("material").getAsString();
            if (!material.equals("SKULL_ITEM") && !material.equals("PLAYER_HEAD")) continue;
            if (!item.has("skin")) continue;
            String id = item.get("id").getAsString();
            String name = item.get("name").getAsString().replaceAll("§.", "").strip();
            var skin = item.get("skin");
            String texture = HeadTexture.normalize(skin.isJsonObject() ? skin.getAsJsonObject().get("value").getAsString() : skin.getAsString());
            if (!id.matches("[A-Z0-9_;-]{1,120}") || name.isBlank() || name.length() > 160 || texture.isEmpty()) continue;
            entries.putIfAbsent(id, new Entry(id, name, texture));
        } catch (RuntimeException invalidEntry) { /* One malformed item must not hide the remaining heads. */ }
        return List.copyOf(entries.values());
    }
    /** Read only item metadata and animation definitions; never extract the remote archive to disk. */
    public static Data parseRepository(InputStream input) throws IOException {
        var items = new TreeMap<String, Entry>();
        Map<String, Animation> animations = Map.of();
        long total = 0; int count = 0;
        byte[] buffer = new byte[8192];
        try (var zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++count > 50000) throw new IOException("Too many head repository files.");
                String path = entry.getName();
                String relative = path.substring(path.indexOf('/') + 1);
                boolean item = relative.startsWith("items/") && relative.endsWith(".json") && relative.indexOf('/', 6) < 0;
                boolean animated = relative.equals("constants/animatedskulls.json");
                int limit = animated ? 16 * 1024 * 1024 : 2 * 1024 * 1024;
                var bytes = item || animated ? new ByteArrayOutputStream() : null;
                int length = 0;
                for (int n; (n = zip.read(buffer)) != -1;) {
                    length += n; total += n;
                    if (length > limit || total > 128L * 1024 * 1024) throw new IOException("Head repository exceeds size limits.");
                    if (bytes != null) bytes.write(buffer, 0, n);
                }
                if (animated) animations = parseAnimations(bytes.toString(StandardCharsets.UTF_8));
                else if (item) try {
                    var value = JsonParser.parseString(bytes.toString(StandardCharsets.UTF_8)).getAsJsonObject();
                    String material = value.get("itemid").getAsString();
                    if (!material.equals("minecraft:skull") && !material.equals("minecraft:player_head")) continue;
                    String id = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
                    var match = TEXTURE.matcher(value.get("nbttag").getAsString());
                    if (!match.find()) continue;
                    add(items, id, value.get("displayname").getAsString(), HeadTexture.normalize(match.group(1)));
                } catch (RuntimeException invalidItem) { /* Preserve the rest of the live repository. */ }
            }
        }
        if (items.isEmpty() || animations.isEmpty()) throw new IOException("The head repository is incomplete.");
        return new Data(List.copyOf(items.values()), animations);
    }
    public static Map<String, Animation> parseAnimations(String json) {
        var skins = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("skins");
        if (skins == null || skins.size() > 10000) throw new IllegalArgumentException("Invalid animated head feed.");
        var animations = new TreeMap<String, Animation>();
        int total = 0;
        for (var entry : skins.entrySet()) try {
            if (!ID.matcher(entry.getKey()).matches()) continue;
            var value = entry.getValue().getAsJsonObject();
            var textures = value.getAsJsonArray("textures");
            if (textures.isEmpty() || textures.size() > 512) continue;
            var frames = new ArrayList<String>(textures.size());
            for (var frame : textures) {
                String raw = frame.getAsString(); int colon = raw.indexOf(':');
                if (colon >= 0) { UUID.fromString(raw.substring(0, colon)); raw = raw.substring(colon + 1); }
                String hash = HeadTexture.normalize(raw);
                if (hash.isEmpty() || hash.startsWith("animated:")) throw new IllegalArgumentException();
                frames.add(hash);
            }
            total += frames.size();
            if (total > 100000) throw new IllegalArgumentException("Too many animated head frames.");
            animations.put(entry.getKey(), new Animation(Math.clamp(value.get("ticks").getAsInt(), 1, 72000), frames));
        } catch (RuntimeException invalidAnimation) { /* A bad skin must not hide other skins. */ }
        return Map.copyOf(animations);
    }
    private static void add(Map<String, Entry> entries, String id, String name, String texture) {
        name = LEVEL.matcher(FORMATTING.matcher(name).replaceAll("")).replaceAll("").strip();
        if (ID.matcher(id).matches() && !name.isEmpty() && name.length() <= 160 && !texture.isEmpty())
            entries.putIfAbsent(id, new Entry(id, name, texture));
    }
    public static Data combine(Data repository, List<Entry> api) {
        var items = new TreeMap<String, Entry>();
        for (var entry : repository.entries()) items.put(entry.id(), entry);
        for (var entry : api) items.putIfAbsent(entry.id(), entry);
        for (var entry : repository.animations().entrySet()) {
            var original = items.get(entry.getKey());
            String name = original == null ? title(entry.getKey()) : original.name();
            String id = "animated:" + entry.getKey();
            items.put(id, new Entry(id, name, id + ":" + entry.getValue().frames().getFirst()));
        }
        return new Data(List.copyOf(items.values()), repository.animations());
    }
    private static String title(String id) {
        var text = new StringBuilder();
        for (String word : id.toLowerCase(Locale.ROOT).split("_")) {
            if (!text.isEmpty()) text.append(' ');
            if (!word.isEmpty()) text.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return text.toString();
    }
    /** Compact validated cache, fetched again each process launch with conditional HTTP requests. */
    public static String cache(Data data) {
        var root = new JsonObject(); root.addProperty("version", 1);
        var items = new com.google.gson.JsonArray();
        for (var entry : data.entries()) {
            var value = new JsonObject(); value.addProperty("id", entry.id()); value.addProperty("name", entry.name()); value.addProperty("texture", entry.texture()); items.add(value);
        }
        root.add("items", items);
        var skins = new JsonObject();
        for (var entry : data.animations().entrySet()) {
            var value = new JsonObject(); value.addProperty("ticks", entry.getValue().ticks());
            var frames = new com.google.gson.JsonArray(); entry.getValue().frames().forEach(frames::add);
            value.add("textures", frames); skins.add(entry.getKey(), value);
        }
        root.add("skins", skins); return root.toString();
    }
    public static Data cached(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        if (root.get("version").getAsInt() != 1 || root.getAsJsonArray("items").size() > 30000) throw new IllegalArgumentException("Invalid cached head catalog.");
        var items = new TreeMap<String, Entry>();
        for (var item : root.getAsJsonArray("items")) try {
            var value = item.getAsJsonObject(); add(items, value.get("id").getAsString(), value.get("name").getAsString(), HeadTexture.normalize(value.get("texture").getAsString()));
        } catch (RuntimeException invalidItem) { /* Skip malformed cached entries. */ }
        return new Data(List.copyOf(items.values()), parseAnimations(json));
    }
}
