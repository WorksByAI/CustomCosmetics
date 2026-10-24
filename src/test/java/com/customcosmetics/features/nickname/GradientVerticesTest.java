package com.customcosmetics.features.nickname;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import net.minecraft.client.gui.font.TextRenderable;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GradientVerticesTest {
    @Test void nestedAndFailedGlyphDrawsDoNotReuseLiveScratchBuffers(){
        var cyan=new NicknameGlyphEffect(new NicknameGradient("smooth",0x24CFDF,0xEA78C5,1),0,1);
        var red=new NicknameGlyphEffect(new NicknameGradient("smooth",0xAA0000,0xFFCC00,1),0,1);
        var nested=new Capture();var outer=new Capture();
        var inner=glyph((pose,sink)->quad(sink,pose,0xFF24CFDF),red);
        var enclosing=glyph((pose,sink)->{
            sink.addVertex(pose,100,20,0).setColor(0xFF24CFDF).setUv(.2f,.3f).setLight(0xF000F0);
            sink.addVertex(pose,100,28,0).setColor(0xFF24CFDF).setUv(.2f,.7f).setLight(0xF000F0);
            inner.render(pose,nested,0xF000F0,false);
            sink.addVertex(pose,108,28,0).setColor(0xFF24CFDF).setUv(.6f,.7f).setLight(0xF000F0);
            sink.addVertex(pose,108,20,0).setColor(0xFF24CFDF).setUv(.6f,.3f).setLight(0xF000F0);
        },cyan);
        enclosing.render(new Matrix4f(),outer,0xF000F0,false);
        assertEquals(64,outer.vertices.size());assertEquals(64,nested.vertices.size());
        assertEquals(0xFF24CFDF,outer.vertices.getFirst().color);assertEquals(0xFFAA0000,nested.vertices.getFirst().color);
        var failing=glyph((pose,sink)->{
            sink.addVertex(pose,0,0,0).setColor(0xFF123456);sink.addVertex(pose,0,1,0).setColor(0xFF123456);
            throw new IllegalStateException("native glyph failed");
        },cyan);
        assertThrows(IllegalStateException.class,()->failing.render(new Matrix4f(),new Capture(),0,false));
        var after=new Capture();enclosing.render(new Matrix4f(),after,0xF000F0,false);
        assertEquals(outer.vertices.stream().map(v->v.color).toList(),after.vertices.stream().map(v->v.color).toList());
        var deep=new Capture();var chain=glyph((pose,sink)->quad(sink,pose,0xFF24CFDF),cyan);
        for(int i=0;i<6;i++){
            var child=chain;chain=glyph((pose,sink)->{child.render(pose,deep,0,false);quad(sink,pose,0xFF24CFDF);},cyan);
        }
        chain.render(new Matrix4f(),deep,0,false);assertEquals(7*64,deep.vertices.size());
    }
    @Test void reusedVerticesDropOptionalAttributesAndOldTwoDimensionalPoses(){
        var output=new Capture();var effect=new NicknameGlyphEffect(new NicknameGradient("smooth",0x24CFDF,0xEA78C5,1),0,1);
        var vertices=new GradientGlyph.GradientVertices(output,effect,100,20,8,0xFF24CFDF,0);
        for(int i=0;i<4;i++)vertices.addVertexWith2DPose(new org.joml.Matrix3x2f().translate(50,70),i,0)
            .setColor(0xFF000000).setUv(.1f,.2f).setUv1(7,8).setNormal(0,1,0).setLineWidth(3);
        vertices.finish();quad(vertices,new Matrix4f(),0xFF24CFDF);vertices.finish();
        assertTrue(output.vertices.subList(0,4).stream().allMatch(v->v.overlaySet&&v.normalSet&&v.lineSet));
        assertTrue(output.vertices.subList(4,68).stream().noneMatch(v->v.overlaySet||v.normalSet||v.lineSet));
        assertEquals(100,output.vertices.get(4).x);assertEquals(20,output.vertices.get(4).y);
    }
    @Test void bulkPoseSnapshotsAndResetsBeforeReusingTheInverse(){
        var output=new Capture();var expected=new Capture();draw(expected,new Matrix4f(),0xB224CFDF);
        var effect=new NicknameGlyphEffect(new NicknameGradient("smooth",0x24CFDF,0xEA78C5,1),0,1);
        var vertices=new GradientGlyph.GradientVertices(output,effect,100,20,8,0xB224CFDF,0);
        var pose=new Matrix4f().translate(12,7,2);vertices.bulkPose(pose);pose.identity();
        for(var p:new float[][]{{112,27},{112,35},{120,35},{120,27}})vertices.addBulkVertex(p[0],p[1],2).setColor(0xB224CFDF);
        vertices.finish();vertices.bulkPose(null);
        for(var p:new float[][]{{100,20},{100,28},{108,28},{108,20}})vertices.addBulkVertex(p[0],p[1],0).setColor(0xB224CFDF);
        vertices.finish();
        assertEquals(128,output.vertices.size());assertEquals(112,output.vertices.getFirst().x);
        assertEquals(expected.vertices.stream().map(v->v.color).toList(),output.vertices.subList(0,64).stream().map(v->v.color).toList());
        assertEquals(expected.vertices.stream().map(v->v.color).toList(),output.vertices.subList(64,128).stream().map(v->v.color).toList());
    }
    @Test void stripBoundaryReusePreservesExactShimmerAndSelectedShadowColors(){
        for(boolean shadow:new boolean[]{false,true})for(long time:new long[]{0,400,1000,2500}){
            var output=new Capture();var effect=new NicknameGlyphEffect(new NicknameGradient("shimmer",0x24CFDF,0xEA78C5,1),.13,.84);
            var vertices=new GradientGlyph.GradientVertices(output,quad->new GradientGlyph.Sample(effect,100,20,8,shadow),time);
            quad(vertices,new Matrix4f(),0xB224CFDF);vertices.finish();assertEquals(64,output.vertices.size());
            for(var vertex:output.vertices){
                double fraction=(vertex.x-100)/8f;
                int rgb=effect.gradient().color(effect.from()+(effect.to()-effect.from())*fraction,(vertex.y-20)/8.0,time);
                if(shadow)rgb=(rgb&0xFCFCFC)>>>2;
                assertEquals(0xB2000000|rgb,vertex.color);
            }
        }
    }
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
    @FunctionalInterface private interface Draw {void render(Matrix4fc pose,VertexConsumer sink);}
    private static GradientGlyph glyph(Draw draw,NicknameGlyphEffect effect){
        var original=(TextRenderable.Styled)java.lang.reflect.Proxy.newProxyInstance(TextRenderable.Styled.class.getClassLoader(),new Class<?>[]{TextRenderable.Styled.class},(proxy,method,args)->{
            if(method.getName().equals("render")){draw.render((Matrix4fc)args[0],(VertexConsumer)args[1]);return null;}
            throw new AssertionError("Unexpected native glyph method: "+method.getName());
        });
        return new GradientGlyph(original,effect,100,20,8,0xFF24CFDF);
    }
    private static void quad(VertexConsumer sink,Matrix4fc pose,int color){
        sink.addVertex(pose,100,20,0).setColor(color).setUv(.2f,.3f).setLight(0xF000F0);
        sink.addVertex(pose,100,28,0).setColor(color).setUv(.2f,.7f).setLight(0xF000F0);
        sink.addVertex(pose,108,28,0).setColor(color).setUv(.6f,.7f).setLight(0xF000F0);
        sink.addVertex(pose,108,20,0).setColor(color).setUv(.6f,.3f).setLight(0xF000F0);
    }
    private static final class V {float x,y,z,u,v;int color,light;boolean overlaySet,normalSet,lineSet;}
    private static final class Capture implements VertexConsumer {
        final List<V> vertices=new ArrayList<>();V current;
        @Override public VertexConsumer addVertex(float x,float y,float z){current=new V();current.x=x;current.y=y;current.z=z;vertices.add(current);return this;}
        @Override public VertexConsumer setColor(int color){current.color=color;return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return setColor((a<<24)|(r<<16)|(g<<8)|b);}
        @Override public VertexConsumer setUv(float u,float v){current.u=u;current.v=v;return this;}
        @Override public VertexConsumer setUv1(int u,int v){current.overlaySet=true;return this;}
        @Override public VertexConsumer setUv2(int u,int v){current.light=(v<<16)|(u&65535);return this;}
        public VertexConsumer setUv3(float u,float v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){current.normalSet=true;return this;}
        @Override public VertexConsumer setLineWidth(float width){current.lineSet=true;return this;}
    }
}
