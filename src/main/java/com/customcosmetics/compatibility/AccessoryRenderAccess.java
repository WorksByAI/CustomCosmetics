// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;
import java.lang.reflect.Method;
import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/** 26.3 replaces atlas sprites with UV mappings and removes the crumbling argument. */
public final class AccessoryRenderAccess {
    private AccessoryRenderAccess(){}
    private static class Holder {static final Method METHOD=findSubmit(OrderedSubmitNodeCollector.class);}
    static Method findSubmit(Class<?> type){
        var methods=java.util.Arrays.stream(type.getMethods()).filter(m->m.getName().equals("submitModelPart")).toList();
        return methods.stream().filter(m->m.getParameterCount()==7&&m.getParameterTypes()[6]==int.class).findFirst()
            .orElseGet(()->methods.stream().filter(m->m.getParameterCount()==8&&!m.getParameterTypes()[7].isPrimitive()).findFirst().orElseThrow(()->new IllegalStateException("Missing cosmetic model submission API")));
    }
    public static void validate(Class<?> type){findSubmit(type);}
    public static void submit(OrderedSubmitNodeCollector queue,ModelPart part,PoseStack pose,RenderType type,int light,int overlay,int color){invoke(Holder.METHOD,queue,part,pose,type,light,overlay,color);}
    static void invoke(Method method,Object queue,Object part,Object pose,Object type,int light,int overlay,int color){
        try{if(method.getParameterCount()==7)method.invoke(queue,part,pose,type,light,overlay,null,color);else method.invoke(queue,part,pose,type,light,overlay,null,color,null);}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot submit cosmetic model",error);}
    }
}
