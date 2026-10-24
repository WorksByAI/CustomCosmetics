// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;

import com.customcosmetics.gametest.PerformanceComparisonGameTest;
import com.customcosmetics.features.damage.DamageSplash;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class PerformanceComparisonProbe {
    @Mixin(Minecraft.class)
    public static class Frames {
        @Inject(method="runTick",at=@At("HEAD"))
        private void tick(boolean renderLevel,CallbackInfo callback){PerformanceComparisonGameTest.tick(renderLevel);}
        @Inject(method="renderFrame",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
        private void renderedFrame(boolean renderLevel,CallbackInfo callback){if(renderLevel)PerformanceComparisonGameTest.frame();}
    }
    @Mixin(DamageSplash.class)
    public static class Skyblock {
        @ModifyReturnValue(method="isSkyblock",at=@At("RETURN"))
        private static boolean context(boolean original){return PerformanceComparisonGameTest.simulatedSkyblock||original;}
    }
}
