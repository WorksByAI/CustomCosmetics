// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.update;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.utils.MessageUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Local chat only. No Minecraft server messages, automatic downloads or automatic link opening. */
public final class UpdateChecker {
    private static GitHubUpdateCheck check;
    private UpdateChecker() {}
    public static void init() {
        if (check != null) return;
        String installed = FabricLoader.getInstance().getModContainer("customcosmetics").orElseThrow()
            .getMetadata().getVersion().getFriendlyString();
        check = new GitHubUpdateCheck(installed);
        check.start();
    }
    public static void tick(Minecraft client) {
        if (check == null) return;
        String latest = check.poll(client.level != null && client.player != null);
        if (latest != null) ClientUiAccess.chat(client).addClientSystemMessage(message(check.installed(), latest));
    }
    public static MutableComponent message(String installed, String latest) {
        var link = Component.literal("[View Release]").withStyle(style -> style.withUnderlined(true)
            .withClickEvent(new ClickEvent.OpenUrl(GitHubUpdateCheck.RELEASE_PAGE)));
        return MessageUtils.createMessageWithPrefix(Component.literal("Update available: " + latest
            + " (installed: " + installed + "). ").append(link));
    }
}
