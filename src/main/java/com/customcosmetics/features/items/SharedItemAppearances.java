// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.customcosmetics.features.items.ItemAppearanceStore.Appearance;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SharedItemAppearances {
    public static final int DEFAULT_LIMIT = 5;
    private static volatile Integer serverLimit;
    private SharedItemAppearances() {}

    public static Integer serverLimit() { return serverLimit; }
    public static int limit() {
        Integer limit = serverLimit;
        return limit == null ? DEFAULT_LIMIT : limit;
    }
    public static void resetLimit() { serverLimit = null; }

    public static void readLimit(JsonObject response) {
        if (!(response.get("itemShareLimit") instanceof com.google.gson.JsonPrimitive value) || !value.isNumber()) return;
        double limit = value.getAsDouble();
        if (limit >= 0 && limit <= ItemAppearanceStore.MAX_SHARED && limit == Math.rint(limit)) serverLimit = (int) limit;
    }

    public static void encode(JsonObject features, Map<String, Appearance> items, boolean includePreviews) {
        if (items.isEmpty()) return;
        var shared = new JsonObject();
        items.forEach((uuid, appearance) -> {
            var value = new JsonObject();
            if (!appearance.name().isEmpty()) value.addProperty("name", appearance.name());
            if (!appearance.model().isEmpty()) value.addProperty("model", appearance.model());
            if (!appearance.armorModel().isEmpty()) value.addProperty("armorModel", appearance.armorModel());
            if (appearance.armorColor() != null) value.addProperty("armorColor", appearance.armorColor());
            if (!appearance.headTexture().isEmpty()) value.addProperty("headTexture", appearance.headTexture());
            if (appearance.hideGlint()) value.addProperty("hideGlint", true);
            if (includePreviews && appearance.preview() != null) value.add("preview", appearance.preview().json());
            shared.add(uuid, value);
        });
        features.add("itemAppearances", shared);
    }

    public static Map<String, Appearance> decode(JsonElement input) {
        if (!(input instanceof JsonObject items) || items.size() > ItemAppearanceStore.MAX_SHARED) return Map.of();
        var result = new LinkedHashMap<String, Appearance>();
        for (var entry : items.entrySet()) {
            String id = entry.getKey();
            if (!ItemAppearanceStore.key(id).equals(id) || id.isEmpty() || !(entry.getValue() instanceof JsonObject value)) continue;
            try {
                String texture = string(value, "headTexture");
                if (!texture.isEmpty() && !texture.equals(HeadTexture.normalize(texture))) continue;
                Integer color = null;
                if (value.has("armorColor")) {
                    if (!(value.get("armorColor") instanceof com.google.gson.JsonPrimitive rgb) || !rgb.isNumber()) continue;
                    double number = rgb.getAsDouble();
                    if (number < 0 || number > 0xFFFFFF || number != Math.rint(number)) continue;
                    color = (int) number;
                }
                var glint = value.get("hideGlint");
                var appearance = new Appearance(string(value, "name"), string(value, "model"), string(value, "armorModel"),
                    color, texture, glint != null && glint.isJsonPrimitive() && glint.getAsJsonPrimitive().isBoolean() && glint.getAsBoolean());
                if (!appearance.empty()) result.put(id, appearance);
            } catch (IllegalArgumentException | IllegalStateException malformed) { /* Ignore only this entry. */ }
        }
        return Map.copyOf(result);
    }

    private static String string(JsonObject value, String key) {
        var field = value.get(key);
        if (field == null) return "";
        if (!field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid item appearance field");
        return field.getAsString();
    }
}
