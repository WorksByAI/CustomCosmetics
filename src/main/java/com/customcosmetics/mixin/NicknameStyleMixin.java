// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.nickname.NicknameGlyphEffect;
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

    @Inject(method="equals",at=@At("HEAD"),cancellable=true)
    private void customcosmetics$keepSeparateSpans(Object other,CallbackInfoReturnable<Boolean> result) {
        if (other instanceof Style style && !java.util.Objects.equals(customcosmetics$effect, NicknameGlyphEffect.from(style)))
            result.setReturnValue(false);
    }

    @Inject(method="hashCode",at=@At("RETURN"),cancellable=true)
    private void customcosmetics$spanHash(CallbackInfoReturnable<Integer> result) {
        if (customcosmetics$effect != null) result.setReturnValue(31 * result.getReturnValue() + customcosmetics$effect.hashCode());
    }

    @Inject(method="applyTo",at=@At("RETURN"),cancellable=true)
    private void customcosmetics$inheritGradient(Style parent,CallbackInfoReturnable<Style> result) {
        if(customcosmetics$effect != null && result.getReturnValue() != (Object)this)
            result.setReturnValue(NicknameGlyphEffect.attach(result.getReturnValue(),customcosmetics$effect));
    }
}
