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
            prepareState(state);
            if(entity==null)entity=java.util.Arrays.stream(graphics.getClass().getMethods()).filter(m->m.getName().equals("entity")&&m.getParameterCount()==9).findFirst().orElseThrow();
            entity.invoke(graphics,state,(float)Math.max(20,height/2.2),new Vector3f(0,1,0),new Quaternionf().rotationZ((float)Math.PI),null,x,y,x+width,y+height);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot render cape preview",error);}
        finally{CapeCosmetics.endPreview();}
    }
    /** Neutral preview only; never clear equipment on the actual player. */
    private static void prepareState(AvatarRenderState state){
        state.bodyRot=state.yRot=state.xRot=state.walkAnimationSpeed=state.walkAnimationPos=0;
        state.capeFlap=state.capeLean=state.capeLean2=state.swimAmount=0;
        com.customcosmetics.compatibility.CapePreviewSwing.clear(state);
        state.showCape=true;state.nameTag=null;state.pose=net.minecraft.world.entity.Pose.STANDING;
        state.isCrouching=state.isFallFlying=state.isVisuallySwimming=state.isPassenger=state.isUsingItem=state.isAutoSpinAttack=false;
        state.rightHandItemState.clear();state.leftHandItemState.clear();state.headItem.clear();state.heldOnHead.clear();
        state.rightHandItemStack=state.leftHandItemStack=net.minecraft.world.item.ItemStack.EMPTY;
        state.rightArmPose=state.leftArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        state.headEquipment=state.chestEquipment=state.legsEquipment=state.feetEquipment=net.minecraft.world.item.ItemStack.EMPTY;
        state.parrotOnLeftShoulder=state.parrotOnRightShoulder=null;state.arrowCount=state.stingerCount=0;
    }
}
