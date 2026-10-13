// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import java.util.*;
import static com.customcosmetics.features.accessories.AccessoryGeometry.*;

/** Measure the actual bone mesh once, including the vanilla wide/slim arm offset. */
public final class AccessoryModelFit {
    private static final Map<ModelPart,Bounds> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
    private AccessoryModelFit(){}
    public static Bounds bounds(ModelPart part){return CACHE.computeIfAbsent(part,p->{float[] a={Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE};p.visit(new PoseStack(),(pose,path,index,cube)->{if(!path.isEmpty())return;for(var face:cube.polygons)for(var v:face.vertices()){a[0]=Math.min(a[0],v.x());a[1]=Math.min(a[1],v.y());a[2]=Math.min(a[2],v.z());a[3]=Math.max(a[3],v.x());a[4]=Math.max(a[4],v.y());a[5]=Math.max(a[5],v.z());}});return a[0]==Float.MAX_VALUE?new Bounds(-2,-2,-2,2,10,2):new Bounds(a[0],a[1],a[2],a[3],a[4],a[5]);});}
    public static void garment(PoseStack pose,ModelPart bone,double size,double armor,double nominalWidth,double nominalHeight,double nominalDepth,double nominalCenterY){
        var fit=AccessoryPlacement.garment(bounds(bone),size,armor,nominalWidth,nominalHeight,nominalDepth,nominalCenterY,nominalWidth==4.8);
        bone.translateAndRotate(pose);pose.translate(fit.centerX(),fit.centerY(),fit.centerZ());pose.scale((float)fit.x(),(float)fit.y(),(float)fit.z());pose.translate(0,-fit.originY(),0);
    }
}
