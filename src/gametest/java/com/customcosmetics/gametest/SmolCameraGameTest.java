// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.sky.Skyboxify;
import com.customcosmetics.features.smolpeople.SmolPlayers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SmolCameraGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ConfigManager.syncEnabled = false;
            ConfigManager.offlineMode = true;
            ConfigManager.smolpeopleMode = false;
        });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("fill -3 99 -7 3 99 7 minecraft:stone");
            world.getServer().runCommand("fill -3 100 -7 3 105 7 minecraft:air");
            world.getServer().runCommand("tp @p 0.5 100 0.5 0 0");
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(8);
            var original = context.computeOnClient(client -> Skyboxify.camera().position());
            context.runOnClient(client -> ConfigManager.smolpeopleMode = true);
            context.waitTicks(8);
            context.runOnClient(client -> {
                if (!SmolPlayers.shouldApplySmolTo(client.player.getId()))
                    throw new AssertionError("SmolPeople did not enable the miniature model");
                var camera = Skyboxify.camera();
                var pivot = miniaturePivot(client.player.position(), client.player.getEyePosition());
                if (Math.abs(camera.position().y - original.y) < 0.5)
                    throw new AssertionError("The front camera still frames the full-size eye height");
                assertFramed(pivot);
            });

            world.getServer().runCommand("fill -2 100 2 2 101 2 minecraft:stone");
            context.waitTicks(8);
            context.runOnClient(client -> {
                var pivot = miniaturePivot(client.player.position(), client.player.getEyePosition());
                var position = Skyboxify.camera().position();
                if (position.z >= 2 || position.distanceTo(pivot) >= 3.9)
                    throw new AssertionError("The camera went through the wall: " + position);
                assertFramed(pivot);
            });
            var aimedBlock = context.computeOnClient(client -> {
                if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
                    throw new AssertionError("The player's aim did not hit the test wall");
                return hit.getBlockPos();
            });
            context.runOnClient(client -> ConfigManager.smolpeopleMode = false);
            context.waitTicks(4);
            context.runOnClient(client -> {
                if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                        || !hit.getBlockPos().equals(aimedBlock))
                    throw new AssertionError("Changing the render camera changed the aimed block");
                ConfigManager.smolpeopleMode = true;
            });

            world.getServer().runCommand("fill -2 100 2 2 101 2 minecraft:air");
            world.getServer().runCommand("tp @p 0.5 100 0.5 0 55");
            context.waitTicks(8);
            context.runOnClient(client -> {
                var pivot = miniaturePivot(client.player.position(), client.player.getEyePosition());
                var position = Skyboxify.camera().position();
                if (position.y < 100 || position.distanceTo(pivot) >= 3.9)
                    throw new AssertionError("The camera went through the floor: " + position);
                assertFramed(pivot);
            });
            context.takeScreenshot("smolpeople-front-camera-floor");
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(8);
            context.runOnClient(client -> {
                if (Skyboxify.camera().position().distanceTo(client.player.getEyePosition()) > 1.0E-4)
                    throw new AssertionError("SmolPeople changed the first-person camera");
                ConfigManager.smolpeopleMode = false;
            });
            System.out.println("SMOLPEOPLE_FRONT_CAMERA_ALIGNMENT_AND_COLLISION_OK");
        }
    }

    private static Vec3 miniaturePivot(Vec3 feet, Vec3 eyes) {
        return feet.lerp(eyes, 0.6);
    }

    private static void assertFramed(Vec3 pivot) {
        var camera = Skyboxify.camera();
        var toFace = pivot.subtract(camera.position());
        var forward = new Vec3(camera.forwardVector());
        if (toFace.dot(forward) <= 0 || toFace.cross(forward).length() > 1.0E-4)
            throw new AssertionError("The miniature face is off the screen center: " + toFace);
    }
}
