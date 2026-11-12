// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.gui.UiBlurPolicy;
import com.customcosmetics.config.ConfigManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
abstract class InventoryBlurMixin {
    @Shadow protected abstract void extractBlurredBackground(GuiGraphicsExtractor graphics);

    @Inject(method = "extractTransparentBackground", at = @At("HEAD"))
    private void ccos$blurContainer(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!UiBlurPolicy.inventory((Screen) (Object) this)) return;
        if (((UiBlurPolicy.Graphics) graphics).ccos$canBlur()) extractBlurredBackground(graphics);
    }

    @WrapOperation(method = "extractTransparentBackground",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fillGradient(IIIIII)V"))
    private void ccos$inventoryDarkness(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom,
                                         int first, int last, Operation<Void> original) {
        int darkness = ConfigManager.inventoryDimPercent;
        if (darkness != 80 && UiBlurPolicy.inventoryDarkness((Screen) (Object) this)) {
            if (darkness == 0) return;
            first = UiBlurPolicy.inventoryDimColor(first, darkness);
            last = UiBlurPolicy.inventoryDimColor(last, darkness);
        }
        original.call(graphics, left, top, right, bottom, first, last);
    }

    @Mixin(GuiGraphicsExtractor.class)
    public abstract static class Graphics implements UiBlurPolicy.Graphics {
        @Shadow @Final private GuiRenderState guiRenderState;
        @Override public boolean ccos$canBlur() { return ((UiBlurPolicy.State) (Object) guiRenderState).ccos$canBlur(); }
    }

    @Mixin(GuiRenderState.class)
    public abstract static class BlurState implements UiBlurPolicy.State {
        @Shadow private int firstStratumAfterBlur;
        @Override public boolean ccos$canBlur() { return firstStratumAfterBlur == Integer.MAX_VALUE; }
    }
}
