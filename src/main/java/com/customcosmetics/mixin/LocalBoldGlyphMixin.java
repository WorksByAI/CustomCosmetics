// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.compatibility.LocalFontGlyphAccess;
import com.customcosmetics.gui.LocalBoldGlyphs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;

public final class LocalBoldGlyphMixin {
    @Mixin(TrueTypeGlyphProvider.class)
    public static abstract class Metrics implements LocalFontGlyphAccess {
        @Override @Invoker("validateFontOpen")
        public abstract FT_Face customcosmetics$face();

        @Override @Invoker("loadGlyph")
        public abstract UnbakedGlyph customcosmetics$loadGlyph(int codepoint, FT_Face face, int glyphIndex);

        @WrapOperation(method = "loadGlyph", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
        private int customcosmetics$metrics(FT_Face face, int index, int flags, Operation<Integer> original) {
            return LocalBoldGlyphs.baking(face) ? LocalBoldGlyphs.rasterize(face, index) : original.call(face, index, flags);
        }
    }

    @Mixin(NativeImage.class)
    public static class Bitmap {
        @WrapOperation(method = "copyFromFont", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
        private int customcosmetics$bitmap(FT_Face face, int index, int flags, Operation<Integer> original) {
            return LocalBoldGlyphs.baking(face) ? LocalBoldGlyphs.rasterize(face, index) : original.call(face, index, flags);
        }
    }
}
