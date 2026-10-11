// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.animations;

import net.minecraft.client.Minecraft;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Official Hypixel location events, plus a local list of nearby loaded players. */
public final class PresenceWorld {
    private static boolean initialized;
    private static Object connection;
    private static int environment;
    private static String instance = "";
    private static boolean hypixel;
    private PresenceWorld() {}
    public record Scope(String world, Set<UUID> players) {}

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
            connection = source; hypixel = true; update.run();
        });
    }
    public static Scope read(Minecraft client) {
        init();
        if (connection != client.getConnection()) {
            connection = client.getConnection(); instance = ""; hypixel = false; environment = 0;
        }
        if (client.level == null || client.player == null || client.hasSingleplayerServer()) return new Scope("", Set.of());
        var server = client.getCurrentServer();
        String host = server == null ? "" : server.ip.toLowerCase(Locale.ROOT);
        String key = hypixel || host.matches("(?:.*\\.)?hypixel\\.net(?::[0-9]+)?")
            ? hypixel ? WorldScope.hypixelKey(environment, instance) : ""
            : host.isEmpty() ? "" : host + ":" + client.level.dimension().identifier();
        var candidates = client.level.players().stream().filter(player -> player != client.player)
            .map(player -> new WorldScope.Candidate(player.getUUID(), client.player.distanceToSqr(player))).toList();
        return new Scope(key.isEmpty() ? "" : WorldScope.hash(key), WorldScope.visible(candidates, client.options.getEffectiveRenderDistance() * 16.0));
    }
}
