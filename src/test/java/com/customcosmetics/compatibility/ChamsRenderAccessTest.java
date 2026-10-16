package com.customcosmetics.compatibility;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChamsRenderAccessTest {
    public interface ColoredParts {
        void submitModelPart(Object model,Object pose,Object type,int light,int overlay,Object texture,int tint);
    }
    public interface ModernParts {
        void submitModelPart(Object model,Object pose,Object type,int light,int overlay,Object texture,int tint,int outline);
    }
    @Test void coloredPartDefaultsKeepTheirTintAndModernExplicitOutlinesAreRemoved()throws Exception{
        var defaults=ColoredParts.class.getMethods()[0];var colored=new Object[]{null,null,null,1,2,null,0xFFEE99AA};
        assertArrayEquals(colored,ChamsRenderAccess.appearanceArguments(defaults,colored));
        var explicit=ModernParts.class.getMethods()[0];var outlined=new Object[]{null,null,null,1,2,null,0xFFEE99AA,0xFF00FFFF};
        var result=ChamsRenderAccess.appearanceArguments(explicit,outlined);
        assertEquals(0xFFEE99AA,result[6]);assertEquals(0,result[7]);assertEquals(0xFF00FFFF,outlined[7]);
    }
    public interface Appearance {
        void submitModel(Object model,Object state,Object pose,Object type,int light,int overlay,int tint,Object texture,int outline,Object crumbling);
        void submitModelPart(Object model,Object pose,Object type,int light,int overlay,Object texture,int tint,Object crumbling,int outline);
        void submitItem(Object pose,Object context,int light,int overlay,int outline,int[] tint,Object quads,Object foil);
        void submitBlockModel(Object pose,Object type,Object parts,int[] tint,int light,int overlay,int outline);
    }
    @Test void replayOmitsGlowMasksWithoutChangingOriginalTexturesLightingOrTint()throws Exception{
        for(var method:Appearance.class.getMethods()){
            var args=new Object[method.getParameterCount()];
            for(int i=0;i<args.length;i++)args[i]=method.getParameterTypes()[i]==int.class?0x12345678:new Object();
            var original=args.clone();var replay=ChamsRenderAccess.appearanceArguments(method,args);
            int outline=switch(method.getName()){case "submitModel","submitModelPart"->8;case "submitItem"->4;default->6;};
            assertArrayEquals(original,args,"Vanilla submissions must keep their glow masks");
            for(int i=0;i<args.length;i++)assertEquals(i==outline?0:args[i],replay[i],method.getName()+" argument "+i);
        }
    }
    public interface Commands {
        Commands order(int value);
        void submitModel(Object pose,int light);
        void submitItem(Object item);
        void submitNameTag(String text);
        void submitShadow(Object shadow);
    }
    @Test void appearanceAndOrderingAreCopiedWhileTextAndShadowsRemainOrdinary(){
        var original=new ArrayList<String>();var extra=new ArrayList<String>();var values=new ArrayList<Object>();
        var queue=(Commands)ChamsRenderAccess.collector(recorder(original,values),recorder(extra,values),Commands.class);
        var pose=new Object();var item=new Object();
        queue.order(2).submitModel(pose,12345);queue.submitItem(item);queue.submitNameTag("Account");queue.submitShadow(pose);
        assertEquals(List.of("order:2","submitModel:12345","submitItem","submitNameTag","submitShadow"),original);
        assertEquals(List.of("order:2","submitModel:12345","submitItem"),extra);
        assertSame(pose,values.get(0));assertSame(pose,values.get(1));
        assertSame(item,values.get(2));assertSame(item,values.get(3));
    }
    private static Commands recorder(List<String> calls,List<Object> values){
        return (Commands)Proxy.newProxyInstance(Commands.class.getClassLoader(),new Class<?>[]{Commands.class},(proxy,method,args)->{
            calls.add(method.getName()+(method.getName().equals("order")?":"+args[0]:method.getName().equals("submitModel")?":"+args[1]:""));
            if(method.getName().equals("order"))return proxy;
            if(method.getName().equals("submitModel")||method.getName().equals("submitItem"))values.add(args[0]);
            return null;
        });
    }
}
