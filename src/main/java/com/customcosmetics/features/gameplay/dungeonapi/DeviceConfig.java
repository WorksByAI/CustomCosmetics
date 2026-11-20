// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay.dungeonapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class DeviceConfig {
    public enum Part {
        SIMON_SAYS("Simon Says", "Simon Says"),
        LIGHT_DEVICE("Light Device", "Lights Device"),
        ARROW_ALIGN("Arrow Align", "Arrow Align"),
        AUTO_I4("Auto I4", "");

        public final String label;
        public final String dungeonName;
        private static final Part[] PARTS = values();

        Part(String label, String dungeonName) {
            this.label = label;
            this.dungeonName = dungeonName;
        }

        public static Part forDungeonName(String name) {
            for (Part part : PARTS) if (!part.dungeonName.isEmpty() && part.dungeonName.equals(name)) return part;
            return null;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final JsonPrimitive AUTO_I4 = new JsonPrimitive(Part.AUTO_I4.name());
    private static final JsonPrimitive MASTER = new JsonPrimitive("DEVICE");
    private static final String MASTER_FIELD = "gameplayDeviceEnabled";
    private static final List<String> AUTO_I4_SETTINGS = List.of("autoI4Leap", "autoI4LeapPriority",
        "autoI4LeapToMelody", "autoI4Mask", "autoI4Precise", "autoI4Predict", "autoI4Rod", "autoI4RotationTime",
        "autoI4SwapOrder");

    private DeviceConfig() {}

    public static Path path(Path directory) {
        return directory.resolve("device.json");
    }

    private static Path oldDungeon(Path directory) {
        return directory.resolve("dungeon/settings.json");
    }

    public static boolean exists(Path directory) {
        return Files.exists(path(directory)) || Files.exists(oldDungeon(directory));
    }

    public static JsonObject read(Path directory) throws IOException {
        Path file = path(directory);
        return Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : new JsonObject();
    }

    public static synchronized void merge(Path directory, JsonObject changes) throws IOException {
        if (changes.isEmpty()) return;
        JsonObject device = read(directory);
        boolean changed = false;
        for (var entry : changes.entrySet()) {
            changed |= !entry.getValue().equals(device.get(entry.getKey()));
            device.add(entry.getKey(), entry.getValue());
        }
        if (!changed) return;
        JsonObject ordered = new JsonObject();
        for (Part part : Part.values()) if (device.has(part.label)) ordered.add(part.label, device.remove(part.label));
        device.entrySet().forEach(entry -> ordered.add(entry.getKey(), entry.getValue()));
        writeJson(path(directory), ordered);
    }

    public static void writeJson(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "config-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(json));
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static JsonObject takeLocal(JsonObject local) {
        JsonObject device = new JsonObject();
        if (local.has(MASTER_FIELD)) device.add("enabled", local.remove(MASTER_FIELD));
        JsonObject part = new JsonObject();
        JsonObject settings = new JsonObject();
        for (String key : AUTO_I4_SETTINGS) if (local.has(key)) settings.add(key, local.remove(key));
        if (!settings.isEmpty()) part.add("settings", settings);
        if (local.get("gameplayModules") instanceof JsonArray modules) {
            boolean enabled = modules.contains(AUTO_I4);
            modules.asList().removeIf(AUTO_I4::equals);
            part.addProperty("enabled", enabled);
        }
        if (local.get("gameplayKeybinds") instanceof JsonObject keys) {
            part.add("keybind", takeBinding(keys, AUTO_I4));
            if (device.has("enabled") || keys.asMap().containsValue(MASTER)) device.add("keybind", takeBinding(keys, MASTER));
        }
        if (!part.isEmpty()) device.add(Part.AUTO_I4.label, part);
        return device;
    }

    private static JsonElement takeBinding(JsonObject keys, JsonPrimitive module) {
        var binding = keys.entrySet().stream().filter(entry -> module.equals(entry.getValue()))
            .map(entry -> entry.getKey()).findFirst();
        keys.entrySet().removeIf(entry -> module.equals(entry.getValue()));
        return binding.isPresent() ? new JsonPrimitive(binding.get()) : JsonNull.INSTANCE;
    }

    private static boolean hasLegacyLocal(JsonObject local) {
        return local.has(MASTER_FIELD) || AUTO_I4_SETTINGS.stream().anyMatch(local::has)
            || local.get("gameplayModules") instanceof JsonArray modules && modules.contains(AUTO_I4)
            || local.get("gameplayKeybinds") instanceof JsonObject keys
                && (keys.asMap().containsValue(AUTO_I4) || keys.asMap().containsValue(MASTER));
    }

    public static boolean restoreStartup(Path directory, JsonObject local, JsonObject device) throws IOException {
        boolean migrate = hasLegacyLocal(local);
        if (!device.has("enabled")) {
            var legacy = takeLocal(local.deepCopy());
            Path old = oldDungeon(directory);
            if (Files.exists(old) && !(device.has(Part.SIMON_SAYS.label) && device.has(Part.LIGHT_DEVICE.label)
                    && device.has(Part.ARROW_ALIGN.label))) {
                String json = Files.readString(old);
                if (!json.isBlank()) takeDungeon(JsonParser.parseString(json).getAsJsonArray()).entrySet()
                    .forEach(entry -> legacy.add(entry.getKey(), entry.getValue()));
            }
            legacy.entrySet().forEach(entry -> {
                if (!device.has(entry.getKey())) device.add(entry.getKey(), entry.getValue());
            });
            migrate |= inferMaster(device);
        }
        restoreLocal(local, device);
        return migrate;
    }

    public static boolean inferMaster(JsonObject device) {
        if (device.has("enabled")) return false;
        boolean any = false, enabled = false;
        for (Part part : Part.values()) {
            if (!(device.get(part.label) instanceof JsonObject child)) continue;
            any = true;
            enabled |= child.get("enabled") instanceof JsonPrimitive value && value.isBoolean() && value.getAsBoolean();
        }
        if (any) device.addProperty("enabled", enabled);
        return any;
    }

    public static void restoreLocal(JsonObject local, JsonObject device) {
        if (device.has("enabled")) local.add(MASTER_FIELD, device.get("enabled"));
        restoreBinding(local, device, MASTER);
        if (!device.has(Part.AUTO_I4.label)) return;
        JsonObject part = device.getAsJsonObject(Part.AUTO_I4.label);
        if (part.has("settings")) for (var entry : part.getAsJsonObject("settings").entrySet()) {
            if (AUTO_I4_SETTINGS.contains(entry.getKey())) local.add(entry.getKey(), entry.getValue());
        }
        if (part.has("enabled")) {
            JsonArray modules = local.get("gameplayModules") instanceof JsonArray array ? array : new JsonArray();
            if (part.get("enabled").getAsBoolean()) {
                if (!modules.contains(AUTO_I4)) modules.add(AUTO_I4);
            } else {
                modules.asList().removeIf(AUTO_I4::equals);
            }
            local.add("gameplayModules", modules);
        }
        restoreBinding(local, part, AUTO_I4);
    }

    private static void restoreBinding(JsonObject local, JsonObject part, JsonPrimitive module) {
        if (part.has("keybind")) {
            JsonObject keys = local.get("gameplayKeybinds") instanceof JsonObject object ? object : new JsonObject();
            keys.entrySet().removeIf(entry -> module.equals(entry.getValue()));
            if (!part.get("keybind").isJsonNull()) keys.add(part.get("keybind").getAsString(), module);
            local.add("gameplayKeybinds", keys);
        }
    }

    public static JsonObject takeDungeon(JsonArray modules) {
        JsonObject device = new JsonObject();
        for (var rows = modules.iterator(); rows.hasNext();) {
            var row = rows.next();
            if (!(row instanceof JsonObject module) || !module.has("name")) continue;
            Part part = Part.forDungeonName(module.get("name").getAsString());
            if (part == null) continue;
            rows.remove();
            module.remove("name");
            device.add(part.label, module);
        }
        return device;
    }

    public static void restoreDungeon(JsonArray modules, JsonObject device) {
        for (Part part : Part.values()) {
            if (part.dungeonName.isEmpty() || !device.has(part.label)) continue;
            var row = device.getAsJsonObject(part.label).deepCopy();
            row.addProperty("name", part.dungeonName);
            modules.add(row);
        }
    }
}
