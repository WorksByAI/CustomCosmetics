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
        draw(graphics,x,y,width,height,true,false);
    }
    public static void drawAccessories(GuiGraphicsExtractor graphics,int x,int y,int width,int height,boolean front){
        drawAccessories(graphics,x,y,width,height,front,WardrobeCamera.DEFAULT);
    }
    public static void drawAccessories(GuiGraphicsExtractor graphics,int x,int y,int width,int height,boolean front,WardrobeCamera camera){draw(graphics,x,y,width,height,false,front,camera);}
    private static void draw(GuiGraphicsExtractor graphics,int x,int y,int width,int height,boolean capeOnly,boolean front){
        draw(graphics,x,y,width,height,capeOnly,front,WardrobeCamera.DEFAULT);
    }
    private static void draw(GuiGraphicsExtractor graphics,int x,int y,int width,int height,boolean capeOnly,boolean front,WardrobeCamera camera){
        var client=Minecraft.getInstance();if(client.player==null||width<1||height<1)return;
        if(!com.customcosmetics.client.ModErrors.active(com.customcosmetics.client.ModErrors.Key.PREVIEW)){unavailable(graphics,x,y,width);return;}
        CapeCosmetics.beginPreview();try{
            var dispatcher=client.getEntityRenderDispatcher();
            if(extract==null)extract=java.util.Arrays.stream(dispatcher.getClass().getMethods()).filter(m->m.getName().equals("extractEntity")&&m.getParameterCount()==2).findFirst().orElseThrow();
            Object extracted;
            com.customcosmetics.features.creature.CreatureModels.beginAvatarPreview();
            try{extracted=extract.invoke(dispatcher,client.player,1f);}
            finally{com.customcosmetics.features.creature.CreatureModels.endAvatarPreview();}
            if(!(extracted instanceof AvatarRenderState state)){
                com.customcosmetics.client.ModErrors.report(com.customcosmetics.client.ModErrors.Key.PREVIEW,new IllegalStateException("Unexpected player preview state: "+(extracted==null?"null":extracted.getClass().getName())));
                unavailable(graphics,x,y,width);return;
            }
            prepareState(state,capeOnly);
            ((com.customcosmetics.features.accessories.AccessoryRenderState)state).customcosmetics$hideAccessories(capeOnly);
            if(!capeOnly)state.showCape=false;
            if(entity==null)entity=java.util.Arrays.stream(graphics.getClass().getMethods()).filter(m->m.getName().equals("entity")&&m.getParameterCount()==9).findFirst().orElseThrow();
            double framing=2.2;
            if(!capeOnly){var settings=((com.customcosmetics.features.accessories.AccessoryRenderState)state).customcosmetics$accessories();double size=1;for(var slot:com.customcosmetics.features.accessories.AccessoryCatalog.slots()){var part=settings.part(slot.id());if(part.enabled())size=Math.max(size,part.scale());}var proportions=com.customcosmetics.features.playersize.PlayerSize.settings(state.id);framing=3.8*size*Math.max(1,Math.max(proportions.x(),Math.max(proportions.y(),proportions.z())));}
            var rotation=new Quaternionf().rotationZ((float)Math.PI).rotateY((front?(float)Math.PI:0)+(capeOnly?0:(float)Math.toRadians(camera.yaw())));
            if(!capeOnly)rotation.rotateX((float)Math.toRadians(camera.pitch()));
            entity.invoke(graphics,state,(float)Math.max(capeOnly?12:6,height/framing)*(capeOnly?1:camera.zoom()),new Vector3f(0,capeOnly?1:1.15f,0),rotation,null,x,y,x+width,y+height);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError|AssertionError error){
            Throwable cause=error instanceof java.lang.reflect.InvocationTargetException wrapped&&wrapped.getCause()!=null?wrapped.getCause():error;
            com.customcosmetics.client.ModErrors.report(com.customcosmetics.client.ModErrors.Key.PREVIEW,cause);unavailable(graphics,x,y,width);
        }
        finally{CapeCosmetics.endPreview();}
    }
    private static void unavailable(GuiGraphicsExtractor g,int x,int y,int width){PinkUi.uiText(g,UiFonts.get(Minecraft.getInstance().font),"Player preview unavailable",x+6,y+12,PinkUi.MUTED,Math.max(1,width-12));}
    /** Neutral preview only; never clear equipment on the actual player. */
    static void prepareState(AvatarRenderState state){
        prepareState(state,true);
    }
    static void prepareState(AvatarRenderState state,boolean capeOnly){
        ((com.customcosmetics.features.accessories.AccessoryRenderState)state).customcosmetics$hideAccessories(true);
        state.bodyRot=state.yRot=state.xRot=state.walkAnimationSpeed=state.walkAnimationPos=0;
        state.capeFlap=state.capeLean=state.capeLean2=state.swimAmount=0;
        com.customcosmetics.compatibility.CapePreviewSwing.clear(state);
        state.showCape=true;state.nameTag=null;state.scoreText=null;state.pose=net.minecraft.world.entity.Pose.STANDING;
        state.isInvisible=state.isInvisibleToPlayer=false;
        state.isCrouching=state.isFallFlying=state.isVisuallySwimming=state.isPassenger=state.isUsingItem=state.isAutoSpinAttack=false;
        state.rightHandItemState.clear();state.leftHandItemState.clear();state.heldOnHead.clear();
        state.rightHandItemStack=state.leftHandItemStack=net.minecraft.world.item.ItemStack.EMPTY;
        state.rightArmPose=state.leftArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        if(capeOnly){
            state.headItem.clear();state.wornHeadType=null;state.wornHeadProfile=null;
            state.headEquipment=state.chestEquipment=state.legsEquipment=state.feetEquipment=net.minecraft.world.item.ItemStack.EMPTY;
        }
        state.parrotOnLeftShoulder=state.parrotOnRightShoulder=null;state.arrowCount=state.stingerCount=0;
    }
}
