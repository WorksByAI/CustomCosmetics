// SPDX-License-Identifier: LGPL-3.0-or-later
package com.customcosmetics.features.bobby.v261.mixin;

import com.customcosmetics.features.bobby.v261.ext.LevelLightEngineExt;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelLightEngine.class)
public abstract class LevelLightEngineMixin implements LevelLightEngineExt {
    @Unique
    private final LongSet bobbyActiveColumns = new LongOpenHashSet();

    @Override
    public void bobby_enabledColumn(long pos) {
        this.bobbyActiveColumns.add(pos);
    }

    @Override
    public void bobby_disableColumn(long pos) {
        this.bobbyActiveColumns.remove(pos);
    }

    @Inject(method = "lightOnInColumn", at = @At("HEAD"), cancellable = true)
    private void bobby_getLightSection(long sectionPos, CallbackInfoReturnable<Boolean> ci) {
        if (bobbyActiveColumns.contains(sectionPos)) {
            ci.setReturnValue(true);
        }
    }
}
