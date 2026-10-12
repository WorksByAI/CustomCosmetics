// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.nickname.GradientGlyph;
import com.customcosmetics.features.nickname.NicknameGlyphEffect;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets="net.minecraft.client.gui.Font$PreparedTextBuilder")
public class NicknameGradientMixin {
    @Redirect(method="accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;createGlyph(FFIILnet/minecraft/network/chat/Style;FF)Lnet/minecraft/client/gui/font/TextRenderable$Styled;"))
    private TextRenderable.Styled customcosmetics$gradientGlyph(BakedGlyph baked,float x,float y,int color,int shadow,Style style,float bold,float offset) {
        NicknameGlyphEffect effect=NicknameGlyphEffect.from(style);
        if (effect != null) color = GradientGlyph.foregroundColor(color, shadow);
        TextRenderable.Styled glyph=baked.createGlyph(x,y,color,shadow,style,bold,offset);
        return glyph == null || effect == null ? glyph : new GradientGlyph(glyph,effect,x,y,baked.info().getAdvance(style.isBold()),color).wrapped();
    }
}
