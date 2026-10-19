// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local overrides indexed by the unique item UUID, never by the player's UUID. */
public final class ItemAppearanceStore {
    public static final int MAX_NAME = 256, MAX_ENTRIES = 1024;
    private static final Appearance ORIGINAL = new Appearance("", "");
    private static final java.util.regex.Pattern COMPACT_UUID = java.util.regex.Pattern.compile("[a-f0-9]{32}");
    private static final java.util.regex.Pattern FULL_UUID = java.util.regex.Pattern.compile("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}");
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public record Appearance(String name, String model) {
        public Appearance {
            name = name == null ? "" : name;
            model = model == null ? "" : model.strip();
            if (name.length() > MAX_NAME || name.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Use at most 256 characters without control characters.");
            if (!model.isEmpty() && (!model.matches("[a-z0-9_.-]+:[a-z0-9_.-][a-z0-9/._-]*") || model.length() > 256))
                throw new IllegalArgumentException("Invalid model ID.");
        }
        public boolean empty() { return name.isEmpty() && model.isEmpty(); }
    }
    private final Path path;
    private final Map<String, Appearance> entries = new TreeMap<>();
    private boolean enabled = true, dirty;
    private int models,names;
    public boolean hasModels(){return models!=0;}
    public boolean hasNames(){return names!=0;}
    private void count(Appearance value,int change){if(value==null)return;if(!value.model().isEmpty())models+=change;if(!value.name().isEmpty())names+=change;}
    public ItemAppearanceStore(Path path) { this.path = path; }
    public static String key(String raw) {
        if (raw == null) return "";
        String value = raw.strip().toLowerCase(Locale.ROOT);
        if (COMPACT_UUID.matcher(value).matches()) value = value.substring(0,8) + "-" + value.substring(8,12) + "-"
            + value.substring(12,16) + "-" + value.substring(16,20) + "-" + value.substring(20);
        if (!FULL_UUID.matcher(value).matches()) return "";
        return UUID.fromString(value).toString();
    }
    public boolean enabled() { return enabled; }
    public void enabled(boolean value) { if (enabled != value) { enabled = value; dirty = true; } }
    public Appearance get(String uuid) { return entries.getOrDefault(key(uuid), ORIGINAL); }
    public int size() { return entries.size(); }
    public boolean dirty() { return dirty; }
    public void put(String uuid, Appearance value) {
        String key = key(uuid);
        if (key.isEmpty()) throw new IllegalArgumentException("This item has no valid unique item UUID.");
        if (value.empty()) { var removed=entries.remove(key);if(removed!=null){count(removed,-1);dirty=true;}return; }
        if (!entries.containsKey(key) && entries.size() >= MAX_ENTRIES) throw new IllegalStateException("Too many customized items.");
        var previous=entries.put(key,value);if(!value.equals(previous)){count(previous,-1);count(value,1);dirty=true;}
    }
    public void load() throws IOException {
        if (!Files.exists(path)) return;
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("The item appearance file is too large.");
        try {
            var root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("version") || root.get("version").getAsInt() != 1) throw new IOException("Unsupported item appearance format.");
            Map<String, Appearance> loaded = new TreeMap<>();
            var values = root.getAsJsonObject("items");
            if (values != null) for (var entry : values.entrySet()) {
                if (loaded.size() >= MAX_ENTRIES) throw new IOException("Too many customized items.");
                String key = key(entry.getKey());
                if (key.isEmpty()) continue;
                try {
                    var data = entry.getValue().getAsJsonObject();
                    var appearance = new Appearance(data.has("name") ? data.get("name").getAsString() : "",
                        data.has("model") ? data.get("model").getAsString() : "");
                    if (!appearance.empty()) loaded.put(key, appearance);
                } catch (RuntimeException malformedEntry) { /* An invalid override must not break other items. */ }
            }
            boolean newEnabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
            entries.clear(); entries.putAll(loaded);models=names=0;for(var value:loaded.values())count(value,1); enabled = newEnabled; dirty = false;
        } catch (RuntimeException malformed) { throw new IOException("Invalid item appearance format.", malformed); }
    }
    public void save() throws IOException {
        if (!dirty) return;
        var root = new JsonObject(); root.addProperty("version", 1); root.addProperty("enabled", enabled);
        var items = new JsonObject();
        for (var entry : entries.entrySet()) {
            var value = new JsonObject(); value.addProperty("name", entry.getValue().name()); value.addProperty("model", entry.getValue().model());
            items.add(entry.getKey(), value);
        }
        root.add("items", items);
        Path directory = path.toAbsolutePath().getParent(); Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "item-appearances-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root), StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            dirty = false;
        } finally { Files.deleteIfExists(temporary); }
    }
}
