// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.utils.MessageUtils;
import net.minecraft.client.Minecraft;

public final class ModuleNotifications {
    private ModuleNotifications() {}

    public static void show(String module, boolean enabled) {
        show(module, enabled ? "Module enabled" : "Module disabled", enabled);
    }

    public static void showAll(boolean enabled) {
        show("Module Manager", enabled ? "All modules enabled" : "All modules disabled", enabled);
    }

    private static void show(String title, String message, boolean enabled) {
        var client = Minecraft.getInstance();
        if (client == null) return;
        ClientUiAccess.chat(client).addClientSystemMessage(MessageUtils.createMessageWithPrefix(title)
            .append(" · ").append(UiText.component(message).withColor(enabled ? 0x55FF55 : 0xFF7777)));
    }
}
