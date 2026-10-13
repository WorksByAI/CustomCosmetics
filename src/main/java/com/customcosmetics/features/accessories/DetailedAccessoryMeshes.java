// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;

import java.util.*;
import static com.customcosmetics.features.accessories.AccessoryGeometry.*;

/** Original faceted designs with layered silhouettes, sculpted seams and separate inlays. */
final class DetailedAccessoryMeshes {
    private DetailedAccessoryMeshes(){}
    private static final class Shape {
        final Builder body=new Builder(),trim=new Builder(),ink=new Builder(),light=new Builder();
        void box(boolean accent,float x,float y,float z,float w,float h,float d){box(accent,x,y,z,w,h,d,0,0,0);}
        void box(boolean accent,float x,float y,float z,float w,float h,float d,float rx,float ry,float rz){var b=new Builder();b.box(w,h,d,Math.min(.22f,Math.min(w,Math.min(h,d))*.2f),16);(accent?trim:body).add(b,new Point(x,y,z),rx,ry,rz);}
        void loft(boolean accent,List<Point> path,float[] widths,float[] depths,int material){(accent?trim:body).loft(path,widths,depths,8,16+material);}
        void panel(boolean accent,List<Point> points,float thickness,int material){(accent?trim:body).panel(points,thickness,16+material);}
        void tube(boolean accent,List<Point> points,float width,int material){var sizes=new float[points.size()];Arrays.fill(sizes,width);loft(accent,points,sizes,sizes,material);}
        void ring(boolean accent,float radius,float y,float thickness){(accent?trim:body).torus(radius,thickness,y,24);}
        void gem(boolean accent,float x,float y,float z,float w,float h,float d){var b=accent?trim:body;var center=new Point(x,y,z);var edge=List.of(new Point(0,-h/2,0),new Point(w/2,0,0),new Point(0,h/2,0),new Point(-w/2,0,0));var front=center.plus(new Point(0,0,-d/2));var back=center.plus(new Point(0,0,d/2));for(int i=0;i<4;i++){var a=center.plus(edge.get(i));var c=center.plus(edge.get((i+1)%4));b.triangle(front,c,a,24+i%2);b.triangle(back,a,c,24+i%2);}}
        void detail(boolean bright,float x,float y,float z,float w,float h,float d){var b=new Builder();b.box(w,h,d,.04f,16);(bright?light:ink).add(b,new Point(x,y,z),0,0,0);}
        void leaf(boolean accent,Point start,Point end,float width,float depth,int material){var delta=end.minus(start);loft(accent,List.of(start,start.plus(delta.times(.28f)).plus(new Point(0,0,-.2f)),start.plus(delta.times(.7f)).plus(new Point(0,0,-.35f)),end),new float[]{width*.48f,width,width*.78f,.05f},new float[]{depth*.65f,depth,depth*.8f,.05f},material);}
        AccessoryMeshes.Mesh mesh(){var a=body.bounds();var b=trim.bounds();var bounds=new Bounds(Math.min(a.minX(),b.minX()),Math.min(a.minY(),b.minY()),Math.min(a.minZ(),b.minZ()),Math.max(a.maxX(),b.maxX()),Math.max(a.maxY(),b.maxY()),Math.max(a.maxZ(),b.maxZ()));return new AccessoryMeshes.Mesh(AccessoryModelBake.bake(body),AccessoryModelBake.bake(trim),ink.faces().isEmpty()?null:AccessoryModelBake.bake(ink),light.faces().isEmpty()?null:AccessoryModelBake.bake(light),null,null,null,null,bounds);}
    }
    static AccessoryMeshes.Mesh build(AccessoryCatalog.Style style){
        var s=new Shape();int v=style.variant();
        switch(style.kind()){
            case "detailed_wings"->wings(s,v);case "detailed_halo"->halo(s,v);case "detailed_ears"->ears(s,v);
            case "detailed_hat"->hat(s,v);case "detailed_neck"->neck(s,v);case "detailed_back"->back(s,v);
            case "detailed_tail"->tail(s,v);case "detailed_pet"->pet(s,v);case "detailed_outfit"->outfit(s,v);case "detailed_aura"->aura(s);
            default->throw new IllegalArgumentException("Unknown original detailed accessory: "+style.kind());
        }
        var mesh=s.mesh();if(!style.kind().equals("detailed_outfit"))return mesh;
        var sleeves=new Shape();sleeves.box(false,0,4,0,4.8f,12.8f,4.8f);sleeves.box(true,0,9.7f,0,4.95f,.7f,4.95f);var arm=sleeves.mesh();var skirt=GarmentMeshes.skirt(true);
        return new AccessoryMeshes.Mesh(mesh.primary(),mesh.accent(),mesh.details(),mesh.highlights(),arm.primary(),arm.accent(),skirt[0],skirt[1],mesh.bounds());
    }
    private static void wings(Shape s,int v){
        if(v<2){
            // Broad primary feathers, then two shorter rows, each at a different depth.
            for(int i=0;i<10;i++){
                var start=new Point(1.4f+i*1.25f,-1.4f-i*.72f,.8f);
                var end=new Point(5.2f+i*1.72f,13-i*2.15f,2.1f+i*.045f);
                if(v==1)end=end.plus(new Point(.4f,-2,0));
                s.leaf(false,start,end,3.1f,1,4);
                if(i%2==0)s.leaf(true,end.minus(end.minus(start).times(.22f)).plus(new Point(0,0,-.2f)),end.plus(new Point(0,0,-.2f)),.75f,.35f,8);
            }
            for(int row=0;row<2;row++)for(int i=0;i<7;i++){
                var a=new Point(1.1f+i*1.78f,-2.6f-i*.9f+row*1.5f,-.55f-row*.8f);
                s.leaf(false,a,a.plus(new Point(3.2f,4.5f,1)),3,.95f,4+row);
            }
            s.tube(true,List.of(new Point(.2f,.4f,-.3f),new Point(3.2f,-4,-.7f),new Point(9,-8.5f,-1),new Point(14.3f,-10,-.2f)),.45f,8);
            s.gem(true,2.5f,-1,-1.3f,1.8f,2.2f,.75f);return;
        }
        if(v==2){
            var outline=List.of(new Point(.6f,1,0),new Point(3,-5,.4f),new Point(7,-11,.9f),new Point(14,-13,2),new Point(22,-11,4),new Point(18,-5,3),new Point(17,3,2.9f),new Point(12,0,1.9f),new Point(8,7,1),new Point(6,3,.8f),new Point(2,10,.2f));
            s.panel(false,outline,.85f,12);var elbow=new Point(7,-11,.9f);
            for(int face:new int[]{-1,1}){
                var offset=new Point(0,0,face*.6f);s.tube(true,List.of(new Point(.6f,1,0).plus(offset),new Point(3,-5,.4f).plus(offset),elbow.plus(offset),new Point(14,-13,2).plus(offset),new Point(22,-11,4).plus(offset)),.65f,8);
                for(var tip:List.of(new Point(18,-5,3),new Point(17,3,2.9f),new Point(8,7,1),new Point(2,10,.2f)))s.tube(true,List.of(elbow.plus(offset),elbow.plus(tip).times(.5f).plus(new Point(0,-1,face*.65f)),tip.plus(offset)),.4f,8);
            }
            for(var tip:List.of(new Point(18,-5,3),new Point(17,3,2.9f),new Point(8,7,1),new Point(2,10,.2f)))s.leaf(true,tip,tip.plus(new Point(1,-2,.3f)),1.2f,.7f,8);
            s.gem(true,7,-11,-.2f,1.8f,2.7f,1);return;
        }
        if(v==3){
            for(int lobe=0;lobe<2;lobe++){
                var outline=new ArrayList<Point>();float cy=lobe==0?-6:8,rx=lobe==0?8:6,ry=lobe==0?10:7;
                for(int i=0;i<24;i++){float a=i*(float)Math.PI*2/24,ripple=.94f+.06f*(float)Math.cos(a*5);float x=rx+(float)Math.cos(a)*rx*ripple,y=cy+(float)Math.sin(a)*ry*ripple;outline.add(new Point(x,y,.013f*x*x));}
                s.panel(false,outline,.9f,12);
                for(int face:new int[]{-1,1}){
                    var rim=new ArrayList<Point>();for(var p:outline)rim.add(p.plus(new Point(0,0,face*.58f)));rim.add(rim.getFirst());s.tube(true,rim,.3f,8);
                    for(int i=0;i<4;i++){var tip=outline.get(2+i*5);var root=new Point(.5f,lobe==0?0:3,face*.62f);s.tube(true,List.of(root,root.plus(tip).times(.5f).plus(new Point(0,0,face*.75f)),tip.plus(new Point(0,0,face*.65f))),.27f,8);}
                    for(int i=0;i<3;i++){var tip=outline.get(2+i*4);var root=new Point(1.8f,lobe==0?0:4,0);var mid=root.plus(tip).times(.5f);var side=new Point(-1.2f,.6f,0);var points=new ArrayList<Point>();for(var p:List.of(root,mid.plus(side),tip.times(.82f),mid.minus(side)))points.add(new Point(p.x(),p.y(),.013f*p.x()*p.x()+face*.75f));s.panel(true,points,.18f,6);}
                    for(int i=0;i<3;i++)s.gem(true,rx+1.2f+i*1.4f,cy-1+i*.8f,.013f*(rx+1+i)*(rx+1+i)+face*.65f,.7f,1.3f,.4f);
                }
            }
            return;
        }
        for(int i=0;i<7;i++){
            float x=2+i*2.6f,y=-2-i*.8f;var root=new Point(x,y,0);var end=new Point(x+3.5f,y+10-i*.85f,2.3f);
            if(v==4){s.loft(false,List.of(root,root.plus(end).times(.5f),end),new float[]{.8f,3.2f,.08f},new float[]{.6f,2.1f,.08f},8);s.leaf(true,root.plus(new Point(0,0,-.5f)),end.plus(new Point(0,0,-.35f)),.4f,.25f,8);}
            else{var outline=List.of(root,new Point(x+2.1f,y+1,.3f),new Point(end.x()+1,end.y()-1,end.z()),end,new Point(x+.3f,y+2,.3f));s.panel(false,outline,1.1f,8);s.tube(true,List.of(root.plus(new Point(0,0,-.7f)),root.plus(end).times(.5f).plus(new Point(0,0,-.65f)),end.plus(new Point(0,0,-.65f))),.5f,8);s.box(true,x,y,-.9f,1.4f,1.4f,.35f,0,0,.785f);}
        }
        s.tube(true,List.of(new Point(0,0,0),new Point(7,-5,.8f),new Point(19,-8,1.8f)),.7f,8);
    }
    private static void halo(Shape s,int v){
        s.ring(false,6,0,.55f);s.ring(true,v==0?5.2f:7,-.6f,.2f);
        int count=v==0?8:5;for(int i=0;i<count;i++){float a=i*(float)Math.PI*2/count,x=(float)Math.cos(a)*6,z=(float)Math.sin(a)*6;s.gem(true,x,-.6f,z,.9f,v==0?1.8f:2.4f,.5f);s.box(false,x,0,z,.45f,.7f,.45f);}
        if(v==1){s.ring(false,4.4f,-1.2f,.3f);s.gem(true,0,-2,-6,1.7f,2.8f,.8f);}
    }
    private static void ears(Shape s,int v){
        float h=v==1?7:5.5f;
        var outline=List.of(new Point(-2.5f,.2f,0),new Point(-2.5f,-1.5f,0),new Point(.7f,-h,.7f),new Point(2.8f,-2,.1f),new Point(2.7f,.3f,.2f));
        s.panel(false,outline,2.3f,4);
        s.panel(true,List.of(new Point(-1.3f,-.7f,-1.21f),new Point(.6f,-h+1.3f,-.3f),new Point(1.65f,-1,-1.1f)),.25f,6);
        for(int i=0;i<3;i++)s.leaf(false,new Point(1.6f,-.4f-i*.5f,.3f),new Point(3.4f,-1.3f-i*.6f,.5f),1,.7f,4);
        s.gem(true,-1.7f,.05f,-1.2f,.6f,.9f,.4f);
    }
    private static void hat(Shape s,int v){
        if(v==0){
            s.ring(false,4.65f,-.6f,.6f);s.ring(true,4.55f,-1.7f,.22f);
            for(int i=0;i<8;i++){float a=i*(float)Math.PI/4,x=(float)Math.cos(a)*4.65f,z=(float)Math.sin(a)*4.65f;s.leaf(false,new Point(x,-.7f,z),new Point(x*.88f,-4.1f-(i%2==0?1:0),z*.88f),1.45f,.65f,8);s.gem(true,x,-1.4f,z,1.15f,1.65f,.8f);}return;
        }
        if(v==1){
            s.loft(false,List.of(new Point(0,0,0),new Point(0,-.75f,0)),new float[]{14,14},new float[]{12,12},0);
            s.loft(false,List.of(new Point(0,-.8f,0),new Point(.3f,-3,.3f),new Point(1.1f,-6.5f,.5f),new Point(3,-9.5f,.8f),new Point(4.8f,-10.3f,.8f)),new float[]{8.9f,7.6f,4.5f,1.8f,.08f},new float[]{8.6f,7.3f,4.1f,1.8f,.08f},0);
            s.ring(true,4.1f,-1.6f,.6f);s.gem(true,0,-2,-4.6f,2.2f,2.6f,.7f);
            for(int i=0;i<3;i++)s.gem(true,1-i*.8f,-4-i*.9f,-3.7f+i*.35f,.6f,1,.3f);return;
        }
        if(v==2){
            s.loft(false,List.of(new Point(0,.2f,0),new Point(-.3f,-1.3f,0),new Point(-1,-3,0),new Point(-1.3f,-3.7f,0)),new float[]{9,10.6f,9.5f,7},new float[]{8.5f,10,8.8f,6.5f},0);
            s.ring(true,4.45f,.2f,.35f);s.box(false,-1.3f,-4.1f,0,.45f,.7f,.45f);
            for(int i=0;i<5;i++){float a=i*(float)Math.PI*2/5;s.leaf(true,new Point(3,-1.2f,-4.1f),new Point(3+(float)Math.cos(a)*1.4f,-1.2f+(float)Math.sin(a)*1.4f,-4.3f),1.1f,.45f,6);}s.gem(true,3,-1.2f,-4.5f,.7f,.9f,.6f);return;
        }
        s.box(false,0,-.3f,0,9.4f,.6f,9.4f);for(int side:new int[]{-1,1}){
            s.box(false,side*2,-1,-4.8f,3.6f,2.6f,1,0,0,side*.06f);s.box(true,side*2,-1,-5.35f,2.6f,1.65f,.22f);
            s.detail(true,side*2-.5f,-1.3f,-5.53f,.7f,.6f,.1f);s.box(true,side*4.8f,-.3f,0,.4f,1,1.6f);
        }s.box(true,0,-1,-4.8f,.85f,.4f,.5f);
    }
    private static void neck(Shape s,int v){
        if(v==0){
            for(int side:new int[]{-1,1}){s.loft(false,List.of(new Point(0,1.4f,-2.7f),new Point(side*1.8f,1.3f,-3.1f),new Point(side*3.1f,1.6f,-2.8f)),new float[]{.55f,2.65f,1.85f},new float[]{.6f,1.35f,.9f},0);s.panel(false,List.of(new Point(side*.3f,1.8f,-2.9f),new Point(side*1.2f,2,-2.9f),new Point(side*1.7f,5,-2.6f),new Point(side*.7f,4.5f,-2.7f)),.45f,0);}
            s.box(true,0,1.5f,-3.3f,1.05f,1.15f,1);s.gem(true,0,1.5f,-3.9f,.55f,.8f,.5f);return;
        }
        s.tube(false,List.of(new Point(-3.8f,0,-2.1f),new Point(-2,1.3f,-2.6f),new Point(0,2.1f,-2.8f),new Point(2,1.3f,-2.6f),new Point(3.8f,0,-2.1f)),.24f,8);
        s.ring(false,.75f,2.3f,.22f);s.gem(true,0,3.2f,-2.9f,1.9f,2.9f,1);s.gem(false,0,3.1f,-3.5f,1.1f,1.8f,.4f);
    }
    private static void back(Shape s,int v){
        if(v==0){
            s.box(false,0,6,3.8f,7,8.6f,3.4f);s.box(true,0,3.1f,5.7f,7.2f,2.6f,.75f);s.box(false,0,7.6f,5.8f,5.3f,3.2f,.8f);s.box(true,0,7.6f,6.3f,.75f,2.3f,.3f);
            for(int side:new int[]{-1,1}){s.tube(true,List.of(new Point(side*2.7f,2.5f,2.3f),new Point(side*3.4f,6,2.1f),new Point(side*2.7f,10,2.3f)),.35f,0);s.box(true,side*2.5f,3,6.2f,.6f,1.5f,.3f);}s.gem(true,0,3,6.3f,1.4f,1.8f,.6f);return;
        }
        s.box(false,0,5,4.2f,.9f,22,.9f,0,0,-.2f);for(int i=0;i<4;i++)s.box(true,-1.5f+i*.2f,-3+i*.7f,4.2f,2.8f,.3f,1,0,0,-.2f);
        s.gem(true,-2.1f,-6,4.2f,3.4f,5.2f,2);s.tube(false,List.of(new Point(-4,-6,4.2f),new Point(-4.4f,-9,4.2f),new Point(-1.2f,-9.8f,4.2f)),.45f,8);
    }
    private static void tail(Shape s,int v){
        var path=List.of(new Point(0,10,3.2f),new Point(1,11,5.1f),new Point(3.4f,10,8),new Point(5.5f,8,10.2f),new Point(5,5.7f,11.5f),new Point(3.6f,5,11));
        s.loft(false,path.subList(0,5),new float[]{1.3f,2.2f,3.8f,3.5f,2.5f},new float[]{1.3f,2.2f,3.8f,3.5f,2.5f},4);
        s.leaf(true,path.get(3),path.getLast(),3.1f,2.8f,6);
        if(v==1)for(int i=1;i<5;i++){var p=path.get(i);s.leaf(true,p.plus(new Point(0,-1,0)),p.plus(new Point(0,-3,-.8f)),1.3f,.65f,8);}
    }
    private static void pet(Shape s,int v){
        s.box(false,0,.4f,0,4.2f,3.5f,3.5f);s.box(false,0,-2.7f,-.65f,v==2?5:4.6f,4.1f,4);
        for(int side:new int[]{-1,1}){
            s.detail(true,side*.95f,-2.7f,-2.76f,1.3f,1.35f,.22f);s.detail(false,side*.95f,-2.6f,-2.92f,.72f,.9f,.16f);s.detail(true,side*.95f-.15f,-2.92f,-3.05f,.28f,.25f,.1f);
            s.box(true,side*1.15f,2,0,1.15f,1.6f,1.3f);s.box(true,side*1.6f,-1.5f,-2.74f,.9f,.35f,.18f);
        }
        if(v==0){
            for(int side:new int[]{-1,1}){s.panel(false,List.of(new Point(side*.5f,-4,-.4f),new Point(side*1.8f,-6.1f,.2f),new Point(side*2.1f,-3.7f,-.4f)),1.15f,4);s.gem(true,side*1.55f,-4.65f,-1.05f,.65f,1.2f,.4f);}
            s.gem(true,0,-1.6f,-2.85f,.7f,.6f,.5f);s.tube(false,List.of(new Point(1.5f,.7f,1.6f),new Point(3,.2f,3.5f),new Point(2.8f,-1.7f,3.8f),new Point(1.8f,-2.1f,3.7f)),.9f,4);
            s.box(true,0,0,-1.95f,4.3f,.5f,.5f);s.gem(true,0,.5f,-2.4f,.85f,1.3f,.6f);return;
        }
        if(v==1){
            s.box(false,0,-1.6f,-2.6f,2.7f,1.5f,1.7f);for(int side:new int[]{-1,1}){s.leaf(true,new Point(side*1.5f,-4,.3f),new Point(side*2.2f,-6.1f,1.1f),1,.65f,8);s.panel(true,List.of(new Point(side*1.5f,-.3f,1),new Point(side*4,-2.9f,1.6f),new Point(side*5.1f,.1f,1.8f),new Point(side*2.3f,1.7f,1.1f)),.6f,12);s.tube(false,List.of(new Point(side*1.5f,-.3f,.7f),new Point(side*4,-2.9f,1.3f),new Point(side*5.1f,.1f,1.5f)),.3f,8);}
            s.tube(false,List.of(new Point(0,1,1.5f),new Point(0,2,3.4f),new Point(1,.5f,4.6f)),1,4);return;
        }
        s.box(true,0,-2.6f,-2.68f,4,2.6f,.22f);for(int side:new int[]{-1,1}){s.detail(false,side*.95f,-2.6f,-3,.7f,.9f,.2f);s.detail(true,side*.95f-.15f,-2.9f,-3.12f,.25f,.24f,.1f);s.leaf(true,new Point(side*1.7f,-.5f,.5f),new Point(side*3.4f,2,.8f),2.5f,.8f,4);s.leaf(false,new Point(side*1.6f,-4,.2f),new Point(side*2.3f,-5.3f,.5f),.9f,.65f,4);}s.gem(true,0,-1.6f,-2.96f,.8f,.8f,.7f);
    }
    private static void outfit(Shape s,int v){
        float h=12.8f;s.box(false,0,6,2.4f,8.8f,h,.4f);s.box(false,0,6,-2.4f,8.8f,h,.4f);
        for(int side:new int[]{-1,1}){
            s.box(false,side*4.1f,6,0,.6f,h,5.2f);
            s.panel(true,List.of(new Point(side*.5f,0,-2.95f),new Point(side*3.4f,.6f,-2.95f),new Point(side*2.2f,4.4f,-3),new Point(side*.6f,2.6f,-3)),.22f,0);
            s.box(true,side*2.3f,9,-2.9f,2.2f,.3f,.22f);s.box(true,side*3.4f,h-.5f,0,.35f,.45f,4.9f);
        }
        s.box(true,0,10.9f,-2.85f,7.9f,.6f,.35f);for(int i=0;i<4;i++)s.gem(true,0,4.3f+i*1.8f,-3.05f,.45f,.65f,.3f);
    }
    private static void aura(Shape s){s.gem(false,0,0,0,1.8f,3,1.2f);s.gem(true,0,-.15f,-.72f,.8f,1.7f,.45f);s.ring(true,1.6f,0,.15f);}
}
