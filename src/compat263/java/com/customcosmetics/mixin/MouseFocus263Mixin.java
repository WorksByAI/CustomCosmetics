// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.compatibility.MouseFocusRecovery;
import com.customcosmetics.compatibility.SdlCursor;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class MouseFocus263Mixin {
    @Inject(method = "onFocus", at = @At("TAIL"))
    private void csb$restoreRelativeMouseMode(boolean focused, CallbackInfo callback) {
        MouseFocusRecovery.onFocus(focused, SdlCursor.INSTANCE);
    }
}
