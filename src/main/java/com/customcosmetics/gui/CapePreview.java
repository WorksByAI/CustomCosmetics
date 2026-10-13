// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.customcosmetics.features.cape.CapeCosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import java.lang.reflect.Method;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Back view of the real player model. The preview never mutates the player or inventory. */
public final class CapePreview {
    private static Method extract,entity;
    private CapePreview(){}
    public static void draw(GuiGraphicsExtractor graphics,int x,int y,int width,int height){
        var client=Minecraft.getInstance();if(client.player==null||width<1||height<1)return;
        CapeCosmetics.beginPreview();try{
            var dispatcher=client.getEntityRenderDispatcher();
            if(extract==null)extract=java.util.Arrays.stream(dispatcher.getClass().getMethods()).filter(m->m.getName().equals("extractEntity")&&m.getParameterCount()==2).findFirst().orElseThrow();
            var state=(AvatarRenderState)extract.invoke(dispatcher,client.player,1f);
            state.bodyRot=0;state.yRot=0;state.xRot=0;state.walkAnimationSpeed=0;state.capeFlap=0;state.capeLean=0;state.capeLean2=0;state.showCape=true;
            state.chestEquipment=net.minecraft.world.item.ItemStack.EMPTY;state.nameTag=null;
            if(entity==null)entity=java.util.Arrays.stream(graphics.getClass().getMethods()).filter(m->m.getName().equals("entity")&&m.getParameterCount()==9).findFirst().orElseThrow();
            entity.invoke(graphics,state,(float)Math.max(20,height/2.2),new Vector3f(0,1,0),new Quaternionf().rotationZ((float)Math.PI),null,x,y,x+width,y+height);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot render cape preview",error);}
        finally{CapeCosmetics.endPreview();}
    }
}
