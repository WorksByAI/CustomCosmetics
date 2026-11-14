// Adapted from Athen LobbyMarker, Copyright (c) 2025-2026 Starred. BSD-3-Clause.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.waypoint.Waypoints;
import com.customcosmetics.utils.MessageUtils;
import net.minecraft.client.Minecraft;
import java.util.LinkedHashMap;

public final class LobbyMarker {
    private static final LinkedHashMap<String, Long> recent = new LinkedHashMap<>();
    private static String current = "";

    private LobbyMarker() {}

    public static void tick(Minecraft client) {
        if (!ConfigManager.lobbyMarkerEnabled) {
            current = "";
            recent.clear();
            return;
        }
        if (client.player == null || client.player.tickCount % 10 != 0) return;
        String next = PresenceWorld.skyblockInstance(client);
        if (next.equals(current)) return;
        current = next;
        if (next.isEmpty() || ConfigManager.lobbyMarkerCrystalOnly && !"crystal_hollows".equals(Waypoints.currentIslandMode())) return;
        long now = System.currentTimeMillis();
        long duration = ConfigManager.lobbyMarkerMinutes * 60_000L;
        recent.entrySet().removeIf(entry -> now - entry.getValue() > duration);
        Long last = recent.remove(next);
        recent.put(next, now);
        if (recent.size() > 256) recent.pollFirstEntry();
        if (last != null) MessageUtils.sendMessage("You have already visited this SkyBlock lobby.");
    }
}
