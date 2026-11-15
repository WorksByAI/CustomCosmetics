// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NickHiderTest {
    @Test void readsClassesFromTabAndSidebar() {
        assertEquals(new NickHider.Member("example", "Mage"),
            NickHider.tabMember("§a[220] [MVP+] Example ✦ (Mage XXX)"));
        assertEquals(new NickHider.Member("playername", "Berserk"),
            NickHider.tabMember("PlayerName (Berserker 25)"));
        assertEquals(new NickHider.Member("playername", ""), NickHider.tabMember("PlayerName (DEAD)"));
        assertEquals(new NickHider.Member("tank_name", "Tank"),
            NickHider.sidebarMember(" [T] [MVP+] Tank_Name 120❤"));
        assertNull(NickHider.tabMember("Dungeon Floor (M7)"));
        assertNull(NickHider.sidebarMember("[MVP+] LobbyPlayer"));
    }

    @Test void keepsTheSelfAliasLiteralAndAllowsAutomaticFallback() {
        assertEquals("", NickHider.selfAlias(null));
        assertEquals("", NickHider.selfAlias(" \n\u200b "));
        assertEquals("本地 Alias", NickHider.selfAlias("  本地 Alias  "));
        assertEquals("𠀋".repeat(30), NickHider.selfAlias("𠀋".repeat(31)));
    }

    @Test void keepsThePrefixLiteralAndLimitsCodePoints() {
        assertEquals("Player", NickHider.prefix(" \n\u200b "));
        assertEquals("旁观者", NickHider.prefix("  旁观\n者  "));
        assertEquals("𠀋".repeat(24), NickHider.prefix("𠀋".repeat(25)));
    }
}
