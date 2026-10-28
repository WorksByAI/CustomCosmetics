// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import org.lwjgl.util.freetype.*;

/** Weight is baked into this mod's glyph atlas once, avoiding overlapping bold quads on Vulkan. */
public final class UiFontWeight {
    public interface Provider { void customcosmetics$weight(); }
    private static final Set<Long> FACES=ConcurrentHashMap.newKeySet();
    private UiFontWeight(){}
    public static boolean owns(Identifier file){return file.getNamespace().equals("customcosmetics")&&file.getPath().equals("lxgwwenkai-medium.ttf");}
    public static void register(FT_Face face){FACES.add(face.address());}
    public static void remove(FT_Face face){if(face!=null)FACES.remove(face.address());}
    // Use identical rasterization for metrics and atlas upload, including bitmap dimensions.
    public static int flags(FT_Face face,int vanilla){return FACES.contains(face.address())?FreeType.FT_LOAD_RENDER:vanilla;}
    public static int loaded(FT_Face face,int result){
        if(result==0&&FACES.contains(face.address()))FreeType.FT_GlyphSlot_Embolden(face.glyph());
        return result;
    }
}
