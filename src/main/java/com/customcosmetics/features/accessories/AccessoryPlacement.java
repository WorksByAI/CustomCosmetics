// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import java.util.function.Function;
import static com.customcosmetics.features.accessories.AccessoryGeometry.*;

/** Cosmetic-space clearances in blocks. Never changes a player position or hitbox. */
public final class AccessoryPlacement {
    public static final double GAP=.025;
    private AccessoryPlacement(){}
    public record Wing(double x,double z,double yaw){}
    public static boolean fitted(String slot){return switch(slot){case "head","hair","outfit","arms","feet","pants"->true;default->false;};}
    public static double size(String slot,AccessoriesSettings.Part part){return fitted(slot)?Math.max(1,part.scale()):part.scale();}
    public static boolean covered(String slot,AccessoriesSettings settings){return settings.part("head").enabled()&&(slot.equals("hair")||slot.equals("face"));}
    public record Fit(double x,double y,double z,double centerX,double centerY,double centerZ,double originY){}
    public static Fit garment(Bounds b,double size,double armor,double width,double height,double depth,double originY,boolean slim){
        double padding=.8+Math.max(0,size-1)*.6+armor*2;
        return new Fit(slim?(b.maxX()-b.minX()+padding)/width:Math.max(1,(b.maxX()-b.minX()+padding)/width),
            Math.max(1,(b.maxY()-b.minY()+.8)/height),Math.max(1,(b.maxZ()-b.minZ()+padding)/depth),
            (b.minX()+b.maxX())/32,(b.minY()+b.maxY())/32,(b.minZ()+b.maxZ())/32,originY/16);
    }
    public static Wing wing(Bounds b,double size,double rear,double phase,double speed,int side){
        double angle=.36+(speed==0?0:Math.sin(phase*.075*speed)*.17);
        double closest=(Math.sin(angle)*b.minX()+Math.cos(angle)*b.minZ())*size/16;
        return new Wing(side*.12,rear+GAP-closest,-side*angle);
    }
    public static double backOffset(Bounds b,double size,double rear){return rear+GAP-b.minZ()*size/16;}
    public static double wingX(AccessoriesSettings settings,Bounds wing,double size,Function<AccessoryCatalog.Style,Bounds> geometry){
        var tail=settings.part("tail");if(!tail.enabled())return .12;
        var b=geometry.apply(AccessoryCatalog.style("tail",tail.style()));double extent=0;
        for(float angle:new float[]{-.13f,.13f}){var rotated=rotated(b,0,angle,0);extent=Math.max(extent,Math.max(Math.abs(rotated.minX()),Math.abs(rotated.maxX()))*tail.scale()/16);}
        double inner=(Math.cos(.53)*wing.minX()-Math.sin(.53)*wing.maxZ())*size/16;
        return Math.max(.12,extent+GAP-inner);
    }
    public static double rear(AccessoriesSettings settings,double skinRear,Function<AccessoryCatalog.Style,Bounds> geometry){
        double rear=skinRear;var outfit=settings.part("outfit");if(outfit.enabled()){double fit=Math.max(1,(skinRear*32+Math.max(0,outfit.scale()-1)*.6)/5.2);rear=Math.max(rear,geometry.apply(AccessoryCatalog.style("outfit",outfit.style())).maxZ()*fit/16);}
        return rear;
    }
    public static double wingRear(AccessoriesSettings settings,double skinRear,Function<AccessoryCatalog.Style,Bounds> geometry){double rear=rear(settings,skinRear,geometry);var back=settings.part("back");if(back.enabled()){var b=geometry.apply(AccessoryCatalog.style("back",back.style()));rear=backOffset(b,back.scale(),rear)+b.maxZ()*back.scale()/16;}return rear;}
    public static Bounds rotated(Bounds b,float x,float y,float z){float minX=Float.MAX_VALUE,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;for(float px:new float[]{b.minX(),b.maxX()})for(float py:new float[]{b.minY(),b.maxY()})for(float pz:new float[]{b.minZ(),b.maxZ()}){var p=new Point(px,py,pz).rotate(x,y,z);minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());minZ=Math.min(minZ,p.z());maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());maxZ=Math.max(maxZ,p.z());}return new Bounds(minX,minY,minZ,maxX,maxY,maxZ);}
}
