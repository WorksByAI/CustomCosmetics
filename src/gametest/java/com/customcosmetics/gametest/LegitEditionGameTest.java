// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.client.ClientEdition;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.gui.CosmeticsScreen;
import com.customcosmetics.sync.SyncCommands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;

public final class LegitEditionGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ConfigManager.offlineMode = true;
            verify(ClientEdition.installed() == ClientEdition.LEGIT, "Wrong test edition");
            verify(!FabricLoader.getInstance().isModLoaded("customskyblock-dungeon"), "Legit loaded Quoi");
            ConfigManager.gameplayPanelConfirmed = true;
            ConfigManager.gameplayModules = java.util.Set.of("AUTO_CLICKER");
            ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.MAIN));
            var screen = ClientUiAccess.screen(client);
            verify(screen.getTitle().getString().equals("CustomSkyBlock"), "Wrong settings title");
            verify(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .noneMatch(widget -> widget.getMessage().getString().equals("Cheating")), "Cheating button visible with an old config");
            var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
            SyncCommands.register(dispatcher);
            var root = dispatcher.getRoot().getChild("customskyblock");
            for (String name : new String[]{"cheating", "autoclicker", "leap"}) {
                verify(root.getChild(name) == null, "Legit registered " + name);
            }
            verify(root.getChild("waypoint") != null && root.getChild("shortcuts") != null, "Missing QOL commands");
            for (String alias : new String[]{"customskyblock", "csb"}) {
                try { verify(dispatcher.execute(alias, (FabricClientCommandSource) null) == 1, "Missing settings command " + alias); }
                catch (CommandSyntaxException error) { throw new AssertionError(error); }
            }
        });
        try (var world = context.worldBuilder().create()) {
            context.waitTicks(6);
            context.runOnClient(client -> {
                ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.ZOOM));
                verify(ClientUiAccess.screen(client).children().stream().filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).anyMatch(widget -> widget.getMessage().getString().equals("Zoom")),
                    "Missing QOL controls");
                ClientUiAccess.setScreen(client, null);
            });
            context.waitTicks(4);
        }
        System.out.println("CSB_LEGIT_EDITION_OK");
    }

    private static void verify(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
