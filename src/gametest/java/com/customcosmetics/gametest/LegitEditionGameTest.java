// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.client.ClientEdition;
import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.nickname.NickHider;
import com.customcosmetics.features.text.TextReplacements;
import com.customcosmetics.gui.CosmeticsScreen;
import com.customcosmetics.gui.PinkUi;
import com.customcosmetics.sync.SyncCommands;
import com.customcosmetics.utils.NickRenderUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

public final class LegitEditionGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ConfigManager.offlineMode = true;
            verify(ClientEdition.installed() == ClientEdition.LEGIT, "Wrong test edition");
            verify(ClientEdition.reportedVersion().endsWith("-legit"), "Legit did not report its edition");
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
            for (String name : new String[]{"cheating", "autoclick", "ac", "autoclicker", "leap"}) {
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
                ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.NICK_HIDER));
                verify(ClientUiAccess.screen(client).children().stream().filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).anyMatch(widget -> widget.getMessage().getString().equals("Hide My Name")),
                    "Missing Nick Hider controls");
                String account = client.player.getGameProfile().name();
                ConfigManager.nickHiderSelf = true;
                NickHider.changed(client);
                verify(TextReplacements.apply("Profile: " + account).equals("Profile: Player-1"), "Self alias was not applied");
                ConfigManager.nickTweaksEnabled = true;
                ConfigManager.nickTweaksNickChanger = "ChangerName";
                var field = (PinkUi.Field) button(ClientUiAccess.screen(client), "My Alias");
                field.setValue("Local Alias");
                var nametag = Component.literal(account);
                verify(TextReplacements.apply("Profile: " + account).equals("Profile: Local Alias"), "Custom self alias was not applied");
                verify(NickRenderUtils.handleFormattedText(nametag).getString().equals(account),
                    "Nick Changer rewrote the nametag before Nick Hider could apply");
                field.setValue("&6Time&#FF66BBDeo");
                var renderedAlias = new StringBuilder();
                var colors = new java.util.ArrayList<Integer>();
                nametag.getVisualOrderText().accept((index, style, codePoint) -> {
                    renderedAlias.appendCodePoint(codePoint);
                    colors.add(style.getColor().getValue());
                    return true;
                });
                verify(renderedAlias.toString().equals("TimeDeo") && colors.equals(java.util.List.of(
                    0xFFAA00, 0xFFAA00, 0xFFAA00, 0xFFAA00, 0xFF66BB, 0xFF66BB, 0xFF66BB)),
                    "Self alias formatting was visible as text or used incorrect colors");
                verify(client.font.width(account) == client.font.width(nametag.getVisualOrderText()),
                    "The raw and styled self alias widths differ");
                field.setValue("");
                verify(TextReplacements.apply(account).equals("Player-1"), "Clearing the field did not restore the automatic alias");
                ConfigManager.nickHiderSelf = false;
                NickHider.changed(client);
                verify(TextReplacements.apply(account).equals(account), "Disabling Nick Hider left its alias active");
                verify(NickRenderUtils.handleFormattedText(nametag).getString().equals("ChangerName"),
                    "Disabling Nick Hider did not restore the Nick Changer name");
                ConfigManager.nickTweaksEnabled = false;
                NickRenderUtils.invalidateLocalTargetsCache();
                ClientUiAccess.setScreen(client, null);
            });
            verifyModuleReset(context);
        }
        System.out.println("CSB_LEGIT_EDITION_OK");
    }

    private static void verifyModuleReset(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ConfigManager.zoomFactor = 9;
            ConfigManager.zoomEnabled = true;
            ConfigManager.hitOpacity = .73f;
            ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.ZOOM));
        });
        context.waitTicks(10);
        context.runOnClient(client -> {
            var screen = ClientUiAccess.screen(client);
            clickButton(screen, "Reset Settings");
            verify(ConfigManager.zoomFactor == 9 && ConfigManager.zoomEnabled, "First click reset the Zoom module");
        });
        context.waitTicks(10);
        context.runOnClient(client -> {
            clickButton(ClientUiAccess.screen(client), "Confirm Reset");
            verify(ConfigManager.zoomFactor == 4 && !ConfigManager.zoomEnabled, "Zoom settings did not reset");
            verify(ConfigManager.hitOpacity == .73f, "Zoom reset changed the Hit Color module");
            ClientUiAccess.setScreen(client, null);
        });
    }

    private static AbstractWidget button(Screen screen, String label) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private static void clickButton(Screen screen, String label) {
        var button = button(screen, label);
        var click = new MouseButtonEvent(button.getX() + 4, button.getY() + 4, new MouseButtonInfo(ClientInput.MOUSE_LEFT, 0));
        verify(screen.mouseClicked(click, false), "Could not click " + label);
        screen.mouseReleased(click);
    }

    private static void verify(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
