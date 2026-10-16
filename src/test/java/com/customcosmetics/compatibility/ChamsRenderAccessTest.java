package com.customcosmetics.compatibility;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChamsRenderAccessTest {
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
