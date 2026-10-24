// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;
import java.lang.reflect.Method;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/** 26.3 replaces atlas sprites with UV mappings and removes the crumbling argument. */
public final class AccessoryRenderAccess {
    private AccessoryRenderAccess(){}
    private static class Holder {static final MethodHandle SUBMIT=adapt(findSubmit(OrderedSubmitNodeCollector.class));}
    static Method findSubmit(Class<?> type){
        var methods=java.util.Arrays.stream(type.getMethods()).filter(m->m.getName().equals("submitModelPart")).toList();
        return methods.stream().filter(m->m.getParameterCount()==7&&m.getParameterTypes()[6]==int.class).findFirst()
            .orElseGet(()->methods.stream().filter(m->m.getParameterCount()==8&&!m.getParameterTypes()[7].isPrimitive()).findFirst().orElseThrow(()->new IllegalStateException("Missing cosmetic model submission API")));
    }
    public static void validate(Class<?> type){findSubmit(type);}
    public static void submit(OrderedSubmitNodeCollector queue,ModelPart part,PoseStack pose,RenderType type,int light,int overlay,int color){invoke(Holder.SUBMIT,queue,part,pose,type,light,overlay,color);}
    static MethodHandle adapt(Method method){
        try{
            var handle=MethodHandles.lookup().unreflect(method);
            if(method.getParameterCount()==8)handle=MethodHandles.insertArguments(handle,8,(Object)null);
            handle=MethodHandles.insertArguments(handle,6,(Object)null);
            return handle.asType(MethodType.methodType(void.class,Object.class,Object.class,Object.class,Object.class,int.class,int.class,int.class));
        }catch(IllegalAccessException error){throw new IllegalStateException("Cannot access cosmetic model submission API",error);}
    }
    static void invoke(Method method,Object queue,Object part,Object pose,Object type,int light,int overlay,int color){
        invoke(adapt(method),queue,part,pose,type,light,overlay,color);
    }
    static void invoke(MethodHandle handle,Object queue,Object part,Object pose,Object type,int light,int overlay,int color){
        try{handle.invokeExact(queue,part,pose,type,light,overlay,color);}
        catch(Throwable error){throw new IllegalStateException("Cannot submit cosmetic model",error);}
    }
}
