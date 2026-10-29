// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

/** A persisted texture hash, independent of player identity and remote profile lookup. */
public final class HeadTexture {
    private static final Pattern HASH = Pattern.compile("[a-fA-F0-9]{32,64}");
    private static final Pattern ANIMATION = Pattern.compile("[A-Z0-9_;.-]{1,120}");
    private static final Pattern TEXTURE_PATH = Pattern.compile("/texture/[a-fA-F0-9]{32,64}");
    private HeadTexture() {}
    public static String normalize(String input) {
        String value = input == null ? "" : input.strip();
        if (value.isEmpty()) return "";
        if (value.length() > 8192) throw new IllegalArgumentException("Head texture is too long.");
        if (value.startsWith("animated:")) {
            String[] parts = value.split(":", -1);
            if (parts.length != 3 || !ANIMATION.matcher(parts[1]).matches() || !HASH.matcher(parts[2]).matches())
                throw new IllegalArgumentException("Invalid animated head skin.");
            return "animated:" + parts[1] + ":" + parts[2].toLowerCase(Locale.ROOT);
        }
        if (HASH.matcher(value).matches()) return value.toLowerCase(Locale.ROOT);
        try {
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                // Some upstream textures carry incomplete or surplus terminal padding.
                // Decode the unpadded value, then still validate the Minecraft host and hash.
                int end = value.length();
                while (end > 0 && value.charAt(end - 1) == '=') end--;
                String json = new String(Base64.getDecoder().decode(value.substring(0, end)), StandardCharsets.UTF_8);
                value = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures")
                        .getAsJsonObject("SKIN").get("url").getAsString();
            }
            URI uri = URI.create(value);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || !"textures.minecraft.net".equalsIgnoreCase(uri.getHost()) || uri.getRawUserInfo() != null
                    || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
                    || !TEXTURE_PATH.matcher(uri.getPath()).matches())
                throw new IllegalArgumentException();
            return uri.getPath().substring(9).toLowerCase(Locale.ROOT);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Use a Minecraft texture URL, texture hash or Base64 skin value.");
        }
    }
    public static String property(String hash) {
        String value = normalize(hash);
        if (value.isEmpty()) throw new IllegalArgumentException("Choose a head texture first.");
        if (value.startsWith("animated:")) value = value.substring(value.lastIndexOf(':') + 1);
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + value + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
    public static String animationId(String selection) {
        return selection.startsWith("animated:") ? selection.substring(9, selection.lastIndexOf(':')) : "";
    }
}
