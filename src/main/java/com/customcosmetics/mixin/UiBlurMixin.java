// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.gui.UiBlurPolicy;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Options.class)
public abstract class UiBlurMixin {
    @ModifyReturnValue(method="getMenuBackgroundBlurriness",at=@At("RETURN"))
    private int customcosmetics$menuBlur(int vanilla){return UiBlurPolicy.radius(vanilla);}
}
