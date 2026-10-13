// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import com.customcosmetics.compatibility.AccessoryRenderAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Original accessories attached to third-person bones, never the first-person hand renderer. */
public final class AccessoryLayer extends RenderLayer<AvatarRenderState,PlayerModel> {
    private static final Identifier WHITE=Identifier.fromNamespaceAndPath("customcosmetics","textures/accessories/materials.png");
    private static final Identifier DETAILED=Identifier.fromNamespaceAndPath("customcosmetics","textures/accessories/detailed-materials.png");
    public AccessoryLayer(RenderLayerParent<AvatarRenderState,PlayerModel> parent){super(parent);}
    @Override public void submit(PoseStack pose,SubmitNodeCollector queue,int light,AvatarRenderState state,float yaw,float pitch){
        var snapshot=(AccessoryRenderState)state;if(snapshot.customcosmetics$hideAccessories()||state.isInvisible)return;
        var settings=snapshot.customcosmetics$accessories();if(!settings.any())return;
        for(var slot:AccessoryCatalog.slots()){
            var p=settings.part(slot.id());if(!p.enabled()||AccessoryPlacement.covered(slot.id(),settings))continue;var style=AccessoryCatalog.style(slot.id(),p.style());var mesh=AccessoryMeshes.get(style);
            var texture=style.collection()==AccessoryCatalog.Collection.DETAILED?DETAILED:WHITE;
            int copies=switch(slot.id()){case "wings","ears","arms","feet","pants"->2;case "particles"->6;default->1;};
            if(slot.id().equals("wings")&&settings.part("tail").enabled())for(int side:new int[]{-1,1}){
                double size=AccessoryPlacement.size("wings",p),x=AccessoryPlacement.wingX(settings,mesh.bounds(),size,s->AccessoryMeshes.get(s).bounds());
                double rear=AccessoryPlacement.wingRear(settings,(AccessoryModelFit.bounds(getParentModel().body).maxZ()+.4+(state.chestEquipment.isEmpty()?0:1.1))/16,s->AccessoryMeshes.get(s).bounds());
                var mount=AccessoryPlacement.wing(mesh.bounds(),size,rear,state.ageInTicks,p.speed(),side);
                if(x>.12){pose.pushPose();getParentModel().body.translateAndRotate(pose);pose.translate(side*.12,.14,mount.z());pose.scale((float)(side*(x-.12)*16),1,1);part(queue,pose,GarmentMeshes.wingRoot(),light,p.accent(),false,WHITE);pose.popPose();}
            }
            for(int copy=0;copy<copies;copy++){
                int side=copy==0?-1:1;pose.pushPose();attach(pose,slot.id(),style,side,copy,state,settings);
                draw(queue,pose,mesh,light,p,slot.id().equals("halo")||slot.id().equals("particles")||slot.id().equals("head")&&style.variant()==5,texture);pose.popPose();
            }
            if(mesh.sleeves()!=null)for(int side:new int[]{-1,1}){pose.pushPose();AccessoryModelFit.garment(pose,side<0?getParentModel().rightArm:getParentModel().leftArm,p.scale(),state.chestEquipment.isEmpty()?0:1.1,4.8,12.8,4.8,4);part(queue,pose,mesh.sleeves(),light,p.color(),false,texture);part(queue,pose,mesh.cuffs(),light,p.accent(),false,texture);pose.popPose();}
            if(mesh.skirt()!=null)for(int side:new int[]{-1,1}){pose.pushPose();AccessoryModelFit.garment(pose,side<0?getParentModel().rightLeg:getParentModel().leftLeg,p.scale(),state.legsEquipment.isEmpty()?0:.5,4.8,12.8,5.2,6);part(queue,pose,mesh.skirt(),light,p.color(),false,texture);part(queue,pose,mesh.hem(),light,p.accent(),false,texture);pose.popPose();}
        }
    }
    private void attach(PoseStack pose,String slot,AccessoryCatalog.Style style,int side,int index,AvatarRenderState state,AccessoriesSettings settings){
        var model=getParentModel();var part=settings.part(slot);double size=AccessoryPlacement.size(slot,part);
        var bounds=AccessoryMeshes.get(style).bounds();java.util.function.Function<AccessoryCatalog.Style,AccessoryGeometry.Bounds> geometry=s->AccessoryMeshes.get(s).bounds();
        double skinRear=(AccessoryModelFit.bounds(model.body).maxZ()+.4+(state.chestEquipment.isEmpty()?0:1.1))/16;
        double rear=AccessoryPlacement.rear(settings,skinRear,geometry);boolean scaled=false;
        switch(slot){
            case "wings"->{model.body.translateAndRotate(pose);var mount=AccessoryPlacement.wing(bounds,size,AccessoryPlacement.wingRear(settings,skinRear,geometry),state.ageInTicks,part.speed(),side);pose.translate(side*AccessoryPlacement.wingX(settings,bounds,size,geometry),.14,mount.z());rotate(pose,0,(float)mount.yaw(),0);pose.scale(side,1,1);}
            case "halo"->{model.head.translateAndRotate(pose);double above=AccessoryHeadLayout.haloHeight(settings,!state.headEquipment.isEmpty(),geometry);
                pose.translate(0,-above+Math.sin(state.ageInTicks*.055)*.015,0);}
            case "ears"->{model.head.translateAndRotate(pose);pose.translate(side*AccessoryHeadLayout.earX(settings,!state.headEquipment.isEmpty(),geometry),-AccessoryHeadLayout.earHeight(settings,!state.headEquipment.isEmpty(),geometry),-.04);rotate(pose,0,0,side*.13f);pose.scale(side,1,1);}
            case "hat"->{model.head.translateAndRotate(pose);pose.translate(0,-AccessoryHeadLayout.hatHeight(settings,!state.headEquipment.isEmpty(),geometry),0);}
            case "hair","head"->{model.head.translateAndRotate(pose);pose.translate(0,-.25,0);scale(pose,size*(slot.equals("hair")?(state.headEquipment.isEmpty()?1.04:1.2):(state.headEquipment.isEmpty()?1:1.14)));pose.translate(0,.25,0);scaled=true;}
            case "face"->{model.head.translateAndRotate(pose);scale(pose,Math.max(1,size)*(state.headEquipment.isEmpty()?1.04:1.2));scaled=true;}
            case "outfit"->{AccessoryModelFit.garment(pose,model.body,size,state.chestEquipment.isEmpty()?0:1.1,8.8,12.8,5.2,6);scaled=true;}
            case "arms"->{AccessoryModelFit.garment(pose,side<0?model.rightArm:model.leftArm,size,state.chestEquipment.isEmpty()?0:1.1,4.8,12.8,4.8,4);scaled=true;}
            case "feet","pants"->{AccessoryModelFit.garment(pose,side<0?model.rightLeg:model.leftLeg,size,(slot.equals("feet")?state.feetEquipment:state.legsEquipment).isEmpty()?0:.5,4.8,12.8,4.8,6);scaled=true;}
            case "shoulder"->{model.body.translateAndRotate(pose);double s=size*.8;pose.translate(-.43+Math.min(0,bounds.minX()*s/16),-bounds.maxY()*s/16-AccessoryPlacement.GAP,0);scale(pose,.8);}
            case "pet"->{model.body.translateAndRotate(pose);pose.translate(Math.max(.95,.52-bounds.minX()*size/16),.24+Math.sin(state.ageInTicks*.07)*.04,-.38);rotate(pose,0,(float)Math.sin(state.ageInTicks*.04)*.15f,0);}
            case "particles"->{model.body.translateAndRotate(pose);double angle=state.ageInTicks*.02+index*Math.PI/3;pose.translate(Math.cos(angle)*1.15,1.35+Math.sin(angle*2)*.12,Math.sin(angle)*1.15);rotate(pose,0,-(float)angle,(float)Math.sin(angle)*.2f);}
            case "tail"->{model.body.translateAndRotate(pose);float angle=(float)Math.sin(state.ageInTicks*.07)*.13f;var turned=AccessoryPlacement.rotated(bounds,0,angle,0);pose.translate(0,0,AccessoryPlacement.backOffset(turned,size,AccessoryPlacement.wingRear(settings,skinRear,geometry)));rotate(pose,0,angle,0);}
            case "back"->{model.body.translateAndRotate(pose);pose.translate(0,0,AccessoryPlacement.backOffset(bounds,size,rear));}
            case "neck"->{model.body.translateAndRotate(pose);if(bounds.maxZ()<0){double front=-rear;pose.translate(0,0,Math.min(0,front-AccessoryPlacement.GAP-bounds.maxZ()*size/16));}else {
                double width=AccessoryModelFit.bounds(model.body).maxX()-AccessoryModelFit.bounds(model.body).minX()+.8+Math.max(0,size-1)*.6;
                pose.scale((float)Math.max(1,width/(bounds.maxX()-bounds.minX())),1,(float)Math.max(1,(rear+AccessoryPlacement.GAP)*16/Math.max(1,bounds.maxZ())));scaled=true;
            }}
            default->model.body.translateAndRotate(pose);
        }
        if(!scaled)scale(pose,size);
    }
    private static void scale(PoseStack pose,double scale){pose.scale((float)scale,(float)scale,(float)scale);}
    private static void rotate(PoseStack pose,float x,float y,float z){pose.mulPose(new org.joml.Matrix4f().rotationXYZ(x,y,z));}
    private static void draw(SubmitNodeCollector queue,PoseStack pose,AccessoryMeshes.Mesh mesh,int light,AccessoriesSettings.Part p,boolean glow,Identifier texture){
        part(queue,pose,mesh.primary(),light,p.color(),false,texture);part(queue,pose,mesh.accent(),glow?0xF000F0:light,p.accent(),glow,texture);
        if(mesh.details()!=null)part(queue,pose,mesh.details(),light,0x302D3B,false,texture);
        if(mesh.highlights()!=null)part(queue,pose,mesh.highlights(),light,0xFFF5FC,false,texture);
    }
    private static void part(SubmitNodeCollector queue,PoseStack pose,ModelPart part,int light,int color,boolean glow,Identifier texture){
        AccessoryRenderAccess.submit(queue,part,pose,glow?RenderTypes.entityTranslucentEmissive(texture):RenderTypes.entityCutout(texture),light,OverlayTexture.NO_OVERLAY,(glow?0xC0000000:0xFF000000)|color);
    }
}
