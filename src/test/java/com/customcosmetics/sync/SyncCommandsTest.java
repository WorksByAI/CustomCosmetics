package com.customcosmetics.sync;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SyncCommandsTest {
    @Test void shortAliasPreservesTheOriginalCommandAndEverySubcommand() {
        var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
        SyncCommands.register(dispatcher);
        var original = dispatcher.getRoot().getChild("customskyblock");
        assertNotNull(original);
        assertNotNull(original.getCommand());
        for (String name : new String[]{"ccos", "csb", "customcosmetics"}) {
            var alias = dispatcher.getRoot().getChild(name);
            assertNotNull(alias);
            assertSame(original, alias.getRedirect());
            assertSame(original.getCommand(), alias.getCommand());
        }
        for (String name : new String[]{"notification", "sync", "party", "dev", "map", "waypoint", "shortcuts"})
            assertNotNull(original.getChild(name), name);
        for (String action : new String[]{"next", "previous", "first", "reset"}) {
            assertNotNull(original.getChild("waypoint").getChild(action), action);
            assertNotNull(original.getChild("waypoint").getChild("ordered").getChild(action), action);
        }
    }
}
