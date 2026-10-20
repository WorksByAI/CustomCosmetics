// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.animations;

import net.minecraft.client.Minecraft;
import java.util.Locale;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Official Hypixel location events, plus a local list of nearby loaded players. */
public final class PresenceWorld {
    private static boolean initialized;
    private static Object connection;
    private static int environment;
    private static String instance = "";
    private static boolean hypixel;
    private static final Scope EMPTY = new Scope("", Set.of());
    private static String locationWorld = "", cachedHost = "", normalizedHost = "", cachedWorldKey = "", cachedWorldHash = "";
    private static boolean hypixelHost;
    private static Scope cachedScope;
    private static Object cachedLevel,cachedPlayer;
    private static int cachedTick,cachedDistance;
    private PresenceWorld() {}
    public record Scope(String world, Set<UUID> players) {}
    public static boolean isHypixel(Minecraft client){
        context(client);
        return client.player!=null&&!client.hasSingleplayerServer()&&(hypixel||hypixelHost);
    }
    public static int environment(Minecraft client){context(client);return environment;}
    /** Cheap render-time check; do not rebuild the nearby-player interest list for every marker frame. */
    public static boolean hasHypixelLocation(Minecraft client){
        return client.player!=null&&client.level!=null&&!client.hasSingleplayerServer()&&connection==client.getConnection()&&hypixel&&!locationWorld.isEmpty();
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        HypixelLocationBridge.register(PresenceWorld.class.getClassLoader(),
            value -> accept(() -> { environment = value; instance = ""; }),
            value -> accept(() -> instance = value));
    }
    private static void accept(Runnable update) {
        var client = Minecraft.getInstance();
        var source = client.getConnection();
        client.execute(() -> {
            if (source == null || source != client.getConnection()) return;
            connection = source; hypixel = true; update.run();cachedScope=null;
            String key = WorldScope.hypixelKey(environment, instance);
            locationWorld = key.isEmpty() ? "" : WorldScope.hash(key);
        });
    }
    private static void context(Minecraft client){
        init();
        if (connection != client.getConnection()) {
            connection = client.getConnection(); instance = ""; hypixel = false; environment = 0;cachedScope=null;
            locationWorld = ""; cachedWorldKey = cachedWorldHash = "";
        }
        var server = client.getCurrentServer();
        String host = server == null ? "" : server.ip;
        if (!Objects.equals(host, cachedHost)) {
            cachedHost = host; normalizedHost = host.toLowerCase(Locale.ROOT);
            hypixelHost = WorldScope.isHypixelHost(host); cachedScope = null;
        }
        if(client.level==null||client.player==null){cachedScope=null;cachedLevel=cachedPlayer=null;}
    }
    public static Scope read(Minecraft client) {
        context(client);
        if (client.level == null || client.player == null || client.hasSingleplayerServer()) return EMPTY;
        int distance=client.options.getEffectiveRenderDistance();
        if(cachedScope!=null&&cachedLevel==client.level&&cachedPlayer==client.player&&cachedTick==client.player.tickCount&&cachedDistance==distance)return cachedScope;
        String world;
        if (hypixel || hypixelHost) world = hypixel ? locationWorld : "";
        else {
            String key = normalizedHost.isEmpty() ? "" : normalizedHost + ":" + client.level.dimension().identifier();
            if (!key.equals(cachedWorldKey)) {
                cachedWorldKey = key; cachedWorldHash = key.isEmpty() ? "" : WorldScope.hash(key);
            }
            world = cachedWorldHash;
        }
        cachedLevel=client.level;cachedPlayer=client.player;cachedTick=client.player.tickCount;cachedDistance=distance;
        if (world.isEmpty()) return cachedScope = EMPTY;
        var visible = new HashSet<UUID>();
        double maximum = distance * 16.0; maximum *= maximum;
        for (var player : client.level.players())
            if (player != client.player && client.player.distanceToSqr(player) <= maximum) visible.add(player.getUUID());
        Set<UUID> players = cachedScope != null && cachedScope.players().equals(visible) ? cachedScope.players() : Set.copyOf(visible);
        if (cachedScope != null && cachedScope.world().equals(world) && cachedScope.players() == players) return cachedScope;
        return cachedScope = new Scope(world, players);
    }
}
