// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.config.ConfigManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Isolated native model-picker regression, with no real account or multiplayer server. */
public final class ItemModelGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> { ConfigManager.syncEnabled=false; ConfigManager.swordBlockingSyncEnabled=false; });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            context.waitTicks(15);
            ItemAppearanceGameTest.check(context, world);
            context.waitTicks(5);
        }
    }
}
