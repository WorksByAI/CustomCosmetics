// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4fc;
/** Solid quads and small edge prisms avoid version-dependent line shader formats. */
public final class PartyGeometry{
    private PartyGeometry(){}
    public static void box(VertexConsumer v,Matrix4fc pose,double x0,double y0,double z0,double x1,double y1,double z1,int color){
        double[][] points={{x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}};
        int[][] faces={{0,1,2,3},{5,4,7,6},{4,0,3,7},{1,5,6,2},{3,2,6,7},{4,5,1,0}};
        for(var face:faces)for(int index:face){var p=points[index];v.addVertex(pose,(float)p[0],(float)p[1],(float)p[2]).setColor(color);}
    }
    public static void outline(VertexConsumer v,Matrix4fc pose,double x0,double y0,double z0,double x1,double y1,double z1,double t,int color){
        for(double x:new double[]{x0,x1})for(double y:new double[]{y0,y1})box(v,pose,x-t,y-t,z0-t,x+t,y+t,z1+t,color);
        for(double x:new double[]{x0,x1})for(double z:new double[]{z0,z1})box(v,pose,x-t,y0-t,z-t,x+t,y1+t,z+t,color);
        for(double y:new double[]{y0,y1})for(double z:new double[]{z0,z1})box(v,pose,x0-t,y-t,z-t,x1+t,y+t,z+t,color);
    }
}
