// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.blocking.BlockHand;
import com.customcosmetics.features.blocking.SwordBlocking;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/** Real input and GPU rendering in an isolated singleplayer world; no external servers or credentials. */
@SuppressWarnings("UnstableApiUsage")
public final class BlockingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ConfigManager.syncEnabled = false;
            ConfigManager.swordBlockingSyncEnabled = false;
            ConfigManager.swordBlockingEnabled = true;
            ConfigManager.swordBlockingStyle = "default";
            ConfigManager.swordBlockingListMode = "off";
            ConfigManager.swordBlockingBlacklist = new String[]{"skyblock:ASPECT_OF_THE_VOID"};
            ConfigManager.swordBlockingWhitelist = new String[0];
            ConfigManager.smolpeopleMode = false;
            ConfigManager.resetAnimations();
            client.options.setCameraType(CameraType.FIRST_PERSON);
        });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            waitForWorldRender(world);
            if(Boolean.getBoolean("customcosmetics.nicknameOnly")) {
                NicknameGradientGameTest.check(context,world);context.waitTicks(5);return;
            }
            if(Boolean.getBoolean("customcosmetics.itemAppearanceOnly")) {
                ItemAppearanceGameTest.check(context,world);context.waitTicks(5);return;
            }
            checkItem(context, world, "minecraft:diamond_sword", false, BlockHand.MAIN, "diamond-main");
            checkItem(context, world, "minecraft:blaze_rod[minecraft:custom_data={id:\"ATOMSPLIT_KATANA\"}]", false,
                BlockHand.MAIN, "atomsplit-main");
            checkItem(context, world, "minecraft:diamond_sword", true, BlockHand.OFF, "diamond-off");
            checkItem(context, world, "minecraft:stick", false, BlockHand.NONE, "stick-no-block");
            checkContinuousVisualBlockhit(context, world);
            checkIgnoreHaste(context, world);
            checkAnimations(context, world);
            try { UpdateNotificationGameTest.check(context); }
            catch (ReflectiveOperationException error) { throw new AssertionError("Update chat verification failed", error); }
            try { SettingsGuiGameTest.check(context); }
            catch (Exception error) { throw new AssertionError("Settings GUI verification failed",error); }
            try { BlockingStyleGameTest.check(context, world); }
            catch (Exception error) { throw new AssertionError("First-person style verification failed",error); }
            BlockingListGameTest.check(context, world);
            context.runOnClient(client -> ConfigManager.swordBlockingStyle = "jigsaw");
            try { RemoteCosmeticsGameTest.check(context); }
            catch (ReflectiveOperationException error) { throw new AssertionError("Cannot inspect remote render state", error); }
            ItemAppearanceGameTest.check(context,world);
            NicknameGradientGameTest.check(context,world);
            context.runOnClient(client -> ConfigManager.swordBlockingStyle = "default");
            // Finish queued render work and flush the disposable world while it is still connected.
            context.waitTicks(5);
            world.getServer().runCommand("save-all flush");
        }
        System.out.println("BLOCKING_GAME_TEST_OK");
    }

    private static void checkContinuousVisualBlockhit(ClientGameTestContext context, TestSingleplayerContext world) {
        // Bedrock in survival keeps the crosshair target present without altering vanilla input logic.
        world.getServer().runCommand("gamemode survival @a");
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:iron_sword");
        world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:air");
        var target = new java.util.concurrent.atomic.AtomicReference<net.minecraft.core.BlockPos>();
        var height = new java.util.concurrent.atomic.AtomicReference<Double>();
        context.runOnClient(client -> {
            target.set(net.minecraft.core.BlockPos.containing(client.player.getEyePosition()).offset(0, 0, 2));
            height.set(client.player.getY());
        });
        var p = target.get();
        world.getServer().runCommand("fill " + (p.getX() - 1) + " " + (p.getY() - 1) + " " + p.getZ() + " "
            + (p.getX() + 1) + " " + (p.getY() + 1) + " " + p.getZ() + " minecraft:bedrock");
        // Aim from the block's center, rather than console-relative coordinates on a grid boundary.
        world.getServer().runCommand("tp @a " + (p.getX() + .5) + " " + height.get() + " " + (p.getZ() - 1.5) + " 0 0");
        context.waitTicks(5);
        context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(0); });
        context.waitFor(client -> client.hitResult != null && client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK);
        context.waitTicks(10);
        context.runOnClient(client -> com.customcosmetics.compatibility.ClientUiAccess.chat(client).clearMessages(true));
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(3);
        context.takeScreenshot("blocking-iron-raised-guard");
        context.getInput().holdKey(options -> options.keyAttack);
        float maximum = 0;
        for (int frame = 0; frame < 12; frame++) {
            context.takeScreenshot("blockhit-on-block-frame-" + String.format("%02d", frame));
            maximum = Math.max(maximum, RenderAssertions.blockingSwing);
        }
        if (maximum <= .1f) throw new AssertionError("Held buttons did not produce continuous visual blockhit");
        context.waitTicks(40);
        context.runOnClient(client -> {
            if (!com.customcosmetics.features.blocking.VisualBlockhit.matches(net.minecraft.world.InteractionHand.MAIN_HAND))
                throw new AssertionError("Continuous blockhit stopped while both buttons remained held on a block");
        });
        context.getInput().releaseKey(options -> options.keyAttack);
        context.waitTicks(10);
        context.runOnClient(client -> {
            if (RenderAssertions.blockingSwing != 0 || SwordBlocking.localHand() != BlockHand.MAIN)
                throw new AssertionError("Releasing left click did not restore the right-click guard");
        });
        context.getInput().releaseKey(options -> options.keyUse);
        context.runOnClient(client -> ConfigManager.animationSwingSpeed = -2);
        context.waitTicks(3); context.getInput().holdKey(options -> options.keyAttack);
        checkSlowProgress(context, false);
        context.getInput().releaseKey(options -> options.keyAttack); context.waitTicks(3);
        context.getInput().holdKey(options -> options.keyUse); context.getInput().holdKey(options -> options.keyAttack);
        checkSlowProgress(context, true);
        context.takeScreenshot("slow-blockhit-minus-two");
        context.runOnClient(client -> { ConfigManager.animationSwingSpeed = 0; ConfigManager.animationDisableHandMove = true; });
        float zero = blockingDepthRange(context, 0), normal = blockingDepthRange(context, 1), doubled = blockingDepthRange(context, 2);
        if (zero > .0001f || normal < .001f || doubled < normal * 1.7f)
            throw new AssertionError("Real blocking renderer ignored Swing Z: 0=" + zero + " 1=" + normal + " 2=" + doubled);
        context.takeScreenshot("blocking-swing-z-double");
        System.out.println("BLOCKING_SWING_Z_RENDER_OK zero=" + zero + " normal=" + normal + " doubled=" + doubled);
        context.getInput().releaseKey(options -> options.keyAttack); context.getInput().releaseKey(options -> options.keyUse);
        context.runOnClient(client -> ConfigManager.resetAnimations()); context.waitTicks(3);
        NoammSwingGameTest.check(context, world);
        world.getServer().runCommand("fill " + (p.getX() - 1) + " " + (p.getY() - 1) + " " + p.getZ() + " "
            + (p.getX() + 1) + " " + (p.getY() + 1) + " " + p.getZ() + " minecraft:air");
        world.getServer().runCommand("gamemode creative @a");
        context.waitTicks(5);
        System.out.println("CONTINUOUS_VISUAL_BLOCKHIT_OK");
    }
    private static float blockingDepthRange(ClientGameTestContext context, double factor) {
        context.runOnClient(client -> {
            ConfigManager.animationSwingZ = factor; RenderAssertions.minBlockingDepth = Float.POSITIVE_INFINITY;
            RenderAssertions.maxBlockingDepth = Float.NEGATIVE_INFINITY; RenderAssertions.depthCalls.set(0);
            RenderAssertions.trackBlockingDepth = true;
        });
        context.waitTicks(24);
        context.runOnClient(client -> RenderAssertions.trackBlockingDepth = false);
        if (RenderAssertions.depthCalls.get() < 10) throw new AssertionError("The real renderer did not draw enough blocking frames");
        return RenderAssertions.maxBlockingDepth - RenderAssertions.minBlockingDepth;
    }
    private static void checkSlowProgress(ClientGameTestContext context, boolean blocking) {
        var values = new java.util.concurrent.atomic.AtomicReference<float[]>(new float[]{0,0});
        for (int tick = 0; tick < 65; tick++) {
            context.waitTicks(1);
            context.runOnClient(client -> {
                float current = blocking ? com.customcosmetics.features.blocking.VisualBlockhit.progress(0)
                    : com.customcosmetics.features.animations.ViewModelAnimations.swing(
                        com.customcosmetics.compatibility.SwingAccess.progress(client.player),0,
                        net.minecraft.world.InteractionHand.MAIN_HAND,client.player.getMainHandItem());
                float[] seen = values.get();
                if (current < seen[0] && seen[0] < (blocking ? .9f : .45f))
                    throw new AssertionError("Speed -2 restarted a slow visual swing early: blocking=" + blocking + " " + seen[0] + " -> " + current);
                seen[0] = current; seen[1] = Math.max(seen[1],current);
            });
        }
        if (values.get()[1] < (blocking ? .8f : .45f)) throw new AssertionError("Speed -2 never completed its downswing: blocking=" + blocking);
        System.out.println("SLOW_SWING_MINUS_TWO_OK blocking=" + blocking);
    }

    private static void checkIgnoreHaste(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(client -> ConfigManager.resetAnimations());
        world.getServer().runCommand("effect clear @a");
        world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:air");
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword[minecraft:swing_animation={type:\"whack\",duration:12}]");
        context.waitTicks(10);
        context.getInput().holdKey(options -> options.keyAttack); context.waitTicks(1);
        context.getInput().releaseKey(options -> options.keyAttack); context.waitTicks(15);
        assertDuration(context, false, 12); assertDuration(context, true, 6);
        world.getServer().runCommand("effect give @a minecraft:haste 600 1 true"); context.waitTicks(5);
        assertDuration(context, false, 10); assertDuration(context, true, 6);
        world.getServer().runCommand("effect clear @a minecraft:haste");
        world.getServer().runCommand("effect give @a minecraft:mining_fatigue 600 1 true"); context.waitTicks(5);
        assertDuration(context, false, 16); assertDuration(context, true, 6);
        checkSixTickVisualSwing(context);
        world.getServer().runCommand("effect give @a minecraft:haste 600 1 true"); context.waitTicks(5);
        assertDuration(context, false, 10); assertDuration(context, true, 6);
        checkSixTickVisualSwing(context);
        world.getServer().runCommand("effect clear @a minecraft:mining_fatigue"); context.waitTicks(5);
        context.runOnClient(client -> ConfigManager.animationSwingSpeed = -.5);
        assertDuration(context, true, 9);
        world.getServer().runCommand("effect clear @a");
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
        context.runOnClient(client -> ConfigManager.resetAnimations()); context.waitTicks(5);
        world.getServer().runCommand("effect give @a minecraft:conduit_power 600 1 true"); context.waitTicks(5);
        assertDuration(context, false, 4); assertDuration(context, true, 6);
        world.getServer().runCommand("effect clear @a"); context.waitTicks(5);
        assertDuration(context, false, 6); assertDuration(context, true, 6);
        checkSixTickVisualSwing(context);
        world.getServer().runCommand("effect give @a minecraft:haste 600 9 true"); context.waitTicks(5);
        assertDuration(context, true, 6);
        checkSixTickVisualSwing(context); // Vanilla has zero sampled progress; observing its call still renders a swing.
        checkVanillaSwingPassthrough(context);
        context.runOnClient(client -> ConfigManager.animationsEnabled = false);
        world.getServer().runCommand("effect clear @a");
        world.getServer().runCommand("effect give @a minecraft:mining_fatigue 600 1 true"); context.waitTicks(5);
        assertDuration(context, true, 10);
        world.getServer().runCommand("effect clear @a");
        context.runOnClient(client -> ConfigManager.resetAnimations()); context.waitTicks(5);
        System.out.println("IGNORE_HASTE_CLASSIC_SIX_TICKS_OK");
    }
    private static void checkVanillaSwingPassthrough(ClientGameTestContext context) {
        for (boolean animations : new boolean[]{false, true}) {
            context.runOnClient(client -> {
                ConfigManager.resetAnimations(); ConfigManager.animationsEnabled = animations;
                if (com.customcosmetics.features.animations.ViewModelAnimations.customTiming())
                    throw new AssertionError("Default settings activated a custom visual clock");
            });
            context.getInput().holdKey(options -> options.keyAttack);
            for (int tick = 0; tick < 12; tick++) {
                context.waitTicks(1);
                context.runOnClient(client -> {
                    float vanilla = com.customcosmetics.compatibility.SwingAccess.progress(client.player);
                    if (visualSwing(client) != vanilla)
                        throw new AssertionError("All timing options off changed vanilla speed: " + animations);
                });
            }
            context.getInput().releaseKey(options -> options.keyAttack);
        }
        context.runOnClient(client -> ConfigManager.animationIgnoreHaste = true);
        System.out.println("ALL_ANIMATION_TIMING_OPTIONS_OFF_VANILLA_SPEED_OK");
    }
    private static void checkSixTickVisualSwing(ClientGameTestContext context) {
        context.waitTicks(20);
        context.getInput().holdKey(options -> options.keyAttack); context.waitTicks(1);
        context.getInput().releaseKey(options -> options.keyAttack);
        context.waitFor(client -> visualSwing(client) > 0);
        var previous = new java.util.concurrent.atomic.AtomicReference<Float>();
        context.runOnClient(client -> previous.set(visualSwing(client)));
        if (previous.get() >= .5f) throw new AssertionError("Visual swing began late: " + previous.get());
        boolean finished = false;
        for (int tick = 0; tick < 6; tick++) {
            context.waitTicks(1);
            var current = new java.util.concurrent.atomic.AtomicReference<Float>();
            context.runOnClient(client -> current.set(visualSwing(client)));
            if (current.get() == 0) {
                if (previous.get() < .8f) throw new AssertionError("Visual swing was truncated: " + previous.get());
                finished = true; break;
            }
            if (Math.abs(current.get() - previous.get() - 1f / 6) > .0001f)
                throw new AssertionError("Ignore Haste render clock is not six ticks: " + previous.get() + " -> " + current.get());
            previous.set(current.get());
        }
        if (!finished) throw new AssertionError("Ignore Haste visual swing exceeded six ticks");
        System.out.println("IGNORE_HASTE_RENDER_CYCLE_SIX_TICKS_OK");
    }
    private static float visualSwing(net.minecraft.client.Minecraft client) {
        return com.customcosmetics.features.animations.ViewModelAnimations.swing(
            com.customcosmetics.compatibility.SwingAccess.progress(client.player), 0,
            net.minecraft.world.InteractionHand.MAIN_HAND, client.player.getMainHandItem());
    }
    private static void assertDuration(ClientGameTestContext context, boolean ignore, int expected) {
        context.runOnClient(client -> {
            ConfigManager.animationIgnoreHaste = ignore;
            int actual = com.customcosmetics.features.animations.ViewModelAnimations.swingDuration(client);
            if (actual != expected) throw new AssertionError("Ignore Haste " + ignore + ": expected " + expected + " ticks but got " + actual);
        });
    }

    private static void checkAnimations(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(client -> {
            ConfigManager.animationItemScale = .7; ConfigManager.animationX = .1; ConfigManager.animationY = .1;
            ConfigManager.animationRotationX = 12; ConfigManager.animationRotationY = 15; ConfigManager.animationRotationZ = 10;
            ConfigManager.animationSwingX = .5; ConfigManager.animationSwingY = .5; ConfigManager.animationSwingZ = 0;
            ConfigManager.animationDisableHandMove = true; ConfigManager.animationDisableEquip = true;
            ConfigManager.animationDisableSwing = true; ConfigManager.animationTerminatorOnly = true;
        });
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:bow[minecraft:custom_data={id:\"TERMINATOR\"}]");
        context.waitTicks(10); RenderAssertions.models.set(0);
        context.getInput().holdKey(options -> options.keyAttack); context.waitTicks(8);
        context.runOnClient(client -> {
            if (RenderAssertions.models.get() == 0 || RenderAssertions.swing != 0 || RenderAssertions.equip != 0 || RenderAssertions.movement)
                throw new AssertionError("Configured viewmodel options did not reach the real renderer");
        });
        context.takeScreenshot("animations-terminator");
        context.getInput().releaseKey(options -> options.keyAttack);
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
        context.runOnClient(client -> {
            ConfigManager.animationDisableSwing = false; ConfigManager.animationIgnoreHaste = true;
            ConfigManager.animationSwingSpeed = -.5;
        });
        context.waitTicks(10); context.getInput().holdKey(options -> options.keyUse);
        context.getInput().holdKey(options -> options.keyAttack); context.waitTicks(5);
        context.takeScreenshot("animations-custom-block-swing");
        context.getInput().releaseKey(options -> options.keyAttack); context.getInput().releaseKey(options -> options.keyUse);
        context.runOnClient(client -> {
            ConfigManager.resetAnimations();
            com.customcosmetics.compatibility.ClientUiAccess.setScreen(client,
                new com.customcosmetics.gui.CosmeticsScreen(null, com.customcosmetics.gui.CosmeticsScreen.Page.ANIMATIONS));
        });
        context.waitTicks(3); context.takeScreenshot("animations-settings");
        context.runOnClient(client -> com.customcosmetics.compatibility.ClientUiAccess.setScreen(client, null));
        System.out.println("ANIMATIONS_SCENARIOS_OK");
    }

    private static void waitForWorldRender(TestSingleplayerContext world) {
        try {
            java.lang.reflect.Method accessor;
            try { accessor = TestSingleplayerContext.class.getMethod("getConnection"); }
            catch (NoSuchMethodException olderApi) { accessor = TestSingleplayerContext.class.getMethod("getClientLevel"); }
            accessor.getReturnType().getMethod("waitForChunksRender").invoke(accessor.invoke(world));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot wait for the test world's renderer", error); }
    }

    private static void checkItem(ClientGameTestContext context, TestSingleplayerContext world,
            String item, boolean offhand, BlockHand expected, String screenshot) {
        context.getInput().releaseKey(options -> options.keyUse);
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
        world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:air");
            world.getServer().runCommand("item replace entity @a weapon." + (offhand ? "offhand" : "mainhand") + " with " + item);
        context.waitFor(client -> client.player != null && !(offhand ? client.player.getOffhandItem() : client.player.getMainHandItem()).isEmpty());
        context.waitTicks(10);
        context.runOnClient(client -> com.customcosmetics.compatibility.ClientUiAccess.chat(client).clearMessages(true));
        RenderAssertions.calls.set(0);
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(10);
        context.runOnClient(client -> {
            if (SwordBlocking.localHand() != expected) throw new AssertionError(screenshot + ": wrong blocking hand " + SwordBlocking.localHand());
        });
        context.takeScreenshot("blocking-" + screenshot);
        if (expected != BlockHand.NONE && RenderAssertions.calls.get() == 0)
            throw new AssertionError(screenshot + ": blocking transform was never rendered");
        if (expected == BlockHand.NONE && RenderAssertions.calls.get() != 0)
            throw new AssertionError("Non-sword unexpectedly rendered a blocking pose");
        if (expected != BlockHand.NONE) {
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.takeScreenshot("blocking-" + screenshot + "-third-person");
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(5);
            context.takeScreenshot("blocking-" + screenshot + "-third-person-front");
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            if (screenshot.equals("diamond-main")) {
                // One left click while right click remains held: record attack and recovery.
                context.waitTicks(10);
                context.takeScreenshot("blockhit-cycle-guard");
                float guardY = RenderAssertions.blockingY;
                context.getInput().holdKey(options -> options.keyAttack);
                context.waitTicks(1);
                context.getInput().releaseKey(options -> options.keyAttack);
                float maximum = 0;
                for (int frame = 0; frame < 9; frame++) {
                    context.waitTicks(1);
                    maximum = Math.max(maximum, RenderAssertions.blockingSwing);
                    System.out.println("BLOCKHIT_FRAME " + frame + " swing=" + RenderAssertions.blockingSwing
                        + " equip=" + RenderAssertions.blockingEquip);
                    if (Math.abs(RenderAssertions.blockingY - guardY) >= .35f)
                        throw new AssertionError("Attack recovery lowered the blocking wrist out of its guard");
                    context.takeScreenshot("blockhit-cycle-frame-" + String.format("%02d", frame));
                }
                if (maximum <= .1f) throw new AssertionError("Left click did not animate while right click remained held");
                context.runOnClient(client -> {
                    if (SwordBlocking.localHand() != BlockHand.MAIN || RenderAssertions.blockingSwing != 0)
                        throw new AssertionError("Blockhit did not return to its held guard after one left click");
                });
                System.out.println("BLOCKHIT_ATTACK_AND_RECOVERY_OK");
            }
            context.getInput().holdKey(options -> options.keyAttack);
            if (screenshot.equals("diamond-main")) {
                for (int frame = 0; frame < 12; frame++) {
                    context.waitTicks(1); context.takeScreenshot("pvp-blockhit-frame-" + String.format("%02d", frame));
                }
            }
            context.waitTicks(5);
            context.takeScreenshot("blocking-" + screenshot + "-swing");
            context.getInput().releaseKey(options -> options.keyAttack);
        }
        context.getInput().releaseKey(options -> options.keyUse);
        context.waitTicks(5);
        context.runOnClient(client -> {
            if (SwordBlocking.localHand() != BlockHand.NONE) throw new AssertionError("Pose remained after releasing right click");
        });
        System.out.println("BLOCKING_SCENARIO_OK " + screenshot);
    }
}
