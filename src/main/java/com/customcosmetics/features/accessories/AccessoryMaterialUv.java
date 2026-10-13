// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;

/** Keep texture pixels near model-pixel size, even on small voxel faces. */
public record AccessoryMaterialUv(float u,float v,float width,float height) {
    public static AccessoryMaterialUv region(AccessoryGeometry.Face face){
        int tile=Math.floorMod(face.material(),16);var n=face.normal();var a=face.a();
        float width=Math.min(15,face.b().minus(a).length()),height=Math.min(15,face.d().minus(a).length());
        float x=Math.abs(n.x())>.8f?a.z():a.x(),y=Math.abs(n.y())>.8f?a.z():a.y();
        float dx=Math.min(15-width,Math.floorMod((int)Math.floor(x),16)),dy=Math.min(15-height,Math.floorMod((int)Math.floor(y),16));
        float density=face.material()>=16?4:1;
        return new AccessoryMaterialUv((tile%4*16+.5f+dx)*density,(tile/4*16+.5f+dy)*density,width*density,height*density);
    }
}
