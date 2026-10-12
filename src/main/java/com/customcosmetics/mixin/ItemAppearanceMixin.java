// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.items.ItemCosmetics;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

public final class ItemAppearanceMixin {
    @Mixin(ItemStack.class)
    public static class Names {
        @ModifyReturnValue(method = "getHoverName", at = @At("RETURN"))
        private Component customcosmetics$itemName(Component original) {
            return ItemCosmetics.name((ItemStack) (Object) this, original);
        }
    }
    @Mixin(ItemModelResolver.class)
    public static class Models {
        @ModifyExpressionValue(method = "appendItemLayers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
        private Object customcosmetics$itemModel(Object original, @Local(argsOnly = true) ItemStack stack) {
            return ItemCosmetics.model(stack, (Identifier) original);
        }
    }
}
