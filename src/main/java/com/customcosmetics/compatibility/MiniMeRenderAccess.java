// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import java.lang.reflect.Method;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Submit the model plus its own snapshot, so deferred rendering cannot reuse another player's pose. */
public final class MiniMeRenderAccess {
    private MiniMeRenderAccess(){}
    private static class Holder {static final MethodHandle SUBMIT=adapt(find(OrderedSubmitNodeCollector.class));}
    static Method find(Class<?> type){
        return java.util.Arrays.stream(type.getMethods()).filter(m->m.getName().equals("submitModel")
            &&(m.getParameterCount()==7||m.getParameterCount()==8)&&m.getParameterTypes()[3].getSimpleName().equals("RenderType"))
            .findFirst().orElseThrow(()->new IllegalStateException("Missing companion model submission API"));
    }
    public static void submit(OrderedSubmitNodeCollector queue,PlayerModel model,AvatarRenderState state,PoseStack pose,Identifier texture,int light,int overlay){
        submit(Holder.SUBMIT,queue,model,state,pose,RenderTypes.entityTranslucent(texture),light,overlay);
    }
    static MethodHandle adapt(Method method){
        try{
            var handle=MethodHandles.lookup().unreflect(method);
            if(method.getParameterCount()==8)handle=MethodHandles.insertArguments(handle,8,(Object)null);
            handle=MethodHandles.insertArguments(handle,7,0);
            return handle.asType(MethodType.methodType(void.class,Object.class,Object.class,Object.class,Object.class,Object.class,int.class,int.class));
        }catch(IllegalAccessException error){throw new IllegalStateException("Cannot access Mini-Me submission API",error);}
    }
    static void submit(MethodHandle handle,Object queue,Object model,Object state,Object pose,Object type,int light,int overlay){
        try{handle.invokeExact(queue,model,state,pose,type,light,overlay);}
        catch(Throwable error){throw new IllegalStateException("Cannot submit Mini-Me companion",error);}
    }
}
