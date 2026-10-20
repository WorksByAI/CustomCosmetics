// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.util.List;

/** Hypixel Mod API mode identifiers, matching Skyblocker's island selection. */
public final class SkyblockIslands {
    public record Island(String id, String name) {}
    public static final List<Island> ALL = List.of(
            new Island("dynamic", "Private Island"), new Island("garden", "Garden"),
            new Island("hub", "Hub"), new Island("farming_1", "The Farming Islands"),
            new Island("foraging_1", "The Park"), new Island("combat_1", "Spider's Den"),
            new Island("combat_2", "Blazing Fortress"), new Island("combat_3", "The End"),
            new Island("crimson_isle", "Crimson Isle"), new Island("mining_1", "Gold Mine"),
            new Island("mining_2", "Deep Caverns"), new Island("mining_3", "Dwarven Mines"),
            new Island("fishing_1", "Backwater Bayou"), new Island("dungeon_hub", "Dungeon Hub"),
            new Island("winter", "Jerry's Workshop"), new Island("rift", "The Rift"),
            new Island("dark_auction", "Dark Auction"), new Island("crystal_hollows", "Crystal Hollows"),
            new Island("dungeon", "Dungeons"), new Island("kuudra", "Kuudra's Hollow"),
            new Island("mineshaft", "Glacite Mineshafts"), new Island("foraging_2", "Moonglade Marsh"),
            new Island("foraging_3", "Torrhus Canyon"), new Island("safari", "Safari"),
            new Island("lotus_atoll", "Lotus Atoll"), new Island("unknown", "Unknown"));
    private SkyblockIslands() {}
    public static String normalize(String mode) {
        if (mode != null) for (Island island : ALL) if (island.id().equals(mode)) return mode;
        return "unknown";
    }
    public static String name(String scope) {
        String id = scope.startsWith("hypixel:") ? scope.substring(8) : scope;
        for (Island island : ALL) if (island.id().equals(id)) return island.name();
        return scope;
    }
    public static String nextScope(String scope, int step) {
        int index = 0;
        for (int i = 0; i < ALL.size(); i++) if (scope.equals("hypixel:" + ALL.get(i).id())) { index = i; break; }
        return "hypixel:" + ALL.get(Math.floorMod(index + step, ALL.size())).id();
    }
}
