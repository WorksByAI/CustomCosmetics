// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;
import com.customcosmetics.gui.BlockingPreview;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Restore the GUI projection after its complete item draw, including collected render features. */
@Mixin(PictureInPictureRenderer.class)
public abstract class PreviewProjectionMixin {
    @Inject(method="prepare",at=@At("RETURN"))
    private void customcosmetics$restoreProjection(CallbackInfo callback){BlockingPreview.restoreProjection();}
}
