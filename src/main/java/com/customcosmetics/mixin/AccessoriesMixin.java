// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.accessories.*;
import com.customcosmetics.sync.PresenceSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class AccessoriesMixin {
    @Mixin(AvatarRenderState.class)
    public static class State implements AccessoryRenderState {
        @Unique private AccessoriesSettings customcosmetics$accessories=AccessoriesSettings.NONE;
        @Unique private boolean customcosmetics$hideAccessories;
        public AccessoriesSettings customcosmetics$accessories(){return customcosmetics$accessories;}
        public void customcosmetics$accessories(AccessoriesSettings value){customcosmetics$accessories=value;}
        public boolean customcosmetics$hideAccessories(){return customcosmetics$hideAccessories;}
        public void customcosmetics$hideAccessories(boolean value){customcosmetics$hideAccessories=value;}
    }
    @Mixin(EntityRenderer.class)
    public static class Snapshot {
        @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",at=@At("TAIL"))
        private void customcosmetics$extractAccessories(Entity entity,EntityRenderState state,float partial,CallbackInfo ci){
            if(!(state instanceof AccessoryRenderState target))return;
            var player=Minecraft.getInstance().player;var uuid=entity.getUUID();
            target.customcosmetics$hideAccessories(false);
            target.customcosmetics$accessories(AccessoriesSettings.choose(player==null?null:player.getUUID(),uuid,ConfigManager.offlineMode,ConfigManager.accessories,PresenceSync.getRemoteAccessories(uuid)));
        }
    }
    @Mixin(AvatarRenderer.class)
    public static abstract class Renderer extends LivingEntityRenderer<net.minecraft.client.player.AbstractClientPlayer,AvatarRenderState,PlayerModel> {
        protected Renderer(){super(null,null,0);}
        @Inject(method="<init>",at=@At("TAIL"))
        private void customcosmetics$addAccessories(CallbackInfo ci){addLayer(new AccessoryLayer(this));}
    }
    @Mixin(LivingEntityRenderer.class)
    public static class Bounds {
        @Group(name="customcosmeticsAccessoryBounds",min=1)
        @Inject(method="getBoundingBoxForCulling(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/phys/AABB;",at=@At("RETURN"),cancellable=true,require=0)
        private void customcosmetics$cosmeticBounds(net.minecraft.world.entity.LivingEntity entity,CallbackInfoReturnable<net.minecraft.world.phys.AABB> ci){
            customcosmetics$expandCosmeticBounds(entity,ci);
        }
        @Group(name="customcosmeticsAccessoryBounds",min=1)
        @Inject(method="getBoundingBoxForCulling(Lnet/minecraft/world/entity/LivingEntity;F)Lnet/minecraft/world/phys/AABB;",at=@At("RETURN"),cancellable=true,require=0)
        private void customcosmetics$cosmeticBoundsModern(net.minecraft.world.entity.LivingEntity entity,float partial,CallbackInfoReturnable<net.minecraft.world.phys.AABB> ci){
            customcosmetics$expandCosmeticBounds(entity,ci);
        }
        @Unique private static void customcosmetics$expandCosmeticBounds(net.minecraft.world.entity.LivingEntity entity,CallbackInfoReturnable<net.minecraft.world.phys.AABB> ci){
            if(!(entity instanceof net.minecraft.world.entity.player.Player)||entity.isInvisible())return;
            var self=Minecraft.getInstance().player;var settings=AccessoriesSettings.choose(self==null?null:self.getUUID(),entity.getUUID(),ConfigManager.offlineMode,ConfigManager.accessories,PresenceSync.getRemoteAccessories(entity.getUUID()));
            if(!settings.any())return;double size=1;for(var slot:AccessoryCatalog.slots())if(settings.part(slot.id()).enabled())size=Math.max(size,settings.part(slot.id()).scale());
            var proportions=com.customcosmetics.features.playersize.PlayerSize.settings(entity.getId());double radius=Math.min(8,2*size*Math.max(1,Math.max(proportions.x(),Math.max(proportions.y(),proportions.z()))));
            // This is only the renderer's temporary frustum box, never the entity's hitbox.
            ci.setReturnValue(ci.getReturnValue().inflate(radius));
        }
    }
}
