// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import com.customcosmetics.features.animations.WorldScope;

/** Reads official location events; never scans scoreboards, players or chunks. */
final class WaypointWorld {
    private static Object eventConnection;
    private static String island = "", instance = "";
    private WaypointWorld() {}
    static void reset() { eventConnection = null; island = instance = ""; }
    static void init() {
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
                String nextIsland = name.equals("SKYBLOCK") ? mode.map(Object::toString).orElse("") : "";
                String nextInstance = (String) packetType.getMethod("getServerName").invoke(packet);
                client.execute(() -> {
                    if (source == null || source != client.getConnection()) return;
                    eventConnection = source;
                    island = nextIsland;
                    instance = nextInstance;
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
        if (WorldScope.isHypixelHost(host)) return eventConnection == client.getConnection() ? WaypointData.hypixelScope(island, instance) : "";
        return "server:" + host + "|" + dimension;
    }
}
