// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.window.WindowAppearance;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.server.packs.PackMetadataResources;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.io.IOException;

@Mixin(Window.class)
public class WindowIconStartup263Mixin {
    @Unique private boolean csb$showAfterFirstIcon;

    @ModifyArg(method = "createWindow", index = 3, at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"))
    private long csb$holdWindowUntilIcon(long flags) {
        csb$showAfterFirstIcon = (flags & SDLVideo.SDL_WINDOW_HIDDEN) == 0
            && Platform.get() == Platform.WINDOWS && WindowAppearance.hasCustomIcon();
        return csb$showAfterFirstIcon ? flags | SDLVideo.SDL_WINDOW_HIDDEN : flags;
    }

    @WrapMethod(method = "setIcon(Lnet/minecraft/server/packs/PackMetadataResources;Lcom/mojang/blaze3d/platform/IconSet;)V")
    private void csb$showAfterFirstIcon(PackMetadataResources pack, IconSet iconSet, Operation<Void> original) throws IOException {
        try {
            original.call(pack, iconSet);
        } finally {
            if (csb$showAfterFirstIcon) {
                csb$showAfterFirstIcon = false;
                SDLVideo.SDL_ShowWindow(((Window) (Object) this).handle());
            }
        }
    }
}
