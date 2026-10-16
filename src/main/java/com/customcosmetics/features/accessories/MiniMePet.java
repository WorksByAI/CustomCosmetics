// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;

import com.customcosmetics.compatibility.MiniMeRenderAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelType;

/** A skin-only companion, with independent model/state and no recursive player rendering. */
public final class MiniMePet {
    public static final String ID="mini_me";
    private static PlayerModel wide,slim;
    private MiniMePet(){}
    public static boolean selected(AccessoryCatalog.Style style){return style.kind().equals(ID);}
    public static double scale(double size){return .32*Math.clamp(size,.5,2);}
    public record Mount(double x,double y,double z,double scale){}
    public static Mount mount(double size,double age,String side){
        double scale=scale(size);
        return new Mount((side.equals("left")?1:-1)*Math.max(.8,.6+scale*.5),-.04-.75*scale+Math.sin(age*.065)*.02,-.35,scale);
    }
    private static PlayerModel model(boolean narrow){
        var model=narrow?slim:wide;
        if(model==null){
            model=new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,narrow),64,64).bakeRoot(),narrow);
            if(narrow)slim=model;else wide=model;
        }return model;
    }
    public static AvatarRenderState snapshot(AvatarRenderState owner){
        var mini=new AvatarRenderState();mini.id=Integer.MIN_VALUE;mini.skin=owner.skin;
        mini.ageInTicks=owner.ageInTicks;mini.isPassenger=true;mini.speedValue=1;
        mini.yRot=Math.clamp(owner.yRot,-60,60);mini.xRot=Math.clamp(owner.xRot,-35,35);
        mini.showHat=owner.showHat;mini.showJacket=owner.showJacket;
        mini.showLeftPants=owner.showLeftPants;mini.showRightPants=owner.showRightPants;
        mini.showLeftSleeve=owner.showLeftSleeve;mini.showRightSleeve=owner.showRightSleeve;
        return mini;
    }
    public static void submit(SubmitNodeCollector queue,PoseStack pose,int light,AvatarRenderState owner,AccessoriesSettings.Part part){
        if(owner.skin==null||owner.skin.body()==null)return;
        var mini=snapshot(owner);var mount=mount(part.scale(),owner.ageInTicks,part.side());
        pose.pushPose();
        try{
            AccessoryLayer.offset(pose,part);
            pose.translate(mount.x(),mount.y(),mount.z());float scale=(float)mount.scale();pose.scale(scale,scale,scale);
            MiniMeRenderAccess.submit(queue,model(owner.skin.model()==PlayerModelType.SLIM),mini,pose,owner.skin.body().texturePath(),light,OverlayTexture.NO_OVERLAY);
        }finally{pose.popPose();}
    }
}
