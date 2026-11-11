// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.gui.UiBlurPolicy;
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
