package com.customcosmetics.features.animations;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.hypixel.data.region.Environment;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldScopeTest {
    @Test void hostAndInstanceValidationPreservesServerBoundaries() {
        for (String host : List.of("hypixel.net", "MC.HYPIXEL.NET:25565", "a.b.hypixel.net"))
            assertTrue(WorldScope.isHypixelHost(host), host);
        for (String host : List.of("nothypixel.net", "hypixel.net.evil.test", "mc.hypixel.net:", "mc.hypixel.net:x", "[::1]:25565"))
            assertFalse(WorldScope.isHypixelHost(host), host);
        assertFalse(WorldScope.isHypixelHost(null));
        for (String instance : List.of("", "with space", "mini\n1", "中", "x".repeat(65)))
            assertEquals("", WorldScope.hypixelKey(0, instance));
        assertEquals("$hypixel0:mini_1-~", WorldScope.hypixelKey(0, "MINI_1-~"));
    }
    @Test void visibleMembershipDeduplicatesWithoutSortingOrLimiting() {
        UUID id = UUID.randomUUID();
        assertEquals(Set.of(id), WorldScope.visible(List.of(new WorldScope.Candidate(id, 4),
            new WorldScope.Candidate(id, 1), new WorldScope.Candidate(UUID.randomUUID(), -1),
            new WorldScope.Candidate(UUID.randomUUID(), Double.POSITIVE_INFINITY)), 2));
    }
    @Test void realOfficialApiHelloAndLocationChangeInstanceWithoutChatCommands() {
        var env = new AtomicInteger(-1); var instance = new AtomicReference<String>();
        assertTrue(HypixelLocationBridge.register(getClass().getClassLoader(), env::set, instance::set));
        var api = HypixelModAPI.getInstance();
        api.handle(new ClientboundHelloPacket(Environment.getById(0).orElseThrow()));
        api.handle(new ClientboundLocationPacket("mini37FG", null, null, null, null));
        assertEquals("$hypixel0:mini37fg", WorldScope.hypixelKey(env.get(), instance.get()));
        String before = WorldScope.hash(WorldScope.hypixelKey(env.get(), instance.get()));
        api.handle(new ClientboundLocationPacket("mini38FG", null, null, null, null));
        assertNotEquals(before, WorldScope.hash(WorldScope.hypixelKey(env.get(), instance.get())));
        assertNotEquals(before, WorldScope.hash(WorldScope.hypixelKey(1, "mini37FG")));
        assertEquals("", WorldScope.hypixelKey(0, "")); assertEquals("", WorldScope.hypixelKey(0, "/locraw"));
    }
    @Test void missingOptionalApiDoesNotCrashOrGuessAHypixelWorld() {
        var unavailable = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.hypixel.")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        assertFalse(HypixelLocationBridge.register(unavailable, value -> fail(), value -> fail()));
        assertEquals("", WorldScope.hypixelKey(-1, "mini37FG"));
    }
    @Test void vanillaRenderDistanceIncludesAllLoadedPlayersWithoutAnExtraPlayerLimit() {
        var players = new ArrayList<WorldScope.Candidate>();
        for (int i = 90; i >= 1; i--) players.add(new WorldScope.Candidate(new UUID(0, i), i * i));
        players.add(new WorldScope.Candidate(new UUID(0, 91), Double.NaN));
        var actual = WorldScope.visible(players, 64);
        assertEquals(64, actual.size()); assertTrue(actual.contains(new UUID(0, 1))); assertTrue(actual.contains(new UUID(0, 49)));
        assertEquals(16, WorldScope.visible(players, 16).size()); assertTrue(WorldScope.visible(players, Double.NaN).isEmpty());
        assertEquals(90, WorldScope.visible(players, 12 * 16).size());
    }
}
