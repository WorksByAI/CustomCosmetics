// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay;

import com.customcosmetics.client.ModErrors;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import java.util.function.BiFunction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class QuoiDungeon {
    private QuoiDungeon() {}

    public static boolean allowed() {
        return ConfigManager.gameplayPanelConfirmed && ModErrors.active(ModErrors.Key.DUNGEON);
    }

    public static boolean hypixel() {
        return PresenceWorld.isHypixel(Minecraft.getInstance());
    }

    @SuppressWarnings("unchecked")
    public static void open(Screen parent, boolean hudEditor) {
        if (!allowed()) return;
        ModErrors.run(ModErrors.Key.DUNGEON, () -> {
            var factory = (BiFunction<Screen, Boolean, Screen>) FabricLoader.getInstance()
                .getEntrypoints("csb-dungeon-screen", BiFunction.class).getFirst();
            ClientUiAccess.setScreen(Minecraft.getInstance(), factory.apply(parent, hudEditor));
        });
    }
}
