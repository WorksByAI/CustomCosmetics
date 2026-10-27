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
}
