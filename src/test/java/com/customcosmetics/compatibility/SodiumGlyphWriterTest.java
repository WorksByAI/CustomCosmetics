// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import com.customcosmetics.features.nickname.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

class SodiumGlyphWriterTest {
    private static final int COLOR=0xB224CFDF;
    private static final float[][] QUAD={{100,20},{100,28},{108,28},{108,20}},UV={{.2f,.3f},{.2f,.7f},{.6f,.7f},{.6f,.3f}};
    private static Object format()throws Exception{
        try{return Class.forName("com.mojang.blaze3d.vertex.DefaultVertexFormat").getField("POSITION_COLOR_TEX_LIGHTMAP").get(null);}
        catch(ClassNotFoundException modern){return Class.forName("com.mojang.renderpearl.api.vertex.DefaultVertexFormat").getField("POSITION_COLOR_TEX_LIGHTMAP").get(null);}
    }
    private static GradientGlyph.GradientVertices wrapper(Capture output,Matrix4f pose){
        var effect=new NicknameGlyphEffect(new NicknameGradient("smooth",0x24CFDF,0xEA78C5,1),0,1);
        return new GradientGlyph.GradientVertices(output,effect,100,20,8,COLOR,0).bulkPose(pose);
    }
    private static ByteBuffer buffer(Object format,Matrix4f pose,int color)throws Exception{
        int stride=(int)format.getClass().getMethod("getVertexSize").invoke(format);
        var memory=MemoryUtil.memCalloc(stride*4);long ptr=MemoryUtil.memAddress(memory);
        Method legacyName=null;try{legacyName=format.getClass().getMethod("getElementName",Class.forName("com.mojang.blaze3d.vertex.VertexFormatElement"));}catch(ClassNotFoundException|NoSuchMethodException newer){}
        for(int i=0;i<4;i++)for(Object element:(List<?>)format.getClass().getMethod("getElements").invoke(format)){
            String name=(String)(legacyName==null?element.getClass().getMethod("name").invoke(element):legacyName.invoke(format,element));
            int offset=(int)(legacyName==null?element.getClass().getMethod("offset").invoke(element):format.getClass().getMethod("getOffset",element.getClass()).invoke(format,element));
            long address=ptr+(long)i*stride+offset;
            switch(name){
                case "Position"->{var p=pose.transformPosition(QUAD[i][0],QUAD[i][1],0,new org.joml.Vector3f());MemoryUtil.memPutFloat(address,p.x);MemoryUtil.memPutFloat(address+4,p.y);MemoryUtil.memPutFloat(address+8,p.z);}
                case "Color"->{MemoryUtil.memPutByte(address,(byte)(color>>>16));MemoryUtil.memPutByte(address+1,(byte)(color>>>8));MemoryUtil.memPutByte(address+2,(byte)color);MemoryUtil.memPutByte(address+3,(byte)(color>>>24));}
                case "UV0"->{MemoryUtil.memPutFloat(address,UV[i][0]);MemoryUtil.memPutFloat(address+4,UV[i][1]);}
                case "UV2"->{MemoryUtil.memPutShort(address,(short)240);MemoryUtil.memPutShort(address+2,(short)240);}
            }
        }
        return memory;
    }
    @Test void nativeBatchPreservesGradientAlphaTextureLightingAndWorldGeometry()throws Exception{
        for(var pose:List.of(new Matrix4f(),new Matrix4f().translate(12,7,2).rotateY(.4f).scale(1.2f)))for(int color:new int[]{COLOR,0xB2093337}){
            var scalar=new Capture();var sink=wrapper(scalar,pose);
            for(int i=0;i<4;i++)sink.addVertex(pose,QUAD[i][0],QUAD[i][1],0).setColor(color).setUv(UV[i][0],UV[i][1]).setLight(0x00F000F0);
            sink.finish();var actual=new Capture();Object format=format();var memory=buffer(format,pose,color);
            try{
                byte[] before=new byte[memory.remaining()];memory.duplicate().get(before);
                SodiumGlyphWriter.push(wrapper(actual,pose),MemoryUtil.memAddress(memory),4,format);
                byte[] after=new byte[memory.remaining()];memory.duplicate().get(after);assertArrayEquals(before,after);
                assertEquals(scalar.vertices.size(),actual.vertices.size());
                for(int i=0;i<scalar.vertices.size();i++){
                    var a=scalar.vertices.get(i);var b=actual.vertices.get(i);
                    assertEquals(a.x,b.x,.0001);assertEquals(a.y,b.y,.0001);assertEquals(a.z,b.z,.0001);
                    assertEquals(a.u,b.u,.00001);assertEquals(a.v,b.v,.00001);assertEquals(a.light,b.light);assertEquals(a.color>>>24,b.color>>>24);
                    for(int shift:new int[]{0,8,16})assertEquals(a.color>>>shift&255,b.color>>>shift&255,1);
                }
            }finally{MemoryUtil.memFree(memory);}
        }
    }
    @Test void optionalSodiumRecognizesTheAdapterAndAcceptsNativeGlyphBatches()throws Exception{
        var output=new Capture();var wrapper=wrapper(output,new Matrix4f());VertexConsumer optimized=wrapper.optimized();
        Class<?> api;try{api=Class.forName("net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter");}
        catch(ClassNotFoundException absent){assertFalse(Boolean.getBoolean("customcosmetics.sodiumTestRequired"),"The requested Sodium API test dependency is missing");assertSame(wrapper,optimized);return;}
        assertNotSame(wrapper,optimized);assertSame(optimized,api.getMethod("tryOf",VertexConsumer.class).invoke(null,optimized));
        Object format=format();var memory=buffer(format,new Matrix4f(),COLOR);
        try(var stack=MemoryStack.stackPush()){
            Method push=Arrays.stream(api.getMethods()).filter(m->m.getName().equals("push")).findFirst().orElseThrow();
            push.invoke(optimized,stack,MemoryUtil.memAddress(memory),4,format);
            assertTrue(output.vertices.size()>4);assertTrue(output.vertices.stream().map(v->v.color).distinct().count()>5);
        }finally{MemoryUtil.memFree(memory);}
    }
    @Test void invalidNativeBatchesAreRejectedBeforeReadingMemory()throws Exception{
        var sink=wrapper(new Capture(),new Matrix4f());Object format=format();
        for(int count:new int[]{-1,65537,4})assertThrows(IllegalArgumentException.class,()->SodiumGlyphWriter.push(sink,0,count,format));
    }
    private static final class Vertex {float x,y,z,u,v;int color,light;}
    private static final class Capture implements VertexConsumer {
        final List<Vertex> vertices=new ArrayList<>();Vertex current;
        @Override public VertexConsumer addVertex(float x,float y,float z){current=new Vertex();current.x=x;current.y=y;current.z=z;vertices.add(current);return this;}
        @Override public VertexConsumer setColor(int color){current.color=color;return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return setColor(a<<24|r<<16|g<<8|b);}
        @Override public VertexConsumer setUv(float u,float v){current.u=u;current.v=v;return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){current.light=v<<16|u&65535;return this;}
        public VertexConsumer setUv3(float u,float v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
        @Override public VertexConsumer setLineWidth(float width){return this;}
    }
}
