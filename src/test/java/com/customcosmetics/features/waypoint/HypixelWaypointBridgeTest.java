// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.hypixel.data.region.Environment;
import net.hypixel.data.type.ServerType;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HypixelWaypointBridgeTest {
    @Test void realApiHelloAndLocationProvideSkyblockModeWithoutReadingServerAddress() {
        var hellos = new AtomicInteger();
        var serverType = new AtomicReference<String>();
        var mode = new AtomicReference<String>();
        assertTrue(HypixelWaypointBridge.register(getClass().getClassLoader(), hellos::incrementAndGet,
                (type, island) -> { serverType.set(type); mode.set(island); }));
        var api = HypixelModAPI.getInstance();
        api.handle(new ClientboundHelloPacket(Environment.getById(0).orElseThrow()));
        api.handle(new ClientboundLocationPacket("mini37", ServerType.valueOf("SKYBLOCK").orElseThrow(), null, "hub", null));
        assertEquals(1, hellos.get());
        assertEquals("SKYBLOCK", serverType.get());
        assertEquals("hub", mode.get());
        api.handle(new ClientboundLocationPacket("mini38", ServerType.valueOf("SKYBLOCK").orElseThrow(), null, null, null));
        assertEquals("", mode.get());
    }

    @Test void unavailableApiDoesNotGuessAHypixelConnection() {
        var unavailable = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.hypixel.")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        assertFalse(HypixelWaypointBridge.register(unavailable, () -> fail(), (type, mode) -> fail()));
    }
}
