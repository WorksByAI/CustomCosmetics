// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import com.mojang.blaze3d.font.UnbakedGlyph;
import org.lwjgl.util.freetype.FT_Face;

public interface LocalFontGlyphAccess {
    FT_Face customcosmetics$face();
    UnbakedGlyph customcosmetics$loadGlyph(int codepoint, FT_Face face, int glyphIndex);
}
