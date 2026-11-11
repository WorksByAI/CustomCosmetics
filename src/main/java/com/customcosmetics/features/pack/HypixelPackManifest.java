// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.pack;

import com.google.common.hash.HashCode;
import com.google.gson.JsonParser;
import net.minecraft.server.packs.DownloadQueue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class HypixelPackManifest {
    private static final URI ENDPOINT = URI.create("https://api.hypixel.net/v2/resources/packs");
    private static final int MAX_BYTES = 64 * 1024;

    private HypixelPackManifest() {}

    static DownloadQueue.DownloadRequest read(int format) throws IOException {
        var connection = (HttpURLConnection) ENDPOINT.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        try (var input = connection.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Hypixel resource pack manifest is too large");
            return select(new String(bytes, StandardCharsets.UTF_8), format);
        } finally {
            connection.disconnect();
        }
    }

    static DownloadQueue.DownloadRequest select(String json, int format) throws IOException {
        var root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.get("success").getAsBoolean()) throw new IOException("Hypixel resource pack manifest is unavailable");
        for (var entry : root.getAsJsonArray("packs")) {
            var pack = entry.getAsJsonObject();
            if (!"SkyBlock".equals(pack.get("id").getAsString())) continue;
            for (var value : pack.getAsJsonArray("versions")) {
                var version = value.getAsJsonObject();
                if (version.get("packFormat").getAsInt() != format) continue;
                URI uri = URI.create(version.get("url").getAsString());
                String sha1 = version.get("hash").getAsString();
                if (!"https".equals(uri.getScheme()) || !"resourcepacks.hypixel.net".equals(uri.getHost())
                        || uri.getPort() != -1 || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                        || uri.getRawQuery() != null || !uri.getRawPath().matches("/SkyBlock/[0-9a-f-]+/" + format + "\\.zip")
                        || !sha1.matches("[0-9a-f]{40}")) {
                    throw new IOException("Invalid SkyBlock resource pack in the official manifest");
                }
                return new DownloadQueue.DownloadRequest(uri.toURL(), HashCode.fromString(sha1));
            }
        }
        throw new IOException("Hypixel has no SkyBlock resource pack for format " + format);
    }
}
