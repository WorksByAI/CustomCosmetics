// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.util.Locale;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;

/** SkyBlock island from official Hypixel packets, with a bounded /locraw fallback. */
final class WaypointWorld {
    private static final WaypointSession SESSION = new WaypointSession();
    private static boolean awaitingLocraw;
    private static int ticks, lastLocraw = -1200, locrawDeadline;
    private static Object level;
    private WaypointWorld() {}
    static void reset() {
        SESSION.reset(null);
        level = null; awaitingLocraw = false;
        ticks = 0; lastLocraw = -1200;
    }
    static void init() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay || !awaitingLocraw) return true;
            String raw = message.getString().strip();
            if (!raw.startsWith("{") || !raw.endsWith("}")) return true;
            try {
                var result = JsonParser.parseString(raw).getAsJsonObject();
                if (!result.has("server") || !result.has("gametype") || !result.has("mode")) return true;
                awaitingLocraw = false;
                var client = Minecraft.getInstance();
                if (!SESSION.locraw(client.getConnection(), result.get("gametype").getAsString(),
                        result.get("mode").getAsString())) return true;
                Waypoints.invalidateScope();
                return false;
            } catch (RuntimeException invalid) { return true; }
        });
        HypixelWaypointBridge.register(WaypointWorld.class.getClassLoader(),
            () -> {
                var client = Minecraft.getInstance();
                Object source = client.getConnection();
                client.execute(() -> {
                    if (source == null || source != client.getConnection()) return;
                    SESSION.hello(source);
                    Waypoints.invalidateScope();
                });
            },
            (name, nextIsland) -> {
                var client = Minecraft.getInstance();
                Object source = client.getConnection();
                client.execute(() -> {
                    if (source == null || source != client.getConnection()) return;
                    SESSION.location(source, name, nextIsland);
                    Waypoints.invalidateScope();
                });
            });
    }
    static void tick(Minecraft client) {
        ticks++;
        if (SESSION.follow(client.getConnection())) {
            awaitingLocraw = false;
            lastLocraw = ticks - 1200;
            Waypoints.invalidateScope();
        }
        if (client.level != level) {
            level = client.level;
            Waypoints.invalidateScope();
        }
        if (awaitingLocraw && ticks > locrawDeadline) awaitingLocraw = false;
        if (ticks % 20 != 0 || ticks - lastLocraw < 1200 || client.getConnection() == null
                || SESSION.hasSkyblockLocation(client.getConnection())
                || !SESSION.isHypixel(client.getConnection()) || !hasSkyblockSidebar(client)) return;
        lastLocraw = ticks;
        awaitingLocraw = true;
        locrawDeadline = ticks + 100;
        client.getConnection().sendCommand("locraw");
    }
    private static boolean hasSkyblockSidebar(Minecraft client) {
        if (client.level == null) return false;
        var objective = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return objective != null && objective.getDisplayName().getString().toUpperCase(Locale.ROOT).contains("SKYBLOCK");
    }
    static String scope(Minecraft client) {
        if (client.level == null || client.player == null) return "";
        return SESSION.scope(client.getConnection(), () -> hasSkyblockSidebar(client));
    }
}
