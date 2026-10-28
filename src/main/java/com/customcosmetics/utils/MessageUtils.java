/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import com.customcosmetics.sync.SyncFailure;

@Environment(EnvType.CLIENT)
public class MessageUtils {
    
    private static final float PASTEL_SATURATION = 0.45f;
    private static final int MESSAGE_COLOR = com.customcosmetics.gui.PinkUi.PINK & 0xFFFFFF;

    public static MutableComponent createMessageWithPrefix(String message) {
        return createMessageWithPrefix(com.customcosmetics.gui.UiText.component(message));
    }
    
    public static MutableComponent createMessageWithPrefix(MutableComponent message) {
        return Component.literal("[CustomCosmetics] ").withStyle(style -> style.withColor(MESSAGE_COLOR))
            .append(styleMessage(message));
    }

    /** Apply the GUI color to every span while retaining click actions and text formatting. */
    public static MutableComponent styleMessage(Component message) {
        MutableComponent styled = message.plainCopy().setStyle(message.getStyle().withColor(MESSAGE_COLOR));
        for (Component child : message.getSiblings()) styled.append(styleMessage(child));
        return styled;
    }
    
    public static int animatedPastelRgbAt(float x, long nowMs) {
        int cycleMs = 4000;
        float time = 1.0f - (nowMs % cycleMs) / (float) cycleMs;
        float hue = (float) positiveModulo(time + (x % 100.0f) / 100.0f, 1.0);
        return net.minecraft.util.Mth.hsvToRgb(hue, PASTEL_SATURATION, 1.0f);
    }
    
    private static double positiveModulo(double value, double mod) {
        double result = value % mod;
        return result < 0 ? result + mod : result;
    }
    
    public static void sendMessage(String message) {
        com.customcosmetics.gui.UiLocale.refresh();
        sendCustomMessage(createMessageWithPrefix(message));
    }
    
    public static void sendCustomMessage(MutableComponent message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null)
                com.customcosmetics.compatibility.ClientUiAccess.chat(client).addClientSystemMessage(styleMessage(message));
        });
    }

    public static void sendUserText(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return;
        }
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.connection == null) {
            return;
        }
        if (text.charAt(0) == '/') {
            player.connection.sendCommand(text.substring(1));
        } else {
            player.connection.sendChat(text);
        }
    }

    public static void sendSyncStartForCommand() {
        sendMessage("Syncing remote data...");
    }

    public static void sendSyncResult(boolean success, boolean isNotification) {
        com.customcosmetics.gui.UiLocale.refresh();
        sendCustomMessage(createSyncResult(success, isNotification));
    }

    public static void sendSyncResult(boolean success, boolean isNotification, SyncFailure failure) {
        com.customcosmetics.gui.UiLocale.refresh();
        sendCustomMessage(createSyncResult(success, isNotification, failure));
    }

    public static MutableComponent createSyncResult(boolean success, boolean isNotification) {
        return createSyncResult(success, isNotification, null);
    }

    public static MutableComponent createSyncResult(boolean success, boolean isNotification, SyncFailure failure) {
        String base = success ? "Succeeded to sync remote data!" : failure == null ? "Failed to sync remote data."
            : com.customcosmetics.gui.UiText.tr("Failed to sync remote data: ") + com.customcosmetics.gui.UiText.tr(failure.description()) + ".";
        MutableComponent msg = com.customcosmetics.gui.UiText.component(base);

        if (isNotification) {
            MutableComponent stop = com.customcosmetics.gui.UiText.component("[stop notifications]")
                .withStyle(style -> style
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent.RunCommand("/customcosmetics notification off")));
            msg.append(Component.literal(" ")).append(stop);
        }
        return createMessageWithPrefix(msg);
    }

}
