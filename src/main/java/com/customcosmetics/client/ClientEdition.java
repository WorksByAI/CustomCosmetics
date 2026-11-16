// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.client;

import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public enum ClientEdition {
    CHEATING("cheating", "Cheating"), LEGIT("legit", "Legit");

    public static final String METADATA_KEY = "customcosmetics:edition";
    private final String id;
    public static final String MOD_NAME = "CustomSkyBlock";
    private final String previousAssetLabel;

    ClientEdition(String id, String previousAssetLabel) { this.id = id; this.previousAssetLabel = previousAssetLabel; }

    public String id() { return id; }
    public String displayName() { return MOD_NAME; }

    public String assetName(String version) {
        return MOD_NAME + "-" + version + "-" + id + ".jar";
    }

    public List<String> releaseAssetNames(String version) {
        return List.of(assetName(version), MOD_NAME + "-" + previousAssetLabel + "-" + version + "+mc26.1-26.3.jar");
    }

    public static ClientEdition installed() { return Installed.EDITION; }

    public static String reportedVersion() {
        String version = CustomCosmeticsClient.version();
        return "unknown".equals(version) ? version : version + "-" + installed().id;
    }

    public static boolean hasCheating() { return installed() == CHEATING; }

    private static final class Installed {
        private static final ClientEdition EDITION = read();

        private static ClientEdition read() {
            var metadata = FabricLoader.getInstance().getModContainer(CustomCosmeticsClient.MOD_ID).orElseThrow().getMetadata();
            var value = metadata.getCustomValue(METADATA_KEY);
            if (value != null) for (var edition : values()) if (edition.id.equals(value.getAsString())) return edition;
            throw new IllegalStateException("Missing or invalid CustomSkyBlock edition");
        }
    }
}
