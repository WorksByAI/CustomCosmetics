// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import com.customcosmetics.features.nickname.GradientGlyph;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.system.MemoryUtil;

/** Optional Sodium bulk-vertex input, including the VertexFormat package change in Minecraft 26.3. */
public final class SodiumGlyphWriter implements InvocationHandler {
    private static final Class<?> API=findApi();
    private static final Map<Object,Layout> LAYOUTS=new ConcurrentHashMap<>();
    private final GradientGlyph.GradientVertices vertices;
    private SodiumGlyphWriter(GradientGlyph.GradientVertices vertices){this.vertices=vertices;}
    private static Class<?> findApi(){
        try{
            var api=Class.forName("net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter",false,VertexConsumer.class.getClassLoader());
            if(Arrays.stream(api.getMethods()).noneMatch(m->m.getName().equals("push")&&m.getParameterCount()==4))return null;
            return api;
        }catch(ClassNotFoundException|LinkageError unavailable){return null;}
    }
    public static VertexConsumer wrap(GradientGlyph.GradientVertices vertices){
        return API==null?vertices:(VertexConsumer)Proxy.newProxyInstance(API.getClassLoader(),new Class<?>[]{VertexConsumer.class,API},new SodiumGlyphWriter(vertices));
    }
    @Override public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{
        if(method.getName().equals("canUseIntrinsics"))return true;
        if(method.getName().equals("push")){push(vertices,((Number)args[1]).longValue(),((Number)args[2]).intValue(),args[3]);return null;}
        if(method.getName().equals("equals"))return proxy==args[0];
        if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
        if(method.getName().equals("toString"))return "CustomCosmetics optimized gradient vertices";
        try{var result=method.invoke(vertices,args);return result==vertices?proxy:result;}
        catch(InvocationTargetException problem){throw problem.getCause();}
    }
    private record Attribute(String name,int offset,String format){}
    private record Layout(int stride,List<Attribute> attributes){
        static Layout of(Object format){
            try{
                int stride=((Number)format.getClass().getMethod("getVertexSize").invoke(format)).intValue();
                if(stride<8||stride>256)throw new IllegalArgumentException("Unsupported glyph vertex stride");
                var result=new ArrayList<Attribute>();
                Method legacyName=null;
                try{legacyName=format.getClass().getMethod("getElementName",Class.forName("com.mojang.blaze3d.vertex.VertexFormatElement"));}
                catch(ClassNotFoundException|NoSuchMethodException newer){}
                for(Object element:(List<?>)format.getClass().getMethod("getElements").invoke(format)){
                    String name,type;int offset;
                    if(legacyName!=null){
                        name=(String)legacyName.invoke(format,element);
                        offset=((Number)format.getClass().getMethod("getOffset",element.getClass()).invoke(format,element)).intValue();
                        String scalar=((Enum<?>)element.getClass().getMethod("type").invoke(element)).name();
                        int count=((Number)element.getClass().getMethod("count").invoke(element)).intValue();
                        type=switch(scalar+"/"+count){case "FLOAT/3"->"RGB32_FLOAT";case "FLOAT/2"->"RG32_FLOAT";case "FLOAT/1"->"R32_FLOAT";case "UBYTE/4"->"RGBA8_UNORM";case "SHORT/2"->"RG16_SINT";case "BYTE/3"->"RGB8_SNORM";default->"unsupported";};
                    }else{
                        name=(String)element.getClass().getMethod("name").invoke(element);
                        offset=((Number)element.getClass().getMethod("offset").invoke(element)).intValue();
                        type=((Enum<?>)element.getClass().getMethod("format").invoke(element)).name();
                    }
                    int bytes=switch(name){
                        case "Position"->type.equals("RGB32_FLOAT")?12:type.equals("RG32_FLOAT")?8:0;
                        case "Color"->type.equals("RGBA8_UNORM")?4:0;
                        case "UV0","UV3"->type.equals("RG32_FLOAT")?8:0;
                        case "UV1","UV2"->type.equals("RG16_SINT")?4:0;
                        case "Normal"->type.equals("RGBA8_SNORM")?4:type.equals("RGB8_SNORM")?3:0;
                        case "LineWidth"->type.equals("R32_FLOAT")?4:0;
                        default->0;
                    };
                    if(bytes==0||offset<0||offset+bytes>stride)throw new IllegalArgumentException("Unsupported glyph vertex attribute: "+name+"/"+type);
                    result.add(new Attribute(name,offset,type));
                }
                if(result.stream().noneMatch(a->a.name.equals("Position")))throw new IllegalArgumentException("Glyph vertex format has no position");
                return new Layout(stride,List.copyOf(result));
            }catch(ReflectiveOperationException error){throw new IllegalArgumentException("Cannot read glyph vertex layout",error);}
        }
    }
    /** Source buffers remain untouched; scalar and bulk glyphs use the exact same recoloring implementation. */
    public static void push(GradientGlyph.GradientVertices sink,long address,int count,Object format){
        if(count<0||count>65536||address==0&&count>0)throw new IllegalArgumentException("Invalid glyph vertex buffer");
        var layout=LAYOUTS.computeIfAbsent(format,Layout::of);
        var position=layout.attributes.stream().filter(a->a.name.equals("Position")).findFirst().orElseThrow();
        for(int vertex=0;vertex<count;vertex++){
            long base=address+(long)vertex*layout.stride,p=base+position.offset;
            sink.addBulkVertex(MemoryUtil.memGetFloat(p),MemoryUtil.memGetFloat(p+4),position.format.equals("RGB32_FLOAT")?MemoryUtil.memGetFloat(p+8):0);
            for(var a:layout.attributes){long ptr=base+a.offset;
                switch(a.name){
                    case "Color"->sink.setColor(MemoryUtil.memGetByte(ptr)&255,MemoryUtil.memGetByte(ptr+1)&255,MemoryUtil.memGetByte(ptr+2)&255,MemoryUtil.memGetByte(ptr+3)&255);
                    case "UV0"->sink.setUv(MemoryUtil.memGetFloat(ptr),MemoryUtil.memGetFloat(ptr+4));
                    case "UV1"->sink.setUv1(MemoryUtil.memGetShort(ptr)&65535,MemoryUtil.memGetShort(ptr+2)&65535);
                    case "UV2"->sink.setUv2(MemoryUtil.memGetShort(ptr)&65535,MemoryUtil.memGetShort(ptr+2)&65535);
                    case "UV3"->sink.setUv3(MemoryUtil.memGetFloat(ptr),MemoryUtil.memGetFloat(ptr+4));
                    case "Normal"->sink.setNormal(MemoryUtil.memGetByte(ptr)/127f,MemoryUtil.memGetByte(ptr+1)/127f,MemoryUtil.memGetByte(ptr+2)/127f);
                    case "LineWidth"->sink.setLineWidth(MemoryUtil.memGetFloat(ptr));
                }
            }
        }
        sink.finish();
    }
}
