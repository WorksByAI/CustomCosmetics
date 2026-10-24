// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;
import com.customcosmetics.features.animations.PresenceWorld;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Hypixel scope fixture only; no network packets or production bypass are installed. */
@Mixin(PresenceWorld.class)
public class RankWorldProbe {
    @org.spongepowered.asm.mixin.Unique private static boolean customcosmetics$rankTestWorld;
    @ModifyReturnValue(method="isHypixel",at=@At("RETURN"))
    private static boolean rankTestWorld(boolean original){return customcosmetics$rankTestWorld||original;}
}
