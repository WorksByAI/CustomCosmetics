// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Optional;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.level.storage.LevelResource;
import com.customcosmetics.features.animations.WorldScope;

/** Hypixel island from location events, with a bounded /locraw fallback. */
final class WaypointWorld {
    private static Object eventConnection;
    private static String island = "";
    private static boolean skyblock, awaitingLocraw;
    private static int ticks, lastLocraw = -1200, locrawDeadline;
    private static Object level;
    private WaypointWorld() {}
    static void reset() {
        eventConnection = level = null; island = ""; skyblock = awaitingLocraw = false;
        ticks = 0; lastLocraw = -1200;
    }
    static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay || !awaitingLocraw) return true;
            String raw = message.getString().strip();
            if (!raw.startsWith("{") || !raw.endsWith("}")) return true;
            try {
                var result = JsonParser.parseString(raw).getAsJsonObject();
                if (!result.has("server") || !result.has("gametype") || !result.has("mode")) return true;
                awaitingLocraw = false;
                skyblock = "SKYBLOCK".equals(result.get("gametype").getAsString());
                island = skyblock ? SkyblockIslands.normalize(result.get("mode").getAsString()) : "";
                Waypoints.invalidateScope();
                return false;
            } catch (RuntimeException invalid) { return true; }
        });
        try {
            ClassLoader loader = WaypointWorld.class.getClassLoader();
            Class<?> apiType = Class.forName("net.hypixel.modapi.HypixelModAPI", false, loader);
            Class<?> handler = Class.forName("net.hypixel.modapi.handler.ClientboundPacketHandler", false, loader);
            Class<?> packetType = Class.forName("net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket", false, loader);
            Object api = apiType.getMethod("getInstance").invoke(null);
            Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{handler}, (proxy, method, args) -> {
                if (!method.getName().equals("handle")) return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "CustomCosmetics waypoint location listener";
                    default -> null;
                };
                Object packet = args[0];
                var client = Minecraft.getInstance();
                Object source = client.getConnection();
                Optional<?> serverType = (Optional<?>) packetType.getMethod("getServerType").invoke(packet);
                String name = serverType.isEmpty() ? "" : (String) serverType.get().getClass().getMethod("name").invoke(serverType.get());
                Optional<?> mode = (Optional<?>) packetType.getMethod("getMode").invoke(packet);
                String nextIsland = name.equals("SKYBLOCK") ? SkyblockIslands.normalize(mode.map(Object::toString).orElse("")) : "";
                client.execute(() -> {
                    if (source == null || source != client.getConnection()) return;
                    eventConnection = source;
                    island = nextIsland;
                    skyblock = name.equals("SKYBLOCK");
                    Waypoints.invalidateScope();
                });
                return null;
            });
            apiType.getMethod("createHandler", Class.class, handler).invoke(api, packetType, callback);
            apiType.getMethod("subscribeToEventPacket", Class.class).invoke(api, packetType);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            // Without the optional API, ordinary servers and singleplayer still work.
        }
    }
    static void tick(Minecraft client) {
        ticks++;
        if (client.level != level) {
            level = client.level;
            island = ""; skyblock = awaitingLocraw = false;
            eventConnection = null;
            lastLocraw = ticks - 1200;
            Waypoints.invalidateScope();
        }
        if (awaitingLocraw && ticks > locrawDeadline) awaitingLocraw = false;
        if (ticks % 20 != 0 || ticks - lastLocraw < 1200 || client.getConnection() == null
                || eventConnection == client.getConnection() && skyblock
                || !isHypixel(client) || !hasSkyblockSidebar(client)) return;
        lastLocraw = ticks;
        awaitingLocraw = true;
        locrawDeadline = ticks + 100;
        client.getConnection().sendCommand("locraw");
    }
    private static boolean isHypixel(Minecraft client) {
        var server = client.getCurrentServer();
        return server != null && WorldScope.isHypixelHost(server.ip.toLowerCase(Locale.ROOT));
    }
    private static boolean hasSkyblockSidebar(Minecraft client) {
        if (client.level == null) return false;
        var objective = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return objective != null && objective.getDisplayName().getString().toUpperCase(Locale.ROOT).contains("SKYBLOCK");
    }
    static String scope(Minecraft client) {
        if (client.level == null || client.player == null) return "";
        String dimension = client.level.dimension().identifier().toString();
        if (client.hasSingleplayerServer()) {
            var server = client.getSingleplayerServer();
            return server == null ? "" : "local:" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize() + "|" + dimension;
        }
        var server = client.getCurrentServer();
        if (server == null) return "";
        String host = server.ip.toLowerCase(Locale.ROOT);
        if (WorldScope.isHypixelHost(host)) return skyblock || hasSkyblockSidebar(client)
                ? WaypointData.hypixelScope(island, "") : "";
        return "server:" + host + "|" + dimension;
    }
}
