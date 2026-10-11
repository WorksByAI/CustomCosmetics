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
        if (world != client.level) { world = client.level; clock = new BlockhitClock(); }
        if (client.isPaused()) return;
        clock.tick(active(client), ViewModelAnimations.swingDuration(client));
    }
    private static boolean active(Minecraft client) {
        return client != null && SwordBlocking.localHand() != BlockHand.NONE && client.options.keyAttack.isDown()
            && client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK;
    }
    public static boolean matches(InteractionHand hand) {
        var client = Minecraft.getInstance();
        return active(client) && (SwordBlocking.localHand() == BlockHand.MAIN) == (hand == InteractionHand.MAIN_HAND);
    }
    public static float progress(float partial) { return clock.progress(partial); }
}
