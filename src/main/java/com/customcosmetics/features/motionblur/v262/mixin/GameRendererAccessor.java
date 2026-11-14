// SPDX-License-Identifier: LGPL-3.0-only
package com.customcosmetics.features.motionblur.v262.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("mainRenderTarget")
    RenderTarget naturalMotionBlur$mainRenderTarget();
}
