// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.update.GitHubUpdateCheck;
import com.customcosmetics.update.UpdateChecker;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

final class UpdateNotificationGameTest {
    static void check(ClientGameTestContext context) throws ReflectiveOperationException {
        var field = UpdateChecker.class.getDeclaredField("check"); field.setAccessible(true);
        Object original = field.get(null);
        var fixture = new GitHubUpdateCheck("1.0.0");
        var start = GitHubUpdateCheck.class.getDeclaredMethod("start", Supplier.class, Executor.class); start.setAccessible(true);
        start.invoke(fixture, (Supplier<Optional<String>>)() -> Optional.of("1.0.1"), (Executor)Runnable::run);
        if (fixture.poll(false) != null) throw new AssertionError("Update appeared before a world was ready");
        boolean notification = ConfigManager.syncNotifications;
        try {
            context.runOnClient(client -> {
                ClientUiAccess.chat(client).clearMessages(true);
                ConfigManager.syncNotifications = false;
                field.set(null, fixture);
                UpdateChecker.tick(client); UpdateChecker.tick(client);
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                var messages = net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("allMessages");
                messages.setAccessible(true);
                int count = 0;
                for (Object line : (java.util.List<?>)messages.get(ClientUiAccess.chat(client))) {
                    var content = (net.minecraft.network.chat.Component)line.getClass().getMethod("content").invoke(line);
                    if (content.getString().startsWith("[CustomCosmetics] Update available:")) {
                        count++;
                        if (!content.getString().equals("[CustomCosmetics] Update available: 1.0.1 (installed: 1.0.0). [View Release]"))
                            throw new AssertionError("Wrong update chat message");
                        content.visit((style,text) -> {
                            if (style.getColor() == null || style.getColor().getValue() != 0xFFA8CA)
                                throw new AssertionError("Update notification is not GUI pink");
                            return Optional.empty();
                        }, content.getStyle());
                    }
                }
                if (count != 1) throw new AssertionError("Expected one real update chat message, got " + count);
            });
            context.takeScreenshot("update-checker-pink-chat-once");
            System.out.println("UPDATE_CHECKER_REAL_CHAT_ONCE_PINK_OK");
        } finally {
            context.runOnClient(client -> { field.set(null, original); ConfigManager.syncNotifications = notification; });
        }
    }
}
