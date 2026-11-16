package com.customcosmetics.sync;

import com.customcosmetics.client.ClientEdition;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SyncCommandsTest {
    @Test void shortAliasPreservesTheOriginalCommandAndEverySubcommand() {
        for (var edition : ClientEdition.values()) verifyCommands(edition);
    }

    private static void verifyCommands(ClientEdition edition) {
        var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
        SyncCommands.register(dispatcher, edition);
        var original = dispatcher.getRoot().getChild("customskyblock");
        assertNotNull(original);
        assertNotNull(original.getCommand());
        var alias = dispatcher.getRoot().getChild("csb");
        assertNotNull(alias);
        assertSame(original, alias.getRedirect());
        assertSame(original.getCommand(), alias.getCommand());
        assertNull(dispatcher.getRoot().getChild("ccos"));
        assertNull(dispatcher.getRoot().getChild("customcosmetics"));
        for (String name : new String[]{"notification", "sync", "party", "dev", "map", "waypoint", "shortcuts"})
            assertNotNull(original.getChild(name), name);
        for (String action : new String[]{"next", "previous", "first", "reset"}) {
            assertNotNull(original.getChild("waypoint").getChild(action), action);
            assertNotNull(original.getChild("waypoint").getChild("ordered").getChild(action), action);
        }
        for (String restricted : new String[]{"cheating", "autoclick", "ac", "autoclicker", "leap"})
            assertEquals(edition == ClientEdition.CHEATING, original.getChild(restricted) != null, restricted);
        if (edition != ClientEdition.CHEATING) return;
        for (String name : new String[]{"autoclick", "ac", "autoclicker"}) {
            var autoClick = original.getChild(name);
            assertNotNull(autoClick.getChild("list").getCommand());
            for (String action : new String[]{"add", "remove", "clear"}) {
                for (String side : new String[]{"left", "right"}) {
                    assertNotNull(autoClick.getChild(action).getChild(side).getCommand());
                }
            }
        }
    }
}
