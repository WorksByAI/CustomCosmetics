// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.google.gson.JsonParser;
import java.util.*;

/** Online item metadata supplies the complete head list rather than a fixed helmet list. */
public final class HeadSkinCatalog {
    public record Entry(String id, String name, String texture) {}
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
}
