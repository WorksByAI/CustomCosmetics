// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.nickname.NicknameGlyphEffect;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Style.class)
public class NicknameStyleMixin implements NicknameGlyphEffect.Carrier {
    @Unique private transient NicknameGlyphEffect customcosmetics$effect;
    @Override public NicknameGlyphEffect customcosmetics$gradient() { return customcosmetics$effect; }
    @Override public void customcosmetics$gradient(NicknameGlyphEffect effect) { customcosmetics$effect=effect; }

    @ModifyReturnValue(method="equals",at=@At("RETURN"))
    private boolean customcosmetics$keepSeparateSpans(boolean original,Object other) {
        return original && (!(other instanceof Style style) || java.util.Objects.equals(customcosmetics$effect,NicknameGlyphEffect.from(style)));
    }

    @ModifyReturnValue(method="hashCode",at=@At("RETURN"))
    private int customcosmetics$spanHash(int original) {
        return customcosmetics$effect==null?original:31*original+customcosmetics$effect.hashCode();
    }

    @ModifyReturnValue(method="applyTo",at=@At("RETURN"))
    private Style customcosmetics$inheritGradient(Style original) {
        return customcosmetics$effect != null && original != (Object)this?NicknameGlyphEffect.attach(original,customcosmetics$effect):original;
    }
}
