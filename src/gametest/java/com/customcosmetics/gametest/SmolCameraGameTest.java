// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.sky.Skyboxify;
import com.customcosmetics.features.smolpeople.SmolPlayers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;

public final class SmolCameraGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ConfigManager.syncEnabled = false;
            ConfigManager.offlineMode = true;
            ConfigManager.smolpeopleMode = false;
        });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("execute as @p at @s run fill ~-2 ~-1 ~1 ~2 ~-1 ~6 minecraft:stone");
            world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 45");
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(8);
            var original = context.computeOnClient(client -> {
                var position = Skyboxify.camera().position();
                if (position.distanceTo(client.player.getEyePosition()) >= 3.9)
                    throw new AssertionError("The floor did not obstruct the vanilla camera");
                return position;
            });
            context.runOnClient(client -> ConfigManager.smolpeopleMode = true);
            context.waitTicks(8);
            context.runOnClient(client -> {
                if (!SmolPlayers.shouldApplySmolTo(client.player.getId()))
                    throw new AssertionError("SmolPeople did not enable the miniature model");
                var position = Skyboxify.camera().position();
                if (original.distanceTo(position) > 1.0E-4)
                    throw new AssertionError("SmolPeople moved the camera after collision: " + original + " -> " + position);
            });
            context.takeScreenshot("smolpeople-front-camera-floor");
            context.runOnClient(client -> {
                ConfigManager.smolpeopleMode = false;
                client.options.setCameraType(CameraType.FIRST_PERSON);
            });
            System.out.println("SMOLPEOPLE_FRONT_CAMERA_PRESERVED_OK");
        }
    }
}
