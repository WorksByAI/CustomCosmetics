// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Entity.class)
public abstract class IqAddonsSpawnerGlowMixin {
    @Shadow private int id;
    @Shadow private boolean hasGlowingTag;
    @Shadow protected abstract boolean getSharedFlag(int flag);
    @Shadow public abstract Level level();

    @WrapMethod(method = "isCurrentlyGlowing")
    private boolean csb$glowBeforeIdAssignment(Operation<Boolean> original) {
        // IQAddons looks up the entity by ID before vanilla evaluates the glow state.
        if (id != 0) return original.call();
        return level().isClientSide() ? getSharedFlag(6) : hasGlowingTag;
    }
}
