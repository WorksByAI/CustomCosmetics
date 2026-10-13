// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;

import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

/** Bake immutable original surfaces into the vanilla model submission path. */
final class AccessoryModelBake {
    private AccessoryModelBake(){}
    static ModelPart bake(AccessoryGeometry.Builder builder){
        var faces=builder.faces();var cubes=new ArrayList<ModelPart.Cube>();
        for(int start=0;start<faces.size();start+=6){
            var group=faces.subList(start,Math.min(start+6,faces.size()));float minX=Float.MAX_VALUE,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;
            for(var face:group)for(var p:face.points()){minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());minZ=Math.min(minZ,p.z());maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());maxZ=Math.max(maxZ,p.z());}
            var cube=new ModelPart.Cube(0,0,minX,minY,minZ,Math.max(.001f,maxX-minX),Math.max(.001f,maxY-minY),Math.max(.001f,maxZ-minZ),0,0,0,false,64,64,EnumSet.allOf(Direction.class));
            for(int i=0;i<6;i++){
                var f=group.get(Math.min(i,group.size()-1));var points=f.points();var vertices=new ModelPart.Vertex[4];var uv=AccessoryMaterialUv.region(f);
                float textureSize=f.material()>=16?256:64;
                for(int j=0;j<4;j++){var p=points.get(i<group.size()?j:0);vertices[j]=new ModelPart.Vertex(p.x(),p.y(),p.z(),(uv.u()+(j==1||j==2?uv.width():0))/textureSize,(uv.v()+(j>=2?uv.height():0))/textureSize);}
                var n=f.normal();cube.polygons[i]=new ModelPart.Polygon(vertices,new Vector3f(n.x(),n.y(),n.z()));
            }
            cubes.add(cube);
        }
        return new ModelPart(List.copyOf(cubes),Map.of());
    }
}
