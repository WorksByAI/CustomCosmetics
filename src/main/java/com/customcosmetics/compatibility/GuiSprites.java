// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;
import java.lang.invoke.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Cached typed adapter for the RenderPipeline package move in 26.3; no reflective varargs per draw. */
public final class GuiSprites {
    private static final Object PIPELINE;
    private static final MethodHandle BLIT;
    static{try{
        PIPELINE=RenderPipelines.class.getField("GUI_TEXTURED").get(null);
        var method=java.util.Arrays.stream(GuiGraphicsExtractor.class.getMethods()).filter(m->m.getName().equals("blitSprite")&&m.getParameterCount()==7&&m.getParameterTypes()[1]==Identifier.class&&m.getParameterTypes()[6]==int.class).findFirst().orElseThrow();
        BLIT=MethodHandles.publicLookup().unreflect(method).asType(MethodType.methodType(void.class,GuiGraphicsExtractor.class,Object.class,Identifier.class,int.class,int.class,int.class,int.class,int.class));
    }catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
    private GuiSprites(){}
    public static void draw(GuiGraphicsExtractor g,Identifier id,int x,int y,int w,int h,int color){
        try{BLIT.invokeExact(g,PIPELINE,id,x,y,w,h,color);}
        catch(RuntimeException|Error failure){throw failure;}
        catch(Throwable failure){throw new IllegalStateException("Cannot draw native GUI sprite",failure);}
    }
}
