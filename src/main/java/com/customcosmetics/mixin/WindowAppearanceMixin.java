// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.window.WindowAppearance;
import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.List;

public final class WindowAppearanceMixin {
    @Mixin(Window.class)
    public static class Title {
        @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
        private String customSkyBlock$windowTitle(String original) {
            String title = ConfigManager.windowTitle;
            return title.isEmpty() ? original : title;
        }
    }

    @Mixin(IconSet.class)
    public static class Icons {
        @Inject(method = "getStandardIcons", at = @At("HEAD"), cancellable = true)
        private void customSkyBlock$standard(CallbackInfoReturnable<List<IoSupplier<InputStream>>> result) {
            var icons = WindowAppearance.standardIcons();
            if (icons != null) result.setReturnValue(icons);
        }

        @Inject(method = "getMacIcon", at = @At("HEAD"), cancellable = true, require = 0)
        private void customSkyBlock$mac(CallbackInfoReturnable<IoSupplier<InputStream>> result) {
            var icon = WindowAppearance.macIcon();
            if (icon != null) result.setReturnValue(icon);
        }
    }
}
