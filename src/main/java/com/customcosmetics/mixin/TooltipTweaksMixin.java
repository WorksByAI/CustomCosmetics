// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.gameplay.TooltipTweaks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

public final class TooltipTweaksMixin {
    @Mixin(ItemEnchantments.class)
    public static class VanillaEnchants {
        @ModifyVariable(method = "addToTooltip", at = @At("HEAD"), argsOnly = true)
        private Consumer<Component> csb$filterVanillaEnchants(Consumer<Component> lines, Item.TooltipContext context,
                                                             Consumer<Component> original, TooltipFlag flag, DataComponentGetter components) {
            return TooltipTweaks.vanillaEnchantTooltip(components, (ItemEnchantments) (Object) this, lines);
        }
    }

    @Mixin(GuiGraphicsExtractor.class)
    public static class Decorations {
        @Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
        private void csb$stars(Font font, ItemStack stack, int x, int y, String countText, CallbackInfo callback) {
            TooltipTweaks.stars((GuiGraphicsExtractor) (Object) this, font, stack, x, y);
        }
    }

    @Mixin(AbstractContainerScreen.class)
    public static class CakeYears {
        @Inject(method = "extractSlot", at = @At("RETURN"))
        private void csb$cakeYear(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo callback) {
            TooltipTweaks.cake(graphics, slot.getItem(), slot.x, slot.y);
        }
    }
}
