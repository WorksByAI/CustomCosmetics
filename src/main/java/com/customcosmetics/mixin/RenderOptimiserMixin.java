// SPDX-License-Identifier: BSD-3-Clause
// Adapted from Athen RenderOptimiser and its mixins, Copyright (c) 2025 Starred.
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.gameplay.GameplayModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class RenderOptimiserMixin {
    @Mixin(targets = {"net.minecraft.client.renderer.ItemInHandRenderer", "net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer"})
    public static class Arm {
        @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
        private void csb$arm(CallbackInfo ci) {
            if (ConfigManager.renderHideArm && GameplayModule.RENDER_OPTIMISER.enabled()) ci.cancel();
        }
    }

    @Mixin(targets = {"net.minecraft.client.gui.Gui", "net.minecraft.client.gui.Hud"})
    public static class Hud {
        @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
        private void csb$effects(CallbackInfo ci) {
            if (ConfigManager.renderHideEffects && GameplayModule.RENDER_OPTIMISER.enabled()) ci.cancel();
        }
    }

    @Mixin(EffectsInInventory.class)
    public static class Inventory {
        @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
        private void csb$effects(CallbackInfo ci) {
            if (ConfigManager.renderHideEffects && GameplayModule.RENDER_OPTIMISER.enabled()) ci.cancel();
        }
    }

    @Mixin(ScreenEffectRenderer.class)
    public static class Fire {
        @Inject(method = {"renderFire", "submitFire"}, at = @At("HEAD"), cancellable = true)
        private static void csb$fire(CallbackInfo ci) {
            if (ConfigManager.renderHideFire && GameplayModule.RENDER_OPTIMISER.enabled()) ci.cancel();
        }
    }

    @Mixin(FogRenderer.class)
    public static class Fog {
        @ModifyVariable(method = "getBuffer", at = @At("HEAD"), ordinal = 0, argsOnly = true)
        private FogRenderer.FogMode csb$fog(FogRenderer.FogMode mode) {
            return ConfigManager.renderHideFog && GameplayModule.RENDER_OPTIMISER.enabled() ? FogRenderer.FogMode.NONE : mode;
        }
    }

    @Mixin(LavaFogEnvironment.class)
    public static class Lava {
        @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
        private void csb$lava(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker delta, CallbackInfo ci) {
            if (!ConfigManager.renderHideLava || !GameplayModule.RENDER_OPTIMISER.enabled()) return;
            fog.environmentalStart = Float.MAX_VALUE;
            fog.environmentalEnd = Float.MAX_VALUE;
            ci.cancel();
        }
    }

    @Mixin(EntityRenderer.class)
    public static class EntityState {
        @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("TAIL"))
        private void csb$state(Entity entity, EntityRenderState state, float delta, CallbackInfo ci) {
            if (!GameplayModule.RENDER_OPTIMISER.enabled()) return;
            if (ConfigManager.renderHideEntityFire) state.displayFireAnimation = false;
        }
    }

    @Mixin(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class)
    public static class Armor {
        @Inject(method = "extractEntity", at = @At("RETURN"))
        private void csb$armor(Entity entity, float delta, CallbackInfoReturnable<EntityRenderState> ci) {
            if (!(ci.getReturnValue() instanceof HumanoidRenderState humanoid) || !GameplayModule.RENDER_OPTIMISER.enabled()) return;
            boolean hideArmor = humanoid instanceof AvatarRenderState
                ? ConfigManager.renderHidePlayerArmor && (entity instanceof LocalPlayer ? ConfigManager.renderHideSelfArmor : ConfigManager.renderHideOtherArmor)
                : ConfigManager.renderHideMobArmor;
            if (!hideArmor) return;
            humanoid.headEquipment = humanoid.chestEquipment = humanoid.legsEquipment = humanoid.feetEquipment = ItemStack.EMPTY;
            humanoid.wornHeadType = null;
        }
    }

    @Mixin(Entity.class)
    public static class Glow {
        @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
        private void csb$glow(CallbackInfoReturnable<Boolean> ci) {
            if (ConfigManager.renderHideGlow && GameplayModule.RENDER_OPTIMISER.enabled()) ci.setReturnValue(false);
        }
    }
}
