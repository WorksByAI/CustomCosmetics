// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.update;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.utils.MessageUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** One startup offer, with downloads only after the player opts in. */
public final class UpdateChecker {
    private static GitHubUpdateCheck check;
    private static boolean automatic;
    private UpdateChecker() {}
    public static void init() {
        if (check != null) return;
        String installed = FabricLoader.getInstance().getModContainer("customcosmetics").orElseThrow()
            .getMetadata().getVersion().getFriendlyString();
        automatic=com.customcosmetics.config.ConfigManager.updateOnNextLaunch;
        check = new GitHubUpdateCheck(installed,()->com.customcosmetics.config.ConfigManager.updateCheckEnabled);
        if (com.customcosmetics.sync.RemoteAccess.online()) check.start();
    }
    public static void tick(Minecraft client) {
        if (check == null || !com.customcosmetics.config.ConfigManager.updateCheckEnabled || !com.customcosmetics.sync.RemoteAccess.online()) return;
        if(check.settled())return;
        check.start();
        var screen=ClientUiAccess.screen(client);
        boolean title=screen instanceof net.minecraft.client.gui.screens.TitleScreen;
        String latest=check.poll(title||client.level!=null&&client.player!=null);
        if(latest!=null){
            if(title||automatic)ClientUiAccess.setScreen(client,new com.customcosmetics.gui.UpdateScreen(screen,check.installed(),latest,automatic));
            else ClientUiAccess.chat(client).addClientSystemMessage(message(check.installed(),latest));
        }
    }
    public static MutableComponent message(String installed, String latest) {
        var link = Component.literal("[View Release]").withStyle(style -> style.withUnderlined(true)
            .withClickEvent(new ClickEvent.OpenUrl(GitHubUpdateCheck.RELEASE_PAGE)));
        return MessageUtils.createMessageWithPrefix(Component.literal("Update available: " + latest
            + " (installed: " + installed + "). ").append(link));
    }
}
