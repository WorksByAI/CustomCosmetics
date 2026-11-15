// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay;

import com.customcosmetics.client.ModErrors;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.gameplay.dungeonapi.DungeonMenu;
import com.customcosmetics.gui.ModuleNotifications;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class QuoiDungeon {
    private static DungeonMenu menu;
    private QuoiDungeon() {}

    public static boolean allowed() {
        return ConfigManager.gameplayPanelConfirmed && ModErrors.active(ModErrors.Key.DUNGEON);
    }

    public static boolean hypixel() {
        return PresenceWorld.isHypixel(Minecraft.getInstance());
    }

    public static DungeonMenu menu() {
        if (!allowed()) return null;
        if (menu == null) ModErrors.run(ModErrors.Key.DUNGEON, () -> {
            var entrypoint = FabricLoader.getInstance().getEntrypoints("csb-dungeon-menu", DungeonMenu.class).getFirst();
            entrypoint.setToggleNotifier(ModuleNotifications::show);
            menu = entrypoint;
        });
        return menu;
    }
}
