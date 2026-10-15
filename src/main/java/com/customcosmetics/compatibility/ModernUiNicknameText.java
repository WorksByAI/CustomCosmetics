// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import com.customcosmetics.features.nickname.*;
import com.customcosmetics.utils.NickRenderUtils;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import org.joml.Matrix4fc;
import java.lang.reflect.*;
import java.util.*;

/** Optional Modern UI integration. All shaping, glyphs, textures and draw modes remain native. */
public final class ModernUiNicknameText {
    public interface Carrier {
        Palette customcosmetics$palette();
        void customcosmetics$palette(Palette value);
    }
    public record Prepared(FormattedCharSequence clean,FormattedCharSequence masked,Palette palette) {}
    public record PaletteSequence(FormattedCharSequence clean,FormattedCharSequence delegate,Palette palette) implements NicknameText {
        @Override public boolean accept(FormattedCharSink sink){return delegate.accept(sink);}
    }
    private record Glyph(int codepoint,Style style) {}
    private static final ThreadLocal<Palette> ACTIVE=new ThreadLocal<>();
    private static Boolean nativeEffects;
    private static Method drawSequence;
    private ModernUiNicknameText(){}

    public static boolean active(Font font){return font.getSplitter().getClass().getName().equals("icyllis.modernui.mc.text.ModernStringSplitter");}
    private static synchronized boolean nativeEffects(){
        if(nativeEffects!=null)return nativeEffects;
        try{
            var loader=ModernUiNicknameText.class.getClassLoader();
            var runs=Class.forName("icyllis.modernui.mc.text.TextRunRenderState",false,loader);
            var effects=Class.forName("icyllis.modernui.mc.text.TextEffectRenderState",false,loader);
            var prepared=Class.forName("icyllis.modernui.mc.text.ModernPreparedText",false,loader);
            nativeEffects=Carrier.class.isAssignableFrom(runs)&&Carrier.class.isAssignableFrom(effects)&&Carrier.class.isAssignableFrom(prepared);
            runs.getDeclaredMethod("buildPass",VertexConsumer.class,float.class,int.class,int.class,int.class,int.class,float.class,boolean.class);
            effects.getDeclaredMethod("buildPass",VertexConsumer.class,int.class,int.class,int.class,int.class,float.class,boolean.class);
        }catch(ReflectiveOperationException unavailable){nativeEffects=false;}
        return nativeEffects;
    }
    public static FormattedCharSequence sequence(String text){return sink->StringDecomposer.iterateFormatted(text,Style.EMPTY,sink);}
    public static FormattedCharSequence sequence(FormattedText text){return Language.getInstance().getVisualOrder(text);}
    public static Prepared prepare(FormattedCharSequence input,Font font,int color,int background){
        if(input instanceof PaletteSequence masked)return new Prepared(masked.clean(),masked,masked.palette());
        var resolved=NickRenderUtils.handleCharSequence(input);
        var measured=NickRenderUtils.measureGradient(resolved,font);
        if(measured!=input&&!(measured instanceof NicknameText))measured=new ProcessedNicknameText(measured);
        if(!nativeEffects())return new Prepared(measured,measured,Palette.EMPTY);
        return mask(measured,color,background);
    }
    public static Prepared mask(FormattedCharSequence clean,int color,int background){
        boolean[] styled={false};
        clean.accept((index,style,cp)->{styled[0]=NicknameGlyphEffect.from(style)!=null;return !styled[0];});
        if(!styled[0])return new Prepared(clean,clean,Palette.EMPTY);
        var glyphs=new ArrayList<Glyph>();var reserved=new HashSet<Integer>(Set.of(0,0xFFFFFF));
        reserve(reserved,color);reserve(reserved,background);
        clean.accept((index,style,cp)->{glyphs.add(new Glyph(cp,style));if(style.getColor()!=null)reserve(reserved,style.getColor().getValue());return true;});
        var palette=new Palette(reserved);var result=new ArrayList<FormattedCharSequence>(glyphs.size());
        for(var glyph:glyphs){
            var effect=NicknameGlyphEffect.from(glyph.style());var style=glyph.style();
            if(effect!=null)style=style.withColor(palette.add(effect));
            result.add(FormattedCharSequence.codepoint(glyph.codepoint(),style));
        }
        return palette.empty()?new Prepared(clean,clean,Palette.EMPTY)
            :new Prepared(clean,new PaletteSequence(clean,FormattedCharSequence.composite(result),palette),palette);
    }
    private static void reserve(Set<Integer> reserved,int color){reserved.add(color&0xFFFFFF);reserved.add(shadow(color));}
    public static int shadow(int color){return (color&0xFCFCFC)>>>2;}
    public static Palette current(){var value=ACTIVE.get();return value==null?Palette.EMPTY:value;}
    public static Scope enter(Palette palette){return new Scope(palette);}
    public static final class Scope implements AutoCloseable {
        private final Palette previous=ACTIVE.get();
        private Scope(Palette next){ACTIVE.set(next);}
        @Override public void close(){if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
    }
    public static final class Palette {
        public static final Palette EMPTY=new Palette(Set.of());
        private final Set<Integer> reserved;
        private final Map<Integer,NicknameGlyphEffect> foreground=new HashMap<>(),shadows=new HashMap<>();
        private int next;
        public Palette(Set<Integer> reserved){this.reserved=new HashSet<>(reserved);}
        public boolean empty(){return foreground.isEmpty();}
        public int add(NicknameGlyphEffect effect){
            int marker,shade;
            do{
                if(next>=131072)throw new IllegalArgumentException("Too many nickname color spans");
                int n=next++;marker=(128+((n>>>12)&31)*4)<<16|((n>>>6)&63)*4<<8|(n&63)*4;shade=shadow(marker);
            }while(reserved.contains(marker)||reserved.contains(shade));
            reserved.add(marker);reserved.add(shade);foreground.put(marker,effect);shadows.put(shade,effect);return marker;
        }
        public GradientGlyph.GradientVertices wrap(VertexConsumer destination,boolean isShadow){
            return new GradientGlyph.GradientVertices(destination,quad->{
                var effect=(isShadow?shadows:foreground).get(quad.color()&0xFFFFFF);
                return effect==null?null:new GradientGlyph.Sample(effect,quad.left(),quad.top(),Math.max(.01f,quad.right()-quad.left()),isShadow);
            },System.currentTimeMillis());
        }
    }
    /** Wrapper is scoped to one native layout draw, including its buffer switches. */
    public static final class Buffers implements AutoCloseable,InvocationHandler {
        private final Object original;
        private final Palette palette;private final boolean shadow;
        private final Matrix4fc pose;
        private final IdentityHashMap<VertexConsumer,GradientGlyph.GradientVertices> wrappers=new IdentityHashMap<>();
        public Buffers(Object original,Palette palette,boolean shadow){this(original,palette,shadow,null);}
        public Buffers(Object original,Palette palette,boolean shadow,Matrix4fc pose){this.original=original;this.palette=palette;this.shadow=shadow;this.pose=pose;}
        public Object source(){var type=bufferType(original);return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},this);}
        @Override public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{
            try{
                // A buffer switch can flush the previous native buffer immediately.
                if(method.getName().equals("getBuffer"))wrappers.values().forEach(GradientGlyph.GradientVertices::finish);
                var result=method.invoke(original,args);
                return method.getName().equals("getBuffer")&&result instanceof VertexConsumer vertices
                    ?wrappers.computeIfAbsent(vertices,key->palette.wrap(key,shadow).bulkPose(pose)).optimized():result;
            }catch(InvocationTargetException problem){throw problem.getCause();}
        }
        @Override public void close(){wrappers.values().forEach(GradientGlyph.GradientVertices::finish);}
    }
    private static Class<?> bufferType(Object buffers){try{return Class.forName("net.minecraft.client.renderer.MultiBufferSource",false,buffers.getClass().getClassLoader());}catch(ClassNotFoundException problem){throw new IllegalStateException("Unsupported Modern UI world buffers",problem);}}
    public static float drawSequence(Object renderer,FormattedCharSequence text,float x,float y,int color,boolean shadow,
                                     Matrix4fc pose,Object buffers,Font.DisplayMode mode,int background,int light){
        try{
            if(drawSequence==null)drawSequence=renderer.getClass().getMethod("drawText",FormattedCharSequence.class,float.class,float.class,int.class,boolean.class,Matrix4fc.class,bufferType(buffers),Font.DisplayMode.class,int.class,int.class);
            return (float)drawSequence.invoke(renderer,text,x,y,color,shadow,pose,buffers,mode,background,light);
        }catch(ReflectiveOperationException problem){throw new IllegalStateException("Cannot render a Modern UI nickname",problem instanceof InvocationTargetException invocation?invocation.getCause():problem);}
    }
}
