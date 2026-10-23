// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;

import com.customcosmetics.features.items.ItemCosmetics;
import com.customcosmetics.features.items.HeadTexture;
import com.customcosmetics.gametest.RenderAssertions;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ItemOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the resolved ID submitted to the real renderer, after production overrides. */
@Mixin(ItemModelResolver.class)
public class ItemAppearanceProbeMixin {
    @Inject(method="appendItemLayers",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/item/ItemModelResolver;getItemModel(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/item/ItemModel;"))
    private void customcosmetics$observeItemModel(ItemStackRenderState state,ItemStack item,ItemDisplayContext context,
        Level level,ItemOwner owner,int seed,CallbackInfo ci,@Local Identifier model){
        String uuid=ItemCosmetics.uuid(item);
        if(uuid.equals("12345678-1234-1234-1234-123456789abc")){
            RenderAssertions.customizedItemModel=model.toString();RenderAssertions.itemModelOverrides.incrementAndGet();
            var profile=item.get(net.minecraft.core.component.DataComponents.PROFILE);
            if(profile!=null){
                var properties=profile.partialProfile().properties().get("textures");
                if(!properties.isEmpty())RenderAssertions.customizedHeadTexture=HeadTexture.normalize(properties.iterator().next().value());
                RenderAssertions.headContexts.add(context.name());
            }
        }
        if(uuid.equals("12345678-1234-1234-1234-123456789abd"))RenderAssertions.untouchedItemModel=model.toString();
    }
}
