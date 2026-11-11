// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;
import com.customcosmetics.gui.CapePreview;
import com.customcosmetics.gametest.CapeGameTest;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapePreview.class)
public class CapePreviewProbeMixin {
    @Inject(method="prepareState(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/customcosmetics/gui/PreviewStyle;)V",at=@At("TAIL"))
    private static void preview(AvatarRenderState state, @Coerce Object style, CallbackInfo ci) {
        if ("CAPE".equals(((Enum<?>) style).name())) CapeGameTest.observePreview(state);
    }
}
