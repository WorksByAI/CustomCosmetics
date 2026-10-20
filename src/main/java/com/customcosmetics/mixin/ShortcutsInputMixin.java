// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.shortcuts.Shortcuts;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class ShortcutsInputMixin {
    @Mixin(KeyboardHandler.class)
    public abstract static class Keyboard {
        @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
        private void customcosmetics$shortcut(long window, int action, KeyEvent event, CallbackInfo callback) {
            if (Shortcuts.keyboard(window, action, event)) callback.cancel();
        }
    }
    @Mixin(MouseHandler.class)
    public abstract static class Mouse {
        @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
        private void customcosmetics$shortcut(long window, MouseButtonInfo event, int action, CallbackInfo callback) {
            if (Shortcuts.mouse(window, event, action)) callback.cancel();
        }
    }
}
