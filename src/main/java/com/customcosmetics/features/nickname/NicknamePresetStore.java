// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Named color presets are saved locally, separately from the active synced appearance. */
public final class NicknamePresetStore {
    public static final int MAX_PRESETS = 64, MAX_NAME_LENGTH = 40;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private final LinkedHashMap<String, NicknameColors> presets = new LinkedHashMap<>();
    private boolean unreadable;
    public NicknamePresetStore(Path path) { this.path = path; }
    public Map<String, NicknameColors> entries() { return Collections.unmodifiableMap(new LinkedHashMap<>(presets)); }
    public static String name(String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > MAX_NAME_LENGTH || clean.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Use a preset name of 1–40 characters.");
        return clean;
    }
    private String existing(String name) {
        return presets.keySet().stream().filter(key -> key.equalsIgnoreCase(name)).findFirst().orElse(name);
    }
    public void put(String rawName, NicknameColors colors) throws IOException {
        checkWritable();
        String name = existing(name(rawName));
        if (!presets.containsKey(name) && presets.size() >= MAX_PRESETS)
            throw new IllegalArgumentException("You can save up to 64 presets.");
        var next = new LinkedHashMap<>(presets);
        next.put(name, Objects.requireNonNull(colors));
        write(next);
        presets.clear(); presets.putAll(next);
    }
    public void remove(String rawName) throws IOException {
        checkWritable();
        var next = new LinkedHashMap<>(presets);
        next.remove(existing(name(rawName)));
        write(next);
        presets.clear(); presets.putAll(next);
    }
    private void checkWritable() throws IOException {
        if (unreadable) throw new IOException("Preset file could not be read; restore it before saving.");
    }
    public void load() throws IOException {
        if (!Files.exists(path)) return;
        try {
            if (Files.size(path) > 256 * 1024) throw new IOException("Preset file is too large.");
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1 || !root.get("presets").isJsonObject())
                throw new IOException("Unsupported preset file.");
            var next = new LinkedHashMap<String, NicknameColors>();
            for (var entry : root.getAsJsonObject("presets").entrySet()) {
                try {
                    String name = name(entry.getKey());
                    if (next.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name))) continue;
                    JsonObject value = entry.getValue().getAsJsonObject();
                    if (!value.has("start") || !value.has("end") || !value.has("style")) continue;
                    NicknameColors colors = JSON.fromJson(value, NicknameColors.class);
                    if (colors != null && next.size() < MAX_PRESETS) next.put(name, colors);
                } catch (RuntimeException ignored) { /* Retain other valid presets. */ }
            }
            presets.clear(); presets.putAll(next); unreadable = false;
        } catch (IOException error) { unreadable = true; throw error; }
        catch (RuntimeException error) { unreadable = true; throw new IOException("Invalid preset file.", error); }
    }
    private void write(Map<String, NicknameColors> values) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        JsonObject root = new JsonObject(); root.addProperty("version", 1); root.add("presets", JSON.toJsonTree(values));
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "nickname-presets-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(root), StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
