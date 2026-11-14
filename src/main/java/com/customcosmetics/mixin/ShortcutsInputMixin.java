// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.shortcuts.Shortcuts;
import com.customcosmetics.features.gameplay.GameplayKeybinds;
import com.customcosmetics.features.gameplay.AutoClicker;
import com.customcosmetics.features.gameplay.TooltipTweaks;
import com.customcosmetics.config.ConfigManager;
import com.mojang.blaze3d.platform.InputConstants;
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
            if (ConfigManager.tooltipShowHex) TooltipTweaks.keyEvent(window, action, InputConstants.getKey(event));
            if (Shortcuts.keyboard(window, action, event)) callback.cancel();
            else if (GameplayKeybinds.acceptsInput(window, action)) GameplayKeybinds.press(InputConstants.getKey(event));
        }
    }
    @Mixin(MouseHandler.class)
    public abstract static class Mouse {
        @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
        private void customcosmetics$shortcut(long window, MouseButtonInfo event, int action, CallbackInfo callback) {
            if (ConfigManager.tooltipShowHex) TooltipTweaks.keyEvent(window, action, InputConstants.Type.MOUSE.getOrCreate(event.button()));
            if (Shortcuts.mouse(window, event, action)) callback.cancel();
            else {
                if (GameplayKeybinds.acceptsInput(window, action))
                    GameplayKeybinds.press(InputConstants.Type.MOUSE.getOrCreate(event.button()));
                if (AutoClicker.mouse(window, event.button(), action)) callback.cancel();
            }
        }
    }
}
