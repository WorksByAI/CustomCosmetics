// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLMouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class MouseFocus263Mixin {
    @Inject(method = "onFocus", at = @At("TAIL"))
    private void csb$restoreRelativeMouseMode(boolean focused, CallbackInfo callback) {
        if (!focused) return;
        var client = Minecraft.getInstance();
        if (client == null || client.level == null || ClientUiAccess.screen(client) != null || !client.mouseHandler.isMouseGrabbed()) return;
        long window = ((Window) (Object) this).handle();
        // Minecraft's grab flag survives a lost SDL capture. Restore the backend without moving the pointer.
        if (!SDLMouse.SDL_GetWindowRelativeMouseMode(window)) SDLMouse.SDL_SetWindowRelativeMouseMode(window, true);
    }
}
