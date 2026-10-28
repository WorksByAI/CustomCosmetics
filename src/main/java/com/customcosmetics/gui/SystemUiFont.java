// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.mojang.blaze3d.font.GlyphProvider;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.resources.Identifier;
import java.nio.file.*;
import java.util.Optional;
/** The native font manager owns glyph caching, textures, native memory and disposal. */
public final class SystemUiFont {
    public static final Identifier FILE=Identifier.fromNamespaceAndPath("customcosmetics","system/yahei.ttc");
    private static final Optional<Path> SOURCE=Boolean.getBoolean("customcosmetics.forceUnifont")?Optional.empty():SystemFontResolver.installed();
    public static final GlyphProvider EMPTY=()->IntSets.EMPTY_SET;
    private static volatile boolean loaded;
    private SystemUiFont(){}
    public static Optional<Path> source(){return SOURCE;}
    public static void loaded(boolean success){loaded=success;}
    public static boolean loaded(){return loaded;}
}
