// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.LocalFontGlyphAccess;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.lwjgl.util.freetype.FT_Face;

import static org.lwjgl.util.freetype.FreeType.*;

/** Rasterizes an emboldened outline into the existing local atlas on the first use of a bold glyph. */
public final class LocalBoldGlyphs {
    private static final ThreadLocal<FT_Face> BAKING_FACE = new ThreadLocal<>();

    private LocalBoldGlyphs() {}

    static BakedGlyph bake(GlyphProvider provider, int codepoint, UnbakedGlyph.Stitcher atlas) {
        if (!(provider instanceof LocalFontGlyphAccess access)) return null;
        FT_Face face = access.customcosmetics$face();
        synchronized (face) {
            int index = FT_Get_Char_Index(face, codepoint);
            if (index == 0) return null;
            BAKING_FACE.set(face);
            try {
                return access.customcosmetics$loadGlyph(codepoint, face, index).bake(atlas);
            } finally {
                BAKING_FACE.remove();
            }
        }
    }

    public static boolean baking(FT_Face face) { return BAKING_FACE.get() == face; }

    public static int rasterize(FT_Face face, int index) {
        // Use the same load flags for the metrics pass and the atlas upload.
        int error = FT_Load_Glyph(face, index, FT_LOAD_NO_BITMAP);
        if (error != 0) return error;
        var slot = face.glyph();
        FT_GlyphSlot_Embolden(slot);
        return FT_Render_Glyph(slot, FT_RENDER_MODE_NORMAL);
    }
}
