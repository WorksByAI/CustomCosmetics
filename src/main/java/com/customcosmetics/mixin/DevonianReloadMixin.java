// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Let a resource reload finish before Devonian changes the world-loading overlay. */
public final class DevonianReloadMixin {
    @Pseudo
    @Mixin(targets = "com.github.synnerz.devonian.features.misc.hiders.DisableWorldLoadingScreen", remap = false)
    public static class WorldLoading {
        @WrapWithCondition(method = "initialize$lambda$0", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setOverlay(Lnet/minecraft/client/gui/screens/Overlay;)V"))
        private static boolean ccos$keepReloadDuringScreenChange(Gui gui, Overlay next) {
            return !(gui.overlay() instanceof LoadingOverlay);
        }

        @WrapWithCondition(method = "onPlayerLoaded", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setOverlay(Lnet/minecraft/client/gui/screens/Overlay;)V"))
        private boolean ccos$keepReloadWhenPlayerLoads(Gui gui, Overlay next) {
            return !(gui.overlay() instanceof LoadingOverlay);
        }
    }

    @Pseudo
    @Mixin(targets = "com.github.synnerz.devonian.features.misc.hiders.DisableWorldLoadingScreen$PausingOverlay", remap = false)
    public static class Pausing {
        @WrapWithCondition(method = "tick", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setOverlay(Lnet/minecraft/client/gui/screens/Overlay;)V"))
        private boolean ccos$keepReloadDuringPause(Gui gui, Overlay next) {
            return !(gui.overlay() instanceof LoadingOverlay);
        }
    }
}
