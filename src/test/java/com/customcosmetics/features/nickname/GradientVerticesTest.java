package com.customcosmetics.features.nickname;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GradientVerticesTest {
    @Test void interpolationScratchNeverLeaksAttributesFromPreviousQuads(){
        var output=new Capture();var effect=new NicknameGlyphEffect(new NicknameGradient("smooth",0x24CFDF,0xEA78C5,1),0,1);
        var vertices=new GradientGlyph.GradientVertices(output,effect,100,20,8,0xFF24CFDF,0);
        for(int color:new int[]{0xFF24CFDF,0xFF000000,0xFF24CFDF}){
            vertices.addVertex(100,20,0).setColor(color).setUv(.2f,.3f).setLight(0xF000F0);
            vertices.addVertex(100,28,0).setColor(color).setUv(.2f,.7f).setLight(0xF000F0);
            vertices.addVertex(108,28,0).setColor(color).setUv(.6f,.7f).setLight(0xF000F0);
            vertices.addVertex(108,20,0).setColor(color).setUv(.6f,.3f).setLight(0xF000F0);vertices.finish();
        }
        assertEquals(132,output.vertices.size());assertTrue(output.vertices.subList(64,68).stream().allMatch(v->v.color==0xFF000000));
        assertEquals(output.vertices.subList(0,64).stream().map(v->v.color).toList(),output.vertices.subList(68,132).stream().map(v->v.color).toList());
    }
    @Test void oneGlyphGetsMultipleColorsWithoutChangingAlphaAtlasCoordinatesOrLighting() {
        var output=new Capture();
        draw(output,new Matrix4f(),0xB224CFDF);
        assertTrue(output.vertices.size()>4);
        assertTrue(output.vertices.stream().map(v->v.color).distinct().count()>5);
        for(var v:output.vertices){assertEquals(0xB2,v.color>>>24);assertEquals(0x00F000F0,v.light);assertTrue(v.x>=100&&v.x<=108);assertTrue(v.u>=.2f&&v.u<=.6f);}
        assertEquals(.2f,output.vertices.getFirst().u);assertEquals(.6f,output.vertices.getLast().u);
        assertEquals(20,output.vertices.getFirst().y);assertEquals(28,output.vertices.get(1).y);
    }
    @Test void aWorldTransformChangesPositionsButDoesNotChangeGradientColors() {
        var flat=new Capture();var moved=new Capture();
        draw(flat,new Matrix4f(),0xB224CFDF);draw(moved,new Matrix4f().translate(12,7,2).rotateY(.4f),0xB224CFDF);
        assertEquals(flat.vertices.stream().map(v->v.color).toList(),moved.vertices.stream().map(v->v.color).toList());
        assertNotEquals(flat.vertices.getFirst().x,moved.vertices.getFirst().x);
    }
    @Test void theOriginalShadowQuadRemainsUncoloredAndUnsplit() {
        var output=new Capture();draw(output,new Matrix4f(),0xB2093337);
        assertEquals(4,output.vertices.size());assertTrue(output.vertices.stream().allMatch(v->v.color==0xB2093337));
    }
    @Test void anIdenticalBlackForegroundAndShadowStillKeepsTheShadowUntouched() {
        int original = 0xB2000000;
        int foreground = GradientGlyph.foregroundColor(original, original);
        assertEquals(original >>> 24, foreground >>> 24);
        assertNotEquals(original, foreground);
        assertEquals(0xB224CFDF, GradientGlyph.foregroundColor(0xB224CFDF, 0xB2093337));
        var output = new Capture();
        draw(output, new Matrix4f(), original, foreground);
        assertEquals(4, output.vertices.size());
        assertTrue(output.vertices.stream().allMatch(v -> v.color == original));
    }
    private static void draw(Capture destination,Matrix4f pose,int color) {
        draw(destination, pose, color, 0xB224CFDF);
    }
    private static void draw(Capture destination,Matrix4f pose,int color,int foreground) {
        var effect=new NicknameGlyphEffect(new NicknameGradient("smooth",0x24CFDF,0xEA78C5,1),0,1);
        var vertices=new GradientGlyph.GradientVertices(destination,effect,100,20,8,foreground,0);
        vertices.addVertex(pose,100,20,0).setColor(color).setUv(.2f,.3f).setLight(0x00F000F0);
        vertices.addVertex(pose,100,28,0).setColor(color).setUv(.2f,.7f).setLight(0x00F000F0);
        vertices.addVertex(pose,108,28,0).setColor(color).setUv(.6f,.7f).setLight(0x00F000F0);
        vertices.addVertex(pose,108,20,0).setColor(color).setUv(.6f,.3f).setLight(0x00F000F0);
        vertices.finish();
    }
    private static final class V {float x,y,z,u,v;int color,light;}
    private static final class Capture implements VertexConsumer {
        final List<V> vertices=new ArrayList<>();V current;
        @Override public VertexConsumer addVertex(float x,float y,float z){current=new V();current.x=x;current.y=y;current.z=z;vertices.add(current);return this;}
        @Override public VertexConsumer setColor(int color){current.color=color;return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return setColor((a<<24)|(r<<16)|(g<<8)|b);}
        @Override public VertexConsumer setUv(float u,float v){current.u=u;current.v=v;return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){current.light=(v<<16)|(u&65535);return this;}
        public VertexConsumer setUv3(float u,float v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
        @Override public VertexConsumer setLineWidth(float width){return this;}
    }
}
