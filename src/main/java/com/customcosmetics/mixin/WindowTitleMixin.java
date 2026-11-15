// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Window.class)
abstract class WindowTitleMixin {
    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
    private String customSkyBlock$windowTitle(String original) {
        String title = ConfigManager.windowTitle;
        return title.isEmpty() ? original : title;
    }
}
