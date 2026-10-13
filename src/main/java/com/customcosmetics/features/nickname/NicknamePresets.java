// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.customcosmetics.config.CosmeticsConfigDir;
import com.customcosmetics.client.CustomCosmeticsClient;

public final class NicknamePresets {
    private static NicknamePresetStore store;
    private NicknamePresets() {}
    public static void init() {
        store = new NicknamePresetStore(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("nickname-color-presets.json"));
        try { store.load(); }
        catch (java.io.IOException error) { CustomCosmeticsClient.LOGGER.warn("Could not load nickname presets: {}", error.getMessage()); }
    }
    public static NicknamePresetStore store() { if (store == null) init(); return store; }
}
