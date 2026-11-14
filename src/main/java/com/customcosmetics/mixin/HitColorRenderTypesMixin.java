// SPDX-License-Identifier: MIT
// Adapted from HitColor X, Copyright (c) 2026 Contt.
package com.customcosmetics.mixin;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderTypes.class)
public final class HitColorRenderTypesMixin {
    @ModifyReceiver(method = {"lambda$static$10", "lambda$static$11"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/rendertype/RenderSetup$RenderSetupBuilder;createRenderSetup()Lnet/minecraft/client/renderer/rendertype/RenderSetup;"),
        require = 0)
    private static RenderSetup.RenderSetupBuilder legacyItems(RenderSetup.RenderSetupBuilder builder) { return builder.useOverlay(); }
}
