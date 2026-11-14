// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.client.ModErrors;
import java.util.HashSet;

public enum GameplayModule {
    AUTO_BOOK_COMBINE("Auto Book Combine", Category.GENERAL),
    AUTO_CLICKER("Auto Clicker", Category.GENERAL),
    AUTO_EXPERIMENTS("Auto Experiments", Category.GENERAL),
    AUTO_GFS("Auto GFS", Category.GENERAL),
    AUTO_HARP("Auto Harp", Category.GENERAL),
    TREVOR_HELPER("Trevor Helper", Category.GENERAL),
    AUTO_CLOSE_CHEST("Auto Close Chest", Category.DUNGEON),
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
    FRESH_TOOLS("Fresh Tools Timer", Category.KUUDRA),
    KUUDRA_BLOCK_PERKS("Block Perks", Category.KUUDRA),
    KUUDRA_AUTO_PEARL("Auto Pearl", Category.KUUDRA),
    KUUDRA_PEEK("Kuudra Peek", Category.KUUDRA),
    KUUDRA_REQUEUE("Kuudra Queuer", Category.KUUDRA),
    KUUDRA_SPLITS("Kuudra Splits", Category.KUUDRA),
    MANA_USED_ALERT("Mana Used Alert", Category.KUUDRA),
    KUUDRA_REND_DAMAGE("Rend Damage Tracker", Category.KUUDRA),
    KUUDRA_TEAMMATES("Teammate Highlight", Category.KUUDRA),
    KUUDRA_SUPPLY_WAYPOINTS("Supply Waypoints", Category.KUUDRA),
    KUUDRA_TITLES("Kuudra Titles", Category.KUUDRA),
    KUUDRA_TIMERS("Kuudra Timers", Category.KUUDRA),
    KUUDRA_STUN_HELPER("Stun Helper", Category.KUUDRA),
    CORPSE_ESP("Corpse ESP", Category.RENDER),
    PEST_ESP("Pest ESP", Category.RENDER),
    SAFARI_ESP("Safari ESP", Category.RENDER),
    WORLD_SCANNER("World Scanner", Category.RENDER),
    RENDER_OPTIMISER("Render Optimiser", Category.RENDER),
    BOSS_SPAWN_ALERT("Slayer Boss Spawn Alert", Category.SLAYER),
    COCOON_ALERT("Cocoon Alert", Category.SLAYER),
    SLAYER_MINIBOSS_ALERT("Miniboss Alert", Category.SLAYER),
    BEACON_ESP("Beacon ESP", Category.SLAYER),
    SLAYER_SPLITS("Slayer Splits", Category.SLAYER),
    SLAYER_BOSS_HIGHLIGHT("Slayer Boss Highlight", Category.SLAYER),
    DAGGER_SWAP("Dagger Swap", Category.SLAYER),
    CHOCOLATE_FACTORY("Chocolate Factory", Category.OTHER),
    DOJO("Dojo", Category.OTHER),
    MIRRORVERSE("Mirrorverse Solvers", Category.OTHER),
    AUTO_CARNIVAL("Auto Carnival", Category.OTHER),
    GREAT_SPOOK("Great Spook", Category.OTHER),
    AUTO_JOIN_SKYBLOCK("Auto Join SkyBlock", Category.OTHER),
    AUTO_SPRINT("Auto Sprint", Category.OTHER);

    public enum Category {
        GENERAL("General"), DUNGEON("Dungeon"), KUUDRA("Kuudra"),
        SLAYER("Slayer"), RENDER("Render"), OTHER("Other");

        public final String label;
        Category(String label) { this.label = label; }
    }

    public final String label;
    public final Category category;

    GameplayModule(String label, Category category) {
        this.label = label;
        this.category = category;
    }

    public boolean enabled() {
        return ConfigManager.gameplayPanelConfirmed && ConfigManager.gameplayModules.contains(name())
            && ModErrors.active(ModErrors.Key.GAMEPLAY);
    }

    public void toggle() {
        var next = new HashSet<>(ConfigManager.gameplayModules);
        if (!next.remove(name())) next.add(name());
        ConfigManager.gameplayModules = java.util.Set.copyOf(next);
        ConfigManager.requestSave();
    }
}
