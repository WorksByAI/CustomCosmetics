// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.pack;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HypixelPackManifestTest {
    private static final String BASE = "https://resourcepacks.hypixel.net/SkyBlock/1b5b3899-cdc3-4c90-ac57-7d3867e099d5/";
    private static final String HASH = "526bd87b753a8c877a75b27bce6ac58ef8bc9bfe";

    @Test
    void selectsTheOfficialArchiveForTheExactGameFormat() throws IOException {
        var request = HypixelPackManifest.select(manifest(BASE + "97.zip", HASH), 97);
        assertEquals(BASE + "97.zip", request.url().toString());
        assertEquals(HASH, request.hash().toString());
        assertThrows(IOException.class, () -> HypixelPackManifest.select(manifest(BASE + "97.zip", HASH), 88));
    }

    @Test
    void rejectsUntrustedHostsAndMissingHashes() {
        assertThrows(IOException.class, () -> HypixelPackManifest.select(
            manifest(BASE.replace("resourcepacks.hypixel.net", "example.com") + "97.zip", HASH), 97));
        assertThrows(IOException.class, () -> HypixelPackManifest.select(manifest(BASE + "97.zip", ""), 97));
    }

    private static String manifest(String url, String sha1) {
        return """
            {"success":true,"packs":[{"id":"SkyBlock","versions":[{"packFormat":97,"url":"%s","hash":"%s"}]}]}
            """.formatted(url, sha1);
    }
}
