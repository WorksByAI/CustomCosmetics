// SPDX-License-Identifier: AGPL-3.0-or-later
// Based on NoammAddons Tweaks.transformHead and MixinItemStackRenderState (CC0-1.0).
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.FeatureModules;
import com.customcosmetics.features.animations.ViewModelAnimations;
import com.customcosmetics.features.items.ItemModelVisibility;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemStackRenderState.class)
public abstract class LegacyHeldSkullMixin {
    @Shadow ItemDisplayContext displayContext;

    @WrapMethod(method = "submit")
    private void csb$legacyHeldSkull(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline,
                                    Operation<Void> original) {
        if (ConfigManager.legacyHeldSkull && displayContext.firstPerson()
            && FeatureModules.enabled(FeatureModules.Feature.ANIMATIONS)
            && (Object) this instanceof ItemModelVisibility.State state) {
            for (int i = 0; i < state.customcosmetics$layerCount(); i++) {
                if (!(state.customcosmetics$layers()[i] instanceof ItemModelVisibility.Layer layer) || !layer.customcosmetics$playerHead()) continue;
                pose.pushPose();
                try {
                    pose.translate(0f, 0.11f, 0f);
                    ViewModelAnimations.rotate(pose, Axis.YP, displayContext.leftHand() ? -225f : 225f);
                    pose.scale(0.44f, 0.44f, 0.44f);
                    original.call(pose, collector, light, overlay, outline);
                } finally {
                    pose.popPose();
                }
                return;
            }
        }
        original.call(pose, collector, light, overlay, outline);
    }
}
