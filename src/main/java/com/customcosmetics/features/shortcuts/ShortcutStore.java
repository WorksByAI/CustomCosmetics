// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.shortcuts;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Refuses to overwrite an unreadable file; successful edits use atomic replacement. */
public final class ShortcutStore {
    public record Data(boolean enabled, List<ShortcutRules.Entry> entries) {
        public Data { entries = List.copyOf(entries); }
    }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private boolean writable = true;
    public ShortcutStore(Path file) { this.file = file; }
    public boolean writable() { return writable; }
    void protect() { writable = false; }
    public Data load() throws IOException {
        writable = false;
        if (Files.notExists(file)) { writable = true; return new Data(true, List.of()); }
        try {
            if (Files.size(file) > 512 * 1024) throw new IllegalArgumentException("File exceeds 512 KiB.");
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement version = root.get("version");
            if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
                || !version.getAsString().equals("1")) throw new IllegalArgumentException("Unsupported shortcut version.");
            boolean enabled = booleanValue(root, "enabled");
            List<ShortcutRules.Entry> entries = new ArrayList<>();
            for (JsonElement raw : root.getAsJsonArray("entries")) {
                JsonObject entry = raw.getAsJsonObject();
                entries.add(new ShortcutRules.Entry(string(entry, "id"),
                    ShortcutRules.Kind.valueOf(string(entry, "kind")), string(entry, "trigger"),
                    string(entry, "command"), booleanValue(entry, "enabled")));
            }
            ShortcutRules.validate(entries, Set.of());
            writable = true;
            return new Data(enabled, entries);
        } catch (RuntimeException exception) {
            throw new IOException("shortcuts.json could not be loaded; the original file has been preserved.", exception);
        }
    }
    public void save(Data data) throws IOException {
        if (!writable) throw new IOException("Fix or restore shortcuts.json, then Reload. The original file is protected.");
        ShortcutRules.validate(data.entries(), Set.of());
        JsonObject root = new JsonObject(); root.addProperty("version", 1); root.addProperty("enabled", data.enabled());
        root.add("entries", JSON.toJsonTree(data.entries()));
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "shortcuts-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(root), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected text: " + key);
        return value.getAsString();
    }
    private static boolean booleanValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("Expected boolean: " + key);
        return value.getAsBoolean();
    }
}
