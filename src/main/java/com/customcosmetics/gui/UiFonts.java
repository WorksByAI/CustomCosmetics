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
    private static final FontDescription.Resource TITLE=new FontDescription.Resource(Identifier.fromNamespaceAndPath("customcosmetics","gui_title"));
    private static Font base,view,title;
    private UiFonts(){}
    public static boolean modern(){return com.customcosmetics.update.ClientFlavor.BUNDLED_FONT&&"modern".equals(com.customcosmetics.config.ConfigManager.uiFont);}
    public static Font get(Font original){
        if(base!=original){view=create(original,false);title=create(original,true);base=original;}
        return view;
    }
    private static Font create(Font original,boolean heading){
        var provider=((UiFontAccess)(Object)original).customcosmetics$provider();
        return new Font(new Font.Provider(){
            public GlyphSource glyphs(FontDescription description){
                if(!modern()||!description.equals(FontDescription.DEFAULT)&&!description.equals(CHINESE))return provider.glyphs(description);
                return provider.glyphs(heading?TITLE:CHINESE);
            }
            public EffectGlyph effect(){return provider.effect();}
        });
    }
    /** Modern titles use larger native glyphs, with no fractional enlargement of the body font. */
    public static void drawTitle(GuiGraphicsExtractor g,String text,int x,int y,int color,int maxWidth){
        get(net.minecraft.client.Minecraft.getInstance().font);
        if(modern()){PinkUi.text(g,title,text,x,y,color,maxWidth);return;}
        g.pose().pushMatrix();g.pose().translate(x,y-2);g.pose().scale(1.5f,1.5f);
        PinkUi.text(g,view,text,0,0,color,(int)(maxWidth/1.5));g.pose().popMatrix();
    }
}
