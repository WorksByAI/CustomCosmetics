// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.pack.CatharsisPackSupport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.SpatialAttributeInterpolator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class CatharsisVisualMixin {
    @Mixin(EnvironmentAttributeSystem.class)
    public abstract static class Environment {
        @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
        private void ccos$visualOverride(EnvironmentAttribute<?> attribute, Vec3 position,
                                         SpatialAttributeInterpolator interpolator, CallbackInfoReturnable<Object> callback) {
            var value = CatharsisPackSupport.environment(attribute);
            if (value == null) return;
            var level = Minecraft.getInstance().level;
            if (level != null && level.environmentAttributes() == (Object) this) callback.setReturnValue(value);
        }
    }

    @Mixin(GuiGraphicsExtractor.class)
    public abstract static class Graphics {
        @WrapOperation(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
        private Object ccos$tooltipStyle(ItemStack stack, DataComponentType<?> type, Operation<Object> original) {
            var style = type == DataComponents.TOOLTIP_STYLE ? CatharsisPackSupport.tooltip(stack) : null;
            return style != null ? style : original.call(stack, type);
        }
    }

    @Mixin(AbstractContainerScreen.class)
    public abstract static class Container {
        @WrapOperation(method = "extractTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
        private Object ccos$tooltipStyle(ItemStack stack, DataComponentType<?> type, Operation<Object> original) {
            var style = type == DataComponents.TOOLTIP_STYLE ? CatharsisPackSupport.tooltip(stack) : null;
            return style != null ? style : original.call(stack, type);
        }
    }
}
