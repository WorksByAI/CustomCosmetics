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
    public static void drawModel(GuiGraphicsExtractor graphics,int x,int y,int width,int height){draw(graphics,x,y,width,height,PreviewStyle.MODEL,true,WardrobeCamera.DEFAULT);}
    private static void draw(GuiGraphicsExtractor graphics,int x,int y,int width,int height,boolean capeOnly,boolean front){
        draw(graphics,x,y,width,height,capeOnly,front,WardrobeCamera.DEFAULT);
    }
    private static void draw(GuiGraphicsExtractor graphics,int x,int y,int width,int height,boolean capeOnly,boolean front,WardrobeCamera camera){
        draw(graphics,x,y,width,height,capeOnly?PreviewStyle.CAPE:PreviewStyle.EQUIPMENT,front,camera);
    }
    private static void draw(GuiGraphicsExtractor graphics,int x,int y,int width,int height,PreviewStyle style,boolean front,WardrobeCamera camera){
        boolean capeOnly=style==PreviewStyle.CAPE;
        var client=Minecraft.getInstance();if(client.player==null||width<1||height<1)return;
        if(!com.customcosmetics.client.ModErrors.active(com.customcosmetics.client.ModErrors.Key.PREVIEW)){unavailable(graphics,x,y,width);return;}
        CapeCosmetics.beginPreview();try{
            var dispatcher=client.getEntityRenderDispatcher();
            if(extract==null)extract=java.util.Arrays.stream(dispatcher.getClass().getMethods()).filter(m->m.getName().equals("extractEntity")&&m.getParameterCount()==2).findFirst().orElseThrow();
            Object extracted;
            if(style==PreviewStyle.MODEL)extracted=extract.invoke(dispatcher,client.player,1f);
            else{
                com.customcosmetics.features.creature.CreatureModels.beginAvatarPreview();
                try{extracted=extract.invoke(dispatcher,client.player,1f);}
                finally{com.customcosmetics.features.creature.CreatureModels.endAvatarPreview();}
            }
            if(!(extracted instanceof net.minecraft.client.renderer.entity.state.EntityRenderState state)||style!=PreviewStyle.MODEL&&!(state instanceof AvatarRenderState)){
                com.customcosmetics.client.ModErrors.report(com.customcosmetics.client.ModErrors.Key.PREVIEW,new IllegalStateException("Unexpected player preview state: "+(extracted==null?"null":extracted.getClass().getName())));
                unavailable(graphics,x,y,width);return;
            }
            if(state instanceof AvatarRenderState avatar)prepareState(avatar,style);
            state.isInvisible=false;
            if(state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living){living.isInvisibleToPlayer=false;living.bodyRot=living.yRot=living.xRot=0;}
            prepareNames(state,client.player);
            if(entity==null)entity=java.util.Arrays.stream(graphics.getClass().getMethods()).filter(m->m.getName().equals("entity")&&m.getParameterCount()==9).findFirst().orElseThrow();
            double framing=3.1;
            if(style==PreviewStyle.EQUIPMENT){var settings=((com.customcosmetics.features.accessories.AccessoryRenderState)state).customcosmetics$accessories();double size=1;for(var slot:com.customcosmetics.features.accessories.AccessoryCatalog.slots()){var part=settings.part(slot.id());if(part.enabled())size=Math.max(size,part.scale());}var proportions=com.customcosmetics.features.playersize.PlayerSize.settings(((AvatarRenderState)state).id);framing=3.8*size*Math.max(1,Math.max(proportions.x(),Math.max(proportions.y(),proportions.z())));}
            var rotation=new Quaternionf().rotationZ((float)Math.PI).rotateY((front?(float)Math.PI:0)+(capeOnly||style==PreviewStyle.MODEL?0:(float)Math.toRadians(camera.yaw())));
            if(!capeOnly&&style!=PreviewStyle.MODEL)rotation.rotateX((float)Math.toRadians(camera.pitch()));
            double scale=Math.max(style==PreviewStyle.MODEL?1:capeOnly?12:6,height/framing);
            if(style==PreviewStyle.MODEL){
                var model=com.customcosmetics.features.creature.CreatureModels.previewEntity(client.player);
                double visualWidth=model.getBbWidth(),visualHeight=model.getBbHeight();
                if(state instanceof AvatarRenderState avatar){
                    var proportions=com.customcosmetics.features.playersize.PlayerSize.settings(avatar.id);
                    boolean smol=com.customcosmetics.features.smolpeople.SmolPlayers.shouldApplySmolTo(avatar.id);
                    visualWidth=(smol?.6:1.05)*proportions.x();
                    visualHeight=(smol?1.15:model.getBbHeight())*proportions.y();
                }
                // Native name components use the active game font at a readable screen size.
                // They must not force the body to shrink, or render a duplicate 3-D nametag.
                var name=state.nameTag;var score=state.scoreText;state.nameTag=null;state.scoreText=null;state.nameTagAttachment=null;
                boolean hasScore=score!=null&&!score.getString().isEmpty();
                int nameRows=(name==null?0:1)+(hasScore?1:0),nameHeight=nameRows*12+6;
                if(name!=null)modelName(graphics,client.font,name,x,y+3,width);
                if(hasScore)modelName(graphics,client.font,score,x,y+3+(name==null?0:12),width);
                int bodyY=y+nameHeight,bodyHeight=Math.max(1,height-nameHeight);
                scale=PreviewModelFit.scale(width,bodyHeight,visualWidth,visualHeight);
                entity.invoke(graphics,state,(float)scale,new Vector3f(0,(float)(visualHeight/2),0),rotation,null,x,bodyY,x+width,bodyY+bodyHeight);
            }else entity.invoke(graphics,state,(float)scale*(capeOnly?1:camera.zoom()),new Vector3f(0,capeOnly?1:1.15f,0),rotation,null,x,y,x+width,y+height);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError|AssertionError error){
            Throwable cause=error instanceof java.lang.reflect.InvocationTargetException wrapped&&wrapped.getCause()!=null?wrapped.getCause():error;
            com.customcosmetics.client.ModErrors.report(com.customcosmetics.client.ModErrors.Key.PREVIEW,cause);unavailable(graphics,x,y,width);
        }
        finally{CapeCosmetics.endPreview();}
    }
    private static void modelName(GuiGraphicsExtractor g,net.minecraft.client.gui.Font font,net.minecraft.network.chat.Component name,int x,int y,int width){
        int textWidth=font.width(name);float scale=Math.min(1,Math.max(1,width-8)/(float)Math.max(1,textWidth));
        g.pose().pushMatrix();
        try{g.pose().translate(x+width/2f,y);g.pose().scale(scale,scale);g.fill(-textWidth/2-2,-1,(textWidth+1)/2+2,10,0x50000000);g.text(font,name,-textWidth/2,0,0xFFFFFFFF,false);}
        finally{g.pose().popMatrix();}
    }
    private static void unavailable(GuiGraphicsExtractor g,int x,int y,int width){PinkUi.uiText(g,UiFonts.get(Minecraft.getInstance().font),"Player preview unavailable",x+6,y+12,PinkUi.MUTED,Math.max(1,width-12));}
    /** Neutral preview only; never clear equipment on the actual player. */
    static void prepareState(AvatarRenderState state){
        prepareState(state,true);
    }
    static void prepareState(AvatarRenderState state,boolean capeOnly){
        prepareState(state,capeOnly?PreviewStyle.CAPE:PreviewStyle.EQUIPMENT);
    }
    static void prepareNames(net.minecraft.client.renderer.entity.state.EntityRenderState state,net.minecraft.client.player.AbstractClientPlayer player){
        if(state.nameTag==null)state.nameTag=player.getDisplayName();
        if(state.scoreText==null)state.scoreText=player.belowNameDisplay();
        if(state.nameTagAttachment==null)state.nameTagAttachment=player.getAttachments().getNullable(net.minecraft.world.entity.EntityAttachment.NAME_TAG,0,0);
    }
    private static void prepareState(AvatarRenderState state,PreviewStyle style){
        ((com.customcosmetics.features.accessories.AccessoryRenderState)state).customcosmetics$hideAccessories(style==PreviewStyle.CAPE);
        state.bodyRot=state.yRot=state.xRot=state.walkAnimationSpeed=state.walkAnimationPos=0;
        state.capeFlap=state.capeLean=state.capeLean2=state.swimAmount=0;
        com.customcosmetics.compatibility.CapePreviewSwing.clear(state);
        if(style==PreviewStyle.EQUIPMENT)state.showCape=false;
        else if(style==PreviewStyle.CAPE)state.showCape=true;
        state.pose=net.minecraft.world.entity.Pose.STANDING;
        state.isInvisible=state.isInvisibleToPlayer=false;
        state.isCrouching=state.isFallFlying=state.isVisuallySwimming=state.isPassenger=state.isUsingItem=state.isAutoSpinAttack=false;
        if(style!=PreviewStyle.MODEL){
            state.rightHandItemState.clear();state.leftHandItemState.clear();state.heldOnHead.clear();
            state.rightHandItemStack=state.leftHandItemStack=net.minecraft.world.item.ItemStack.EMPTY;
            state.rightArmPose=state.leftArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        }
        if(style==PreviewStyle.CAPE){
            state.headItem.clear();state.wornHeadType=null;state.wornHeadProfile=null;
            state.headEquipment=state.chestEquipment=state.legsEquipment=state.feetEquipment=net.minecraft.world.item.ItemStack.EMPTY;
        }
        state.parrotOnLeftShoulder=state.parrotOnRightShoulder=null;state.arrowCount=state.stingerCount=0;
    }
}
