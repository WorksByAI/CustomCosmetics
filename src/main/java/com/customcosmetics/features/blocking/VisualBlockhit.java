// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.blocking;

import com.customcosmetics.features.animations.ViewModelAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;

/** Reads input and the crosshair target; only supplies progress to the item renderer. */
public final class VisualBlockhit {
    private static BlockhitClock clock = new BlockhitClock();
    private static Object world;
    private VisualBlockhit() {}

    public static void tick(Minecraft client) {
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ANIMATIONS))return;
        if (world != client.level) { world = client.level; clock = new BlockhitClock(); }
        if (client.isPaused()) return;
        boolean held=hand(client)!=BlockHand.NONE;
        clock.tick(held,held?ViewModelAnimations.swingDuration(client):1);
    }
    private static BlockHand hand(Minecraft client) {
        if(client==null||!client.options.keyAttack.isDown()||client.hitResult==null||client.hitResult.getType()!=HitResult.Type.BLOCK)return BlockHand.NONE;
        return SwordBlocking.localHand();
    }
    public static boolean matches(InteractionHand hand) {
        var client = Minecraft.getInstance();
        var blocking=hand(client);
        return blocking!=BlockHand.NONE&&(blocking==BlockHand.MAIN)==(hand==InteractionHand.MAIN_HAND);
    }
    public static float progress(float partial) { return clock.progress(partial); }
}
