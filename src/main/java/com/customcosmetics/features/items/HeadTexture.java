// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/** A persisted texture hash, independent of player identity and remote profile lookup. */
public final class HeadTexture {
    private HeadTexture() {}
    public static String normalize(String input) {
        String value = input == null ? "" : input.strip();
        if (value.isEmpty()) return "";
        if (value.length() > 8192) throw new IllegalArgumentException("Head texture is too long.");
        if (value.matches("[a-fA-F0-9]{32,64}")) return value.toLowerCase(Locale.ROOT);
        try {
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                value = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures")
                        .getAsJsonObject("SKIN").get("url").getAsString();
            }
            URI uri = URI.create(value);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || !"textures.minecraft.net".equalsIgnoreCase(uri.getHost()) || uri.getRawUserInfo() != null
                    || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
                    || !uri.getPath().matches("/texture/[a-fA-F0-9]{32,64}"))
                throw new IllegalArgumentException();
            return uri.getPath().substring(9).toLowerCase(Locale.ROOT);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Use a Minecraft texture URL, texture hash or Base64 skin value.");
        }
    }
    public static String property(String hash) {
        String value = normalize(hash);
        if (value.isEmpty()) throw new IllegalArgumentException("Choose a head texture first.");
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + value + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
