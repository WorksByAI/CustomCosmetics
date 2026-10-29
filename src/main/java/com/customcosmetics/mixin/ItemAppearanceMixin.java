// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.items.ItemCosmetics;
import com.customcosmetics.features.items.HeadSkins;
import com.customcosmetics.features.items.ItemModelVisibility.RenderCall;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ItemOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

public final class ItemAppearanceMixin {
    @Mixin(net.minecraft.core.component.PatchedDataComponentMap.class)
    public static class Components implements com.customcosmetics.features.items.ItemComponentIdentity {
        @org.spongepowered.asm.mixin.Shadow private it.unimi.dsi.fastutil.objects.Reference2ObjectMap<?,?> patch;
        @Override public Object customcosmetics$componentIdentity() { return patch; }
    }
    @Mixin(ItemStack.class)
    public static class Names {
        @ModifyReturnValue(method = "getHoverName", at = @At("RETURN"))
        private Component customcosmetics$itemName(Component original) {
            return ItemCosmetics.name((ItemStack) (Object) this, original);
        }
        @ModifyReturnValue(method = "hasFoil", at = @At("RETURN"))
        private boolean customcosmetics$itemGlint(boolean original) {
            return ItemCosmetics.foil((ItemStack) (Object) this, original);
        }
    }
    @Mixin(ItemModelResolver.class)
    public static class Models {
        @org.spongepowered.asm.mixin.Unique private static final ThreadLocal<java.util.Deque<RenderCall>> customcosmetics$calls = ThreadLocal.withInitial(java.util.ArrayDeque::new);
        @WrapMethod(method = "appendItemLayers")
        private void customcosmetics$trackModelFallback(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context,
            Level level, ItemOwner owner, int seed, Operation<Void> original) {
            if(!ItemCosmetics.modelOverrideActive(stack)){original.call(state,stack,context,level,owner,seed);return;}
            stack = HeadSkins.renderProxy(stack);
            var calls = customcosmetics$calls.get(); calls.push(new RenderCall(state, stack));
            try { original.call(state, stack, context, level, owner, seed); }
            finally { calls.pop(); }
        }
        @ModifyExpressionValue(method = "appendItemLayers", order = 1100, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
        private Object customcosmetics$itemModel(Object original, @Local(argsOnly = true) ItemStack stack,
            @Local(argsOnly = true) ItemStackRenderState state) {
            if(!ItemCosmetics.modelsActive())return original;
            var calls=customcosmetics$calls.get();
            if(calls.isEmpty()||calls.peek().stack()!=stack)return original;
            // A pack's fallback for this same stack/state must reach the original model, not reselect our override.
            int depth = 0;
            for (var call : calls) if (call.state() == state && call.stack() == stack && ++depth > 1) return original;
            return ItemCosmetics.model(stack, (Identifier) original);
        }
    }
}
