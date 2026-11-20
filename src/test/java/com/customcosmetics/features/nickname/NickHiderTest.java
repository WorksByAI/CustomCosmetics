// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.customcosmetics.config.ConfigManager;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NickHiderTest {
    @Test void dungeonClassesRespectTheSelfSwitchAndPreferExplicitAliases() {
        boolean previousSelf = ConfigManager.nickHiderSelf;
        boolean previousPlayers = ConfigManager.nickHiderPlayers;
        String previousAlias = ConfigManager.nickHiderSelfAlias;
        String previousPrefix = ConfigManager.nickHiderPrefix;
        var own = Set.of("myaccount", "myprofile", "mydungeonnick");
        var numbers = Map.of("myprofile", 1, "mydungeonnick", 2, "teammate", 3, "pendingclass", 4, "stranger", 5);
        var roles = Map.of("mydungeonnick", "Archer", "teammate", "Mage", "pendingclass", "");
        try {
            ConfigManager.nickHiderPlayers = false;
            ConfigManager.nickHiderSelf = false;
            ConfigManager.nickHiderSelfAlias = "&6Alias";
            ConfigManager.nickHiderPrefix = "Player";
            assertEquals(Map.of("teammate", "Mage"), NickHider.buildAliases("myprofile", own, numbers, roles).names());

            ConfigManager.nickHiderSelf = true;
            var explicit = NickHider.buildAliases("myprofile", own, numbers, roles);
            assertEquals("&6Alias", explicit.names().get("mydungeonnick"));
            assertEquals(own, explicit.styled());

            ConfigManager.nickHiderSelfAlias = "";
            var automatic = NickHider.buildAliases("myprofile", own, numbers, roles);
            for (String name : own) assertEquals("Archer", automatic.names().get(name));
            assertEquals(Set.of(), automatic.styled());
            assertEquals("Player-1", NickHider.buildAliases("myprofile", own, numbers, Map.of()).names().get("myaccount"));

            ConfigManager.nickHiderSelf = false;
            ConfigManager.nickHiderPlayers = true;
            assertEquals(Map.of("teammate", "Mage", "pendingclass", "Player-4", "stranger", "Player-5"),
                NickHider.buildAliases("myprofile", own, numbers, roles).names());
        } finally {
            ConfigManager.nickHiderSelf = previousSelf;
            ConfigManager.nickHiderPlayers = previousPlayers;
            ConfigManager.nickHiderSelfAlias = previousAlias;
            ConfigManager.nickHiderPrefix = previousPrefix;
        }
    }

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

    @Test void keepsSelfAliasFormattingWithoutCountingCodesTowardTheDisplayLimit() {
        assertEquals("", NickHider.selfAlias(null));
        assertEquals("", NickHider.selfAlias(" \n\u200b "));
        assertEquals("本地 Alias", NickHider.selfAlias("  本地 Alias  "));
        assertEquals("𠀋".repeat(30), NickHider.selfAlias("𠀋".repeat(31)));
        assertEquals("&6" + "𠀋".repeat(29) + "&#FF66BB火", NickHider.selfAlias("§6" + "𠀋".repeat(29) + "&#FF66BB火a"));
        assertEquals("", NickHider.selfAlias("&6&#FF66BB&r"));
    }

    @Test void keepsThePrefixLiteralAndLimitsCodePoints() {
        assertEquals("Player", NickHider.prefix(" \n\u200b "));
        assertEquals("旁观者", NickHider.prefix("  旁观\n者  "));
        assertEquals("𠀋".repeat(24), NickHider.prefix("𠀋".repeat(25)));
    }
}
