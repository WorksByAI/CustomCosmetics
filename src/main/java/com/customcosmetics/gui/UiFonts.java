// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.customcosmetics.compatibility.UiFontAccess;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** A GUI-only view of Minecraft's cached native font provider, never a global font replacement. */
public final class UiFonts {
    public static final FontDescription.Resource CHINESE=new FontDescription.Resource(Identifier.fromNamespaceAndPath("customcosmetics","gui_chinese"));
    private static Font base,view;
    private UiFonts(){}
    public static boolean modern(){return "modern".equals(com.customcosmetics.config.ConfigManager.uiFont);}
    public static Font get(Font original){
        if(base!=original){
            var provider=((UiFontAccess)(Object)original).customcosmetics$provider();
            view=new Font(new Font.Provider(){
                public GlyphSource glyphs(FontDescription description){return provider.glyphs(modern()&&description.equals(FontDescription.DEFAULT)?CHINESE:description);}
                public EffectGlyph effect(){return provider.effect();}
            });base=original;
        }
        return view;
    }
}
