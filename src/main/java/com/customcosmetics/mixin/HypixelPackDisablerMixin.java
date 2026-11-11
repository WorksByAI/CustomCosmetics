// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.pack.HypixelPackDisabler;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URL;
import java.util.List;
import java.util.UUID;

public final class HypixelPackDisablerMixin {
    @Mixin(DownloadedPackSource.class)
    public static class Downloads {
        @Inject(method = "pushPack", at = @At("HEAD"))
        private void ccos$identify(UUID id, URL url, String hash, CallbackInfo ci) {
            HypixelPackDisabler.remember(id, url);
        }

        @Inject(method = "cleanupAfterDisconnect", at = @At("HEAD"))
        private void ccos$disconnect(CallbackInfo ci) {
            HypixelPackDisabler.disconnect();
        }
    }

    @Mixin(PackRepository.class)
    public static class Repository {
        @ModifyReturnValue(method = "openAllSelected", at = @At("RETURN"))
        private List<PackResources> ccos$hideOfficialTextures(List<PackResources> packs) {
            return HypixelPackDisabler.filter(packs);
        }
    }
}
