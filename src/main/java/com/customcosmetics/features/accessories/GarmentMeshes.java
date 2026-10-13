// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import static com.customcosmetics.features.accessories.AccessoryGeometry.*;
/** Split robe tails follow each leg instead of leaving a rigid box across a walking player. */
final class GarmentMeshes {
    private GarmentMeshes(){}
    private static net.minecraft.client.model.geom.ModelPart root;
    static net.minecraft.client.model.geom.ModelPart wingRoot(){if(root==null){var b=new Builder();b.box(1,.6f,.6f,0,8);var positioned=new Builder();positioned.add(b,new Point(.5f,0,0),0,0,0);root=AccessoryModelBake.bake(positioned);}return root;}
    static net.minecraft.client.model.geom.ModelPart[] skirt(boolean detailed){var main=new Builder();var trim=new Builder();int material=detailed?16:0;float bevel=detailed?.1f:0;
        var box=new Builder();box.box(5.05f,5.9f,5.4f,bevel,material);main.add(box,new Point(0,2.75f,0),0,0,0);
        box=new Builder();box.box(5.15f,.5f,5.5f,bevel,material);trim.add(box,new Point(0,5.55f,0),0,0,0);
        return new net.minecraft.client.model.geom.ModelPart[]{AccessoryModelBake.bake(main),AccessoryModelBake.bake(trim)};
    }
}
