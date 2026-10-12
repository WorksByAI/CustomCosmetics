// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.font.TextRenderable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.joml.Matrix4fc;

/** Uses the original glyph texture, geometry, draw order, bounds and shadow. */
public record GradientGlyph(TextRenderable.Styled original, NicknameGlyphEffect effect,
                            float x, float y, float advance, int foreground) implements InvocationHandler {
    /** The foreground is recolored anyway; keep it distinguishable from an identical shadow. */
    public static int foregroundColor(int color, int shadow) {
        return color == shadow ? color ^ 0xFFFFFF : color;
    }
    /** GPU return types moved in 26.3; delegate their runtime interface without linking old types. */
    public TextRenderable.Styled wrapped() {
        return (TextRenderable.Styled)Proxy.newProxyInstance(TextRenderable.Styled.class.getClassLoader(),new Class<?>[]{TextRenderable.Styled.class},this);
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
        var colors = new GradientVertices(vertices, effect, x, y, advance, foreground, System.currentTimeMillis());
        original.render(pose, colors, light, inverseDepth);
        colors.finish();
    }

    /** Sub-pixel strips let a narrow highlight travel inside a single Chinese glyph. */
    public static final class GradientVertices implements VertexConsumer {
        private static final Method UV3 = uv3Method();
        private static Method uv3Method() {
            try { return VertexConsumer.class.getMethod("setUv3",float.class,float.class); }
            catch(NoSuchMethodException older) { return null; }
        }
        private final VertexConsumer destination;
        private final NicknameGlyphEffect effect;
        private final float x, y, advance;
        private final int foreground;
        private final long time;
        private final Vertex[] quad = new Vertex[4];
        private int count;
        private Vertex current;

        public GradientVertices(VertexConsumer destination, NicknameGlyphEffect effect, float x, float y, float advance, int foreground, long time) {
            this.destination = destination; this.effect = effect; this.x = x; this.y = y;
            this.advance = Math.max(0.01f, advance); this.foreground = foreground; this.time = time;
        }
        @Override public VertexConsumer addVertex(Matrix4fc pose, float x, float y, float z) {
            addVertex(x, y, z); current.pose = pose; return this;
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            if (count == 4) finish();
            current = new Vertex(); current.x = x; current.y = y; current.z = z; quad[count++] = current;
            return this;
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
            if (count != 4 || quad[0].color != foreground) {
                for (int i=0;i<count;i++) emit(quad[i], false);
            } else {
                int strips = Math.clamp((int)Math.ceil(Math.abs(quad[3].x - quad[0].x) * 2), 1, 64);
                for (int strip=0;strip<strips;strip++) {
                    float a = strip/(float)strips, b=(strip+1)/(float)strips;
                    emit(between(quad[0],quad[3],a),true);
                    emit(between(quad[1],quad[2],a),true);
                    emit(between(quad[1],quad[2],b),true);
                    emit(between(quad[0],quad[3],b),true);
                }
            }
            count=0;current=null;
        }
        private void emit(Vertex vertex, boolean gradient) {
            VertexConsumer sink = vertex.pose == null ? destination.addVertex(vertex.x,vertex.y,vertex.z)
                : destination.addVertex(vertex.pose,vertex.x,vertex.y,vertex.z);
            int color=vertex.color;
            if (gradient) {
                double fraction=(vertex.x-x)/advance;
                double position=effect.from()+(effect.to()-effect.from())*fraction;
                color=(color & 0xFF000000) | effect.gradient().color(position,(vertex.y-y)/8.0,time);
            }
            sink.setColor(color).setUv(vertex.u,vertex.v).setLight(vertex.light);
            if(vertex.hasUv3) {
                try { UV3.invoke(sink,vertex.u3,vertex.v3); }
                catch(ReflectiveOperationException error) { throw new IllegalStateException("Cannot preserve the glyph UV3 attribute",error); }
            }
            if(vertex.hasOverlay)sink.setOverlay(vertex.overlay);
            if(vertex.hasNormal)sink.setNormal(vertex.nx,vertex.ny,vertex.nz);
            if(vertex.hasLineWidth)sink.setLineWidth(vertex.lineWidth);
        }
        private static Vertex between(Vertex a,Vertex b,float t) {
            Vertex v=new Vertex();v.pose=a.pose;v.color=a.color;v.light=a.light;
            v.x=a.x+(b.x-a.x)*t;v.y=a.y+(b.y-a.y)*t;v.z=a.z+(b.z-a.z)*t;
            v.u=a.u+(b.u-a.u)*t;v.v=a.v+(b.v-a.v)*t;
            v.u3=a.u3+(b.u3-a.u3)*t;v.v3=a.v3+(b.v3-a.v3)*t;v.hasUv3=a.hasUv3;
            v.overlay=a.overlay;v.hasOverlay=a.hasOverlay;v.nx=a.nx;v.ny=a.ny;v.nz=a.nz;v.hasNormal=a.hasNormal;
            v.lineWidth=a.lineWidth;v.hasLineWidth=a.hasLineWidth;return v;
        }
        private static final class Vertex {
            Matrix4fc pose;float x,y,z,u,v,u3,v3,nx,ny,nz,lineWidth;int color,light,overlay;boolean hasOverlay,hasUv3,hasNormal,hasLineWidth;
        }
    }
}
