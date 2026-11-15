// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.client;

import net.fabricmc.loader.api.FabricLoader;

public enum ClientEdition {
    CHEATING("cheating", "Cheating"), LEGIT("legit", "Legit");

    public static final String METADATA_KEY = "customcosmetics:edition";
    private final String id;
    public static final String MOD_NAME = "CustomSkyBlock";
    private final String assetLabel;

    ClientEdition(String id, String assetLabel) { this.id = id; this.assetLabel = assetLabel; }

    public String id() { return id; }
    public String displayName() { return MOD_NAME; }

    public String assetName(String version) {
        return MOD_NAME + "-" + assetLabel + "-" + version + "+mc26.1-26.3.jar";
    }

    public static ClientEdition installed() { return Installed.EDITION; }

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
