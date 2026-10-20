/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.sync;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.utils.MessageUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

@Environment(EnvType.CLIENT)
public final class SyncCommands {
    private SyncCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("customcosmetics")
            .executes(context -> {
                com.customcosmetics.client.CustomCosmeticsClient.requestGui(com.customcosmetics.gui.CosmeticsScreen.Page.MAIN);
                return 1;
            });



        root.then(
            ClientCommands.literal("notification")
                .executes(context -> showNotificationState())
                .then(ClientCommands.literal("on")
                    .executes(context -> setNotificationEnabled(true)))
                .then(ClientCommands.literal("off")
                    .executes(context -> setNotificationEnabled(false)))
        );

        root.then(
            ClientCommands.literal("sync")
                .executes(context -> syncNow())
                .then(ClientCommands.literal("on")
                    .executes(context -> setSyncEnabled(true)))
                .then(ClientCommands.literal("off")
                    .executes(context -> setSyncEnabled(false)))
        );

        root.then(ClientCommands.literal("party").executes(context->{
            com.customcosmetics.client.CustomCosmeticsClient.requestGui(com.customcosmetics.gui.CosmeticsScreen.Page.PARTY);
            return 1;
        }));
        root.then(ClientCommands.literal("dev").executes(context->SyncDebug.toggle())
            .then(ClientCommands.literal("on").executes(context->SyncDebug.setEnabled(true)))
            .then(ClientCommands.literal("off").executes(context->SyncDebug.setEnabled(false)))
            .then(ClientCommands.literal("status").executes(context->SyncDebug.status())));
        root.then(PlayerBlockCommands.command());
        root.then(com.customcosmetics.features.map.MapCommands.command());
        var waypoint = ClientCommands.literal("waypoint").executes(context -> {
            com.customcosmetics.client.CustomCosmeticsClient.requestScreen(()->new com.customcosmetics.gui.WaypointScreen(null));
            return 1;
        });
        var ordered = ClientCommands.literal("ordered");
        for (String action : new String[]{"next","previous","first","reset"}) {
            var command = ClientCommands.literal(action).executes(context -> {
                if (com.customcosmetics.features.waypoint.Waypoints.orderedAction(action)) return 1;
                MessageUtils.sendMessage("Waypoints: enable an ordered group in this area first.");
                return 0;
            });
            waypoint.then(command); ordered.then(command);
        }
        waypoint.then(ordered);
        root.then(waypoint);
        root.then(ClientCommands.literal("shortcuts").executes(context -> com.customcosmetics.features.shortcuts.Shortcuts.openNextTick()));
        var registered = dispatcher.register(root);
        dispatcher.register(ClientCommands.literal("ccos").executes(registered.getCommand()).redirect(registered));
    }

    private static int showNotificationState() {
        MessageUtils.sendMessage("Sync notification is " + (ConfigManager.syncNotifications ? "ON" : "OFF") + ".");
        return 1;
    }

    private static int setNotificationEnabled(boolean enabled) {
        ConfigManager.syncNotifications = enabled;
        ConfigManager.requestSave();
        MessageUtils.sendMessage("Sync notification " + (enabled ? "enabled." : "disabled."));
        return 1;
    }

    private static int syncNow() {
        if (!PresenceSync.syncOnce()) {
            MessageUtils.sendMessage("Sync cooldown: " + PresenceSync.manualSyncCooldownSeconds() + "s");
            return 0;
        }
        MessageUtils.sendSyncStartForCommand();
        return 1;
    }

    private static int setSyncEnabled(boolean enabled) {
        ConfigManager.syncEnabled = enabled;
        ConfigManager.requestSave();
        MessageUtils.sendMessage("Remote data autosync " + (enabled ? "enabled." : "disabled."));
        return 1;
    }
}
