// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import net.minecraft.network.chat.Style;

/** Render-only metadata is attached to privately copied styles, never serialized to Minecraft. */
public record NicknameGlyphEffect(NicknameGradient gradient, double from, double to) {
    // Separate name occurrences must retain separate spans in the native text-run cache.
    @Override public boolean equals(Object other) {
        return other instanceof NicknameGlyphEffect effect && gradient == effect.gradient
            && Double.compare(from, effect.from) == 0 && Double.compare(to, effect.to) == 0;
    }
    @Override public int hashCode() {
        return 31 * (31 * System.identityHashCode(gradient) + Double.hashCode(from)) + Double.hashCode(to);
    }
    public interface Carrier {
        NicknameGlyphEffect customcosmetics$gradient();
        void customcosmetics$gradient(NicknameGlyphEffect effect);
    }
    public static NicknameGlyphEffect from(Style style) {
        return (Object)style instanceof Carrier carrier ? carrier.customcosmetics$gradient() : null;
    }
    public static Style attach(Style style, NicknameGlyphEffect effect) {
        int color = style.getColor() == null ? 0xFFFFFF : style.getColor().getValue();
        // withColor may reuse its receiver; invert and restore to guarantee an owned copy.
        Style copy = style.withColor(color ^ 0xFFFFFF).withColor(color);
        if ((Object)copy instanceof Carrier carrier) carrier.customcosmetics$gradient(effect);
        return copy;
    }
}
