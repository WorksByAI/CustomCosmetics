// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.client.ModErrors;
import com.customcosmetics.gui.ModuleNotifications;
import java.util.HashSet;

public enum GameplayModule {
    AUTO_BOOK_COMBINE("Auto Book Combine", Category.GENERAL),
    AUTO_CLICKER("Auto Clicker", Category.GENERAL),
    AUTO_EXPERIMENTS("Auto Experiments", Category.GENERAL),
    AUTO_GFS("Auto GFS", Category.GENERAL),
    AUTO_HARP("Auto Harp", Category.GENERAL),
    TREVOR_HELPER("Trevor Helper", Category.GENERAL),
    AUTO_CLOSE_CHEST("Auto Close Chest", Category.DUNGEON),
    AUTO_I4("Auto I4", Category.DUNGEON),
    DEVICE("Device", Category.DUNGEON),
    AUTO_LEAP("Auto Leap", Category.DUNGEON),
    SECRET_AURA("Secret Aura", Category.DUNGEON),
    SECRET_TRIGGERBOT("Secret Triggerbot", Category.DUNGEON),
    AUTO_DOOR("Auto Door Opener", Category.DUNGEON),
    F7_TIMER("F7 Phase Timer", Category.DUNGEON),
    FAST_LEAP("Fast Leap", Category.DUNGEON),
    STARRED_MOBS("Starred Mob Highlight", Category.DUNGEON),
    CRYSTAL_PICKUP("Crystal Pickup", Category.DUNGEON),
    RELICS("Relics", Category.DUNGEON),
    OPEN_TERMINALS("Open Terminals", Category.DUNGEON),
    ARROW_ALIGN("Arrow Align", Category.DUNGEON),
    SIMON_SAYS("Simon Says", Category.DUNGEON),
    HIDE_PLAYERS("Hide Nearby Players", Category.RENDER),
    TRAJECTORIES("Trajectories", Category.RENDER),
    CORPSE_ESP("Corpse ESP", Category.RENDER),
    PEST_ESP("Pest ESP", Category.RENDER),
    SAFARI_ESP("Safari ESP", Category.RENDER),
    WORLD_SCANNER("World Scanner", Category.RENDER),
    RENDER_OPTIMISER("Render Optimiser", Category.RENDER),
    CHOCOLATE_FACTORY("Chocolate Factory", Category.OTHER),
    DOJO("Dojo", Category.OTHER),
    MIRRORVERSE("Mirrorverse Solvers", Category.OTHER),
    AUTO_CARNIVAL("Auto Carnival", Category.OTHER),
    GREAT_SPOOK("Great Spook", Category.OTHER),
    AUTO_JOIN_SKYBLOCK("Auto Join SkyBlock", Category.OTHER),
    AUTO_SPRINT("Auto Sprint", Category.OTHER);

    public enum Category {
        GENERAL("General"), DUNGEON("Dungeon"), RENDER("Render"), OTHER("Other");

        public final String label;
        Category(String label) { this.label = label; }
    }

    public final String label;
    public final Category category;

    GameplayModule(String label, Category category) {
        this.label = label;
        this.category = category;
    }

    public boolean localControl() { return this == AUTO_I4 || this == DEVICE || category != Category.DUNGEON; }

    public boolean configured() {
        return this == DEVICE ? ConfigManager.gameplayDeviceEnabled : ConfigManager.gameplayModules.contains(name());
    }

    public boolean enabled() {
        return localControl() && ConfigManager.gameplayPanelConfirmed && configured()
            && (this != AUTO_I4 || ConfigManager.gameplayDeviceEnabled)
            && ModErrors.active(ModErrors.Key.GAMEPLAY);
    }

    public void toggle() {
        if (this == DEVICE) {
            ConfigManager.gameplayDeviceEnabled = !ConfigManager.gameplayDeviceEnabled;
            ConfigManager.requestSave();
            QuoiDungeon.deviceGateChanged();
            ModuleNotifications.show(label, ConfigManager.gameplayDeviceEnabled);
            return;
        }
        var next = new HashSet<>(ConfigManager.gameplayModules);
        boolean enabled = !next.remove(name());
        if (enabled) next.add(name());
        ConfigManager.gameplayModules = java.util.Set.copyOf(next);
        ConfigManager.requestSave();
        ModuleNotifications.show(label, enabled);
    }
}
