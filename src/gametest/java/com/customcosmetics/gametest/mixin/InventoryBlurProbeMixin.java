// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;

import com.customcosmetics.gametest.Release201GameTest;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
abstract class InventoryBlurProbeMixin {
    @Inject(method = "blurBeforeThisStratum", at = @At("HEAD"))
    private void ccos$observeBlur(CallbackInfo ci) {
        if (Release201GameTest.trackInventoryBlur) Release201GameTest.inventoryBlurPasses++;
    }
}
