// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;
import com.customcosmetics.gui.CosmeticsScreen;
import com.customcosmetics.gametest.UiPerformanceGameTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CosmeticsScreen.class)
public class UiRenderProbeMixin {
    @Inject(method="extractRenderState",at=@At("HEAD")) private void begin(CallbackInfo ci){UiPerformanceGameTest.begin();}
    @Inject(method="extractRenderState",at=@At("RETURN")) private void end(CallbackInfo ci){UiPerformanceGameTest.end();}
    @Mixin(net.minecraft.client.Minecraft.class)
    public static class Frames {
        @Inject(method="renderFrame",at={@At(value="INVOKE",target="Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"),@At(value="INVOKE",target="Lnet/minecraft/client/renderer/GameRenderer;render()V")},require=1,expect=1)
        private void frame(boolean renderLevel,CallbackInfo callback){if(renderLevel)UiPerformanceGameTest.frame();}
    }
}
