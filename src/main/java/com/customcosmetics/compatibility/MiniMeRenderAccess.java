// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import java.lang.reflect.Method;

/** Submit the model plus its own snapshot, so deferred rendering cannot reuse another player's pose. */
public final class MiniMeRenderAccess {
    private MiniMeRenderAccess(){}
    private static class Holder {static final Method METHOD=find(OrderedSubmitNodeCollector.class);}
    static Method find(Class<?> type){
        return java.util.Arrays.stream(type.getMethods()).filter(m->m.getName().equals("submitModel")
            &&(m.getParameterCount()==7||m.getParameterCount()==8)&&m.getParameterTypes()[3].getSimpleName().equals("RenderType"))
            .findFirst().orElseThrow(()->new IllegalStateException("Missing companion model submission API"));
    }
    public static void submit(OrderedSubmitNodeCollector queue,PlayerModel model,AvatarRenderState state,PoseStack pose,Identifier texture,int light,int overlay){
        var method=Holder.METHOD;
        try{
            if(method.getParameterCount()==7)method.invoke(queue,model,state,pose,RenderTypes.entityTranslucent(texture),light,overlay,0);
            else method.invoke(queue,model,state,pose,RenderTypes.entityTranslucent(texture),light,overlay,0,null);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot submit Mini-Me companion",error);}
    }
}
