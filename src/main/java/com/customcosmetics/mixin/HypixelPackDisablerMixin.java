// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.pack.HypixelPackDisabler;
import com.customcosmetics.features.pack.LegacyPackItems;
import com.customcosmetics.features.pack.PackDisablerCommands;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackDownloader;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HypixelPackDisablerMixin {
    @Mixin(DownloadedPackSource.class)
    public interface DownloadSource extends HypixelPackDisabler.DownloadedPacks {
        @Override @Accessor("manager") ServerPackManager customcosmetics$packManager();
    }

    @Mixin(ServerPackManager.class)
    public interface Manager extends HypixelPackDisabler.DownloadWorker {
        @Override @Accessor("downloader") PackDownloader customcosmetics$packDownloader();
    }

    @Mixin(DownloadedPackSource.class)
    public static class Downloads {
        @Inject(method = "pushPack", at = @At("HEAD"))
        private void ccos$identify(UUID id, URL url, String hash, CallbackInfo ci) {
            HypixelPackDisabler.remember(id, url, hash);
        }

        @Inject(method = "cleanupAfterDisconnect", at = @At("HEAD"))
        private void ccos$disconnect(CallbackInfo ci) {
            HypixelPackDisabler.disconnect();
        }
    }

    @Mixin(PackRepository.class)
    public static class Repository {
        @ModifyReturnValue(method = "openAllSelected", at = @At("RETURN"))
        private List<PackResources> ccos$deprioritizeOfficialTextures(List<PackResources> packs) {
            return HypixelPackDisabler.prioritize(packs);
        }
    }

    @Mixin(FallbackResourceManager.class)
    public abstract static class Resources {
        @Shadow public abstract List<Resource> getResourceStack(Identifier location);

        @Inject(method = "listResources", at = @At("RETURN"), cancellable = true)
        private void ccos$chooseTextShaders(CallbackInfoReturnable<Map<Identifier, Resource>> callback) {
            var selected = callback.getReturnValue();
            var result = HypixelPackDisabler.textShaders(selected, this::getResourceStack);
            if (selected != result) callback.setReturnValue(result);
        }
    }

    @Mixin(CustomData.class)
    public interface ItemData extends LegacyPackItems.CustomDataView {
        @Override @Accessor("tag") CompoundTag customcosmetics$readOnlyTag();
    }

    @Mixin(PlayerHeadSpecialRenderer.class)
    public static class Heads {
        @ModifyExpressionValue(
            method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
        private Object ccos$legacyHeadProfile(Object original, @Local(argsOnly = true) ItemStack stack) {
            return LegacyPackItems.headProfile(stack, (ResolvableProfile) original);
        }
    }

    @Mixin(AbstractContainerScreen.class)
    public static class InventoryKeys {
        @Shadow protected Slot hoveredSlot;

        @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
        private void ccos$handlePackKey(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
            if (hoveredSlot != null && hoveredSlot.hasItem() && PackDisablerCommands.handleKey(event, hoveredSlot.getItem())) {
                callback.setReturnValue(true);
            }
        }
    }
}
