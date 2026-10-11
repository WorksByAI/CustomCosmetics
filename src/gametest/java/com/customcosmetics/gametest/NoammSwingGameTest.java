/*
 * Reference duration calculation from Noamm9/NoammAddons (CC0-1.0),
 * MixinLivingEntity at 44e447abbe2f3a1c0678c6388abd3a3b0b2287d8.
 * Adapted 2026-10-11 only as an isolated game-test oracle. See NOTICE.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.customcosmetics.gametest;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.ViewModelAnimations;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;

/** Compare real held-button animation at five partial ticks against native Minecraft with NA's hook. */
public final class NoammSwingGameTest {
    private static NoammSwingReferencePlayer reference;
    private static long comparisons, requests, renderedComparisons;
    private static boolean sampling;

    public static void observe(Object player, InteractionHand hand) {
        var client = Minecraft.getInstance();
        if (reference == null || player != client.player) return;
        reference.referenceDuration = upstreamDuration(client);
        reference.swing(hand, false); // Test-only reference is never spawned or connected to a game server.
        requests++;
    }
    public static void advance(Object player) {
        var client = Minecraft.getInstance();
        if (reference == null || player != client.player) return;
        reference.referenceDuration = upstreamDuration(client);
        reference.step();
    }
    public static void compare(Object player) {
        var client = Minecraft.getInstance();
        if (reference == null || player != client.player) return;
        for (float partial : new float[]{0, .25f, .5f, .75f, 1}) {
            float expected = reference.getAttackAnim(partial);
            float actual;
            sampling = true;
            try { actual = ViewModelAnimations.swing(client.player.getAttackAnim(partial), partial,
                InteractionHand.MAIN_HAND, client.player.getMainHandItem()); }
            finally { sampling = false; }
            if (Math.abs(actual - expected) > .00001f)
                throw new AssertionError("NA/native swing mismatch: duration=" + reference.referenceDuration
                    + " partial=" + partial + " expected=" + expected + " actual=" + actual);
            comparisons++;
        }
    }
    public static void compareRendered(float actual, float partial, InteractionHand hand) {
        if (reference == null || sampling || hand != InteractionHand.MAIN_HAND) return;
        float expected = reference.getAttackAnim(partial);
        if (Math.abs(actual - expected) > .00001f)
            throw new AssertionError("Rendered NA swing mismatch: partial=" + partial + " expected=" + expected + " actual=" + actual);
        renderedComparisons++;
    }
    // Kept independent of the production helper: this is the upstream calculation being used as the oracle.
    private static int upstreamDuration(Minecraft client) {
        int length;
        if (ConfigManager.animationIgnoreHaste) length = 6;
        else if (client.player.hasEffect(MobEffects.HASTE)) length = 6 - (1 + client.player.getEffect(MobEffects.HASTE).getAmplifier());
        else if (client.player.hasEffect(MobEffects.MINING_FATIGUE)) length = 6 + (1 + client.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) * 2;
        else length = 6;
        return Math.max((int)(length * Math.exp(-ConfigManager.animationSwingSpeed)), 1);
    }

    public static void check(ClientGameTestContext context, TestSingleplayerContext world) {
        record Case(String name, String effect, boolean ignore, double speed) {}
        for (var scenario : new Case[]{new Case("normal", "", true, 0),
                new Case("haste-two-ignored", "haste 600 1 true", true, 0),
                new Case("haste-ten-ignored", "haste 600 9 true", true, 0),
                new Case("fatigue-ignored", "mining_fatigue 600 1 true", true, 0),
                new Case("slow", "", true, -.5), new Case("slowest", "", true, -2),
                new Case("fast", "", true, 1), new Case("high-haste-clamp", "haste 600 9 true", false, -.5)}) {
            context.runOnClient(client -> ConfigManager.resetAnimations()); context.waitTicks(2);
            world.getServer().runCommand("effect clear @a");
            if (!scenario.effect().isEmpty()) world.getServer().runCommand("effect give @a minecraft:" + scenario.effect());
            context.waitTicks(4);
            context.runOnClient(client -> {
                ConfigManager.animationIgnoreHaste = scenario.ignore(); ConfigManager.animationSwingSpeed = scenario.speed();
                reference = new NoammSwingReferencePlayer(client.level); comparisons = requests = renderedComparisons = 0;
            });
            context.getInput().holdKey(options -> options.keyAttack); context.waitTicks(30);
            context.getInput().releaseKey(options -> options.keyAttack); context.waitTicks(50);
            context.runOnClient(client -> {
                if (comparisons < 200 || requests < 20 || renderedComparisons < 20)
                    throw new AssertionError("Insufficient input/partial-tick/render coverage: " + comparisons + "/" + requests + "/" + renderedComparisons);
                System.out.println("NA_NATIVE_SWING_MATCH_OK " + scenario.name() + " samples=" + comparisons + " requests=" + requests + " rendered=" + renderedComparisons);
                reference = null;
            });
        }
        world.getServer().runCommand("effect clear @a"); context.runOnClient(client -> ConfigManager.resetAnimations());
        context.waitTicks(3);
        System.out.println("NOAMM_SWING_REAL_INPUT_AND_INTERPOLATION_OK");
    }
}
