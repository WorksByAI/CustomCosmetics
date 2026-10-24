// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.font.TextRenderable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.joml.Matrix4fc;
import org.joml.Matrix3x2fc;

/** Uses the original glyph texture, geometry, draw order, bounds and shadow. */
public record GradientGlyph(TextRenderable.Styled original, NicknameGlyphEffect effect,
                            float x, float y, float advance, int foreground) implements InvocationHandler {
    private static final Class<?>[] STYLED_INTERFACES={TextRenderable.Styled.class};
    private static final ThreadLocal<RenderBuffers> RENDER_BUFFERS=ThreadLocal.withInitial(RenderBuffers::new);
    /** Each nested draw owns a separate slot. Nothing here is shared between render threads. */
    private static final class RenderBuffers {
        private final GradientVertices[] cached=new GradientVertices[4];
        private int depth;
        GradientVertices acquire(VertexConsumer destination,NicknameGlyphEffect effect,float x,float y,float advance,int foreground,long time){
            int slot=depth++;
            if(slot>=cached.length)return new GradientVertices(destination,effect,x,y,advance,foreground,time);
            var vertices=cached[slot];
            if(vertices==null)cached[slot]=vertices=new GradientVertices(destination,effect,x,y,advance,foreground,time);
            else vertices.configure(destination,effect,x,y,advance,foreground,time);
            return vertices;
        }
        void release(GradientVertices vertices){vertices.clear();depth--;}
    }
    private static GradientVertices acquireVertices(VertexConsumer destination,NicknameGlyphEffect effect,float x,float y,float advance,int foreground,long time){
        return RENDER_BUFFERS.get().acquire(destination,effect,x,y,advance,foreground,time);
    }
    /** The foreground is recolored anyway; keep it distinguishable from an identical shadow. */
    public static int foregroundColor(int color, int shadow) {
        return color == shadow ? color ^ 0xFFFFFF : color;
    }
    /** GPU return types moved in 26.3; delegate their runtime interface without linking old types. */
    public TextRenderable.Styled wrapped() {
        return (TextRenderable.Styled)Proxy.newProxyInstance(TextRenderable.Styled.class.getClassLoader(),STYLED_INTERFACES,this);
    }
    public static GradientGlyph unwrap(TextRenderable.Styled glyph) {
        if (Proxy.isProxyClass(glyph.getClass()) && Proxy.getInvocationHandler(glyph) instanceof GradientGlyph gradient) return gradient;
        return null;
    }
    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("render")) {
            render((Matrix4fc)args[0],(VertexConsumer)args[1],(int)args[2],(boolean)args[3]);return null;
        }
        if (method.getName().equals("equals")) return proxy == args[0];
        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
        try { return method.invoke(original,args); }
        catch (InvocationTargetException error) { throw error.getCause(); }
    }
    public void render(Matrix4fc pose, VertexConsumer vertices, int light, boolean inverseDepth) {
        var colors = acquireVertices(vertices, effect, x, y, advance, foreground, AnimationClock.millis());
        try {
            colors.bulkPose(pose);
            original.render(pose, colors.optimized(), light, inverseDepth);
            colors.finish();
        } finally { RENDER_BUFFERS.get().release(colors); }
    }

    /** Sub-pixel strips let a narrow highlight travel inside a single Chinese glyph. */
    public record Sample(NicknameGlyphEffect effect,float x,float y,float advance,boolean shadow) {}
    public record Quad(float left,float top,float right,float bottom,int color) {}
    @FunctionalInterface public interface QuadSelector { Sample select(Quad quad); }
    public static final class GradientVertices implements VertexConsumer {
        private static final Method UV3 = uv3Method();
        private static Method uv3Method() {
            try { return VertexConsumer.class.getMethod("setUv3",float.class,float.class); }
            catch(NoSuchMethodException older) { return null; }
        }
        private VertexConsumer destination;
        private NicknameGlyphEffect effect;
        private float x, y, advance;
        private int foreground;
        private long time;
        private final QuadSelector selector;
        private final Vertex[] quad = {new Vertex(),new Vertex(),new Vertex(),new Vertex()};
        private final Vertex interpolated = new Vertex();
        private int count;
        private Vertex current;
        private org.joml.Matrix4f bulkInverse;
        private boolean hasBulkPose;
        private boolean inverseReady;
        private org.joml.Vector3f bulkLocal;
        public GradientVertices bulkPose(Matrix4fc pose){
            hasBulkPose=pose!=null;inverseReady=false;
            if(pose!=null){if(bulkInverse==null)bulkInverse=new org.joml.Matrix4f();bulkInverse.set(pose);}
            return this;
        }
        public VertexConsumer addBulkVertex(float x,float y,float z){
            addVertex(x,y,z);
            if(hasBulkPose){
                if(!inverseReady){
                    bulkInverse.invert();inverseReady=true;
                    if(bulkLocal==null)bulkLocal=new org.joml.Vector3f();
                }
                bulkInverse.transformPosition(x,y,z,bulkLocal);current.paintX=bulkLocal.x;current.paintY=bulkLocal.y;
            }
            return this;
        }
        private VertexConsumer optimized;
        public VertexConsumer optimized(){
            if(optimized==null)optimized=com.customcosmetics.compatibility.SodiumGlyphWriter.wrap(this);
            return optimized;
        }

        public GradientVertices(VertexConsumer destination, NicknameGlyphEffect effect, float x, float y, float advance, int foreground, long time) {
            this.selector = null;
            configure(destination,effect,x,y,advance,foreground,time);
        }
        public GradientVertices(VertexConsumer destination,QuadSelector selector,long time) {
            this.destination=destination;this.selector=selector;this.time=time;
            effect=null;x=y=0;advance=1;foreground=0;
        }
        private void configure(VertexConsumer destination,NicknameGlyphEffect effect,float x,float y,float advance,int foreground,long time){
            this.destination=destination;this.effect=effect;this.x=x;this.y=y;this.advance=Math.max(.01f,advance);this.foreground=foreground;this.time=time;
        }
        private void clear(){
            destination=null;effect=null;hasBulkPose=false;inverseReady=false;count=0;current=null;
            for(var vertex:quad){vertex.pose=null;vertex.pose2d=null;}
            interpolated.pose=null;interpolated.pose2d=null;
        }
        @Override public VertexConsumer addVertex(Matrix4fc pose, float x, float y, float z) {
            addVertex(x, y, z); current.pose = pose; return this;
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            if (count == 4) finish();
            current = quad[count++];current.reset();current.x = current.paintX = x; current.y = current.paintY = y; current.z = z;
            return this;
        }
        @Override public VertexConsumer addVertexWith2DPose(Matrix3x2fc pose,float x,float y) {
            addVertex(x,y,0);current.pose2d=pose;return this;
        }
        @Override public VertexConsumer setColor(int color) { current.color = color; return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return setColor((a << 24) | (r << 16) | (g << 8) | b); }
        @Override public VertexConsumer setUv(float u, float v) { current.u = u; current.v = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { current.overlay = (v << 16) | (u & 65535); current.hasOverlay = true; return this; }
        @Override public VertexConsumer setUv2(int u, int v) { current.light = (v << 16) | (u & 65535); return this; }
        // Present in 26.3, absent in older compile targets.
        public VertexConsumer setUv3(float u, float v) { current.u3=u;current.v3=v;current.hasUv3=true;return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { current.nx=x;current.ny=y;current.nz=z;current.hasNormal=true;return this; }
        @Override public VertexConsumer setLineWidth(float width) { current.lineWidth=width;current.hasLineWidth=true;return this; }

        public void finish() {
            if (count == 0) return;
            Sample sample=null;
            boolean paint=false;
            if(count==4){
                if(selector==null)paint=quad[0].color==foreground;
                else {
                    float left=Float.POSITIVE_INFINITY,top=left,right=Float.NEGATIVE_INFINITY,bottom=right;
                    for(var vertex:quad){left=Math.min(left,vertex.paintX);top=Math.min(top,vertex.paintY);right=Math.max(right,vertex.paintX);bottom=Math.max(bottom,vertex.paintY);}
                    sample=selector.select(new Quad(left,top,right,bottom,quad[0].color));
                    paint=sample!=null;
                }
            }
            if (!paint) {
                for (int i=0;i<count;i++) emit(quad[i], quad[i].color);
            } else {
                int strips = Math.clamp((int)Math.ceil(Math.abs(quad[3].paintX - quad[0].paintX) * 2-0.0001f), 1, 64);
                int leftTop=0,leftBottom=0;
                for (int strip=0;strip<strips;strip++) {
                    float a = strip/(float)strips, b=(strip+1)/(float)strips;
                    between(interpolated,quad[0],quad[3],a);
                    if(strip==0)leftTop=color(interpolated,sample);
                    emit(interpolated,leftTop);
                    between(interpolated,quad[1],quad[2],a);
                    if(strip==0)leftBottom=color(interpolated,sample);
                    emit(interpolated,leftBottom);
                    between(interpolated,quad[1],quad[2],b);leftBottom=color(interpolated,sample);emit(interpolated,leftBottom);
                    between(interpolated,quad[0],quad[3],b);leftTop=color(interpolated,sample);emit(interpolated,leftTop);
                }
            }
            count=0;current=null;
        }
        private int color(Vertex vertex,Sample sample){
            var paint=sample==null?effect:sample.effect();
            float originX=sample==null?x:sample.x(),originY=sample==null?y:sample.y(),width=sample==null?advance:sample.advance();
            double fraction=(vertex.paintX-originX)/Math.max(.01f,width);
            double position=paint.from()+(paint.to()-paint.from())*fraction;
            int rgb=paint.gradient().color(position,(vertex.paintY-originY)/8.0,time);
            if(sample!=null&&sample.shadow())rgb=(rgb&0xFCFCFC)>>>2;
            return (vertex.color&0xFF000000)|rgb;
        }
        private void emit(Vertex vertex, int color) {
            VertexConsumer sink = vertex.pose2d!=null?destination.addVertexWith2DPose(vertex.pose2d,vertex.x,vertex.y)
                :vertex.pose == null ? destination.addVertex(vertex.x,vertex.y,vertex.z):destination.addVertex(vertex.pose,vertex.x,vertex.y,vertex.z);
            sink.setColor(color).setUv(vertex.u,vertex.v).setLight(vertex.light);
            if(vertex.hasUv3) {
                try { UV3.invoke(sink,vertex.u3,vertex.v3); }
                catch(ReflectiveOperationException error) { throw new IllegalStateException("Cannot preserve the glyph UV3 attribute",error); }
            }
            if(vertex.hasOverlay)sink.setOverlay(vertex.overlay);
            if(vertex.hasNormal)sink.setNormal(vertex.nx,vertex.ny,vertex.nz);
            if(vertex.hasLineWidth)sink.setLineWidth(vertex.lineWidth);
        }
        private static Vertex between(Vertex v,Vertex a,Vertex b,float t) {
            v.pose=a.pose;v.pose2d=a.pose2d;v.color=a.color;v.light=a.light;
            v.x=a.x+(b.x-a.x)*t;v.y=a.y+(b.y-a.y)*t;v.z=a.z+(b.z-a.z)*t;
            v.paintX=a.paintX+(b.paintX-a.paintX)*t;v.paintY=a.paintY+(b.paintY-a.paintY)*t;
            v.u=a.u+(b.u-a.u)*t;v.v=a.v+(b.v-a.v)*t;
            v.u3=a.u3+(b.u3-a.u3)*t;v.v3=a.v3+(b.v3-a.v3)*t;v.hasUv3=a.hasUv3;
            v.overlay=a.overlay;v.hasOverlay=a.hasOverlay;v.nx=a.nx;v.ny=a.ny;v.nz=a.nz;v.hasNormal=a.hasNormal;
            v.lineWidth=a.lineWidth;v.hasLineWidth=a.hasLineWidth;return v;
        }
        private static final class Vertex {
            Matrix4fc pose;Matrix3x2fc pose2d;float x,y,z,paintX,paintY,u,v,u3,v3,nx,ny,nz,lineWidth;int color,light,overlay;boolean hasOverlay,hasUv3,hasNormal,hasLineWidth;
            void reset(){
                pose=null;pose2d=null;x=y=z=paintX=paintY=u=v=u3=v3=nx=ny=nz=lineWidth=0;color=light=overlay=0;
                hasOverlay=hasUv3=hasNormal=hasLineWidth=false;
            }
        }
    }
}
