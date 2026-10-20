// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaypointSessionTest {
    @Test void proxyAddressNeverCreatesAVanillaScopeAndApiEventsSelectIslands() {
        var session = new WaypointSession();
        Object acceleratedConnection = new Object();
        assertEquals("", session.scope(acceleratedConnection, () -> true));
        session.hello(acceleratedConnection);
        assertEquals("", session.scope(acceleratedConnection, () -> false));
        assertEquals("hypixel:unknown", session.scope(acceleratedConnection, () -> true));
        session.location(acceleratedConnection, "SKYBLOCK", "hub");
        assertEquals("hypixel:hub", session.scope(acceleratedConnection, () -> fail("Known island should not scan sidebar")));
        session.location(acceleratedConnection, "SKYBLOCK", "mining_3");
        assertEquals("hypixel:mining_3", session.scope(acceleratedConnection, () -> false));
        session.location(acceleratedConnection, "SKYBLOCK", "future_island");
        assertEquals("hypixel:unknown", session.scope(acceleratedConnection, () -> false));
        session.location(acceleratedConnection, "BEDWARS", "hub");
        assertEquals("", session.scope(acceleratedConnection, () -> false));
    }

    @Test void onlyCurrentConfirmedHypixelConnectionAcceptsLocrawAndRenders() {
        var session = new WaypointSession();
        Object first = new Object(), second = new Object();
        session.hello(first);
        assertFalse(session.locraw(second, "SKYBLOCK", "hub"));
        assertTrue(session.locraw(first, "SKYBLOCK", "garden"));
        assertEquals("hypixel:garden", session.scope(first, () -> false));
        assertTrue(session.follow(second));
        assertEquals("", session.scope(first, () -> true));
        assertEquals("", session.scope(second, () -> true));
        assertFalse(session.locraw(second, "SKYBLOCK", "hub"));
        session.location(second, "SKYBLOCK", "crimson_isle");
        assertEquals("hypixel:crimson_isle", session.scope(second, () -> false));
        session.reset(null);
        assertEquals("", session.scope(second, () -> true));
    }
}
