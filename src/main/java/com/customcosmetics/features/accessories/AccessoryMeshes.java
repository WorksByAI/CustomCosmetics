// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import net.minecraft.client.model.geom.*;
import java.util.*;
import static com.customcosmetics.features.accessories.AccessoryGeometry.*;

/** Original sculpted geometry, baked once; every style has closed front, back and edge surfaces. */
public final class AccessoryMeshes {
    public record Mesh(ModelPart primary,ModelPart accent,ModelPart details,ModelPart highlights,ModelPart sleeves,ModelPart cuffs,ModelPart skirt,ModelPart hem,Bounds bounds){}
    private static final Map<String,Mesh> CACHE=new HashMap<>();
    private AccessoryMeshes(){}
    public static Mesh get(AccessoryCatalog.Style style){return CACHE.computeIfAbsent(style.collection().id+":"+style.kind()+":"+style.variant(),ignored->style.collection()==AccessoryCatalog.Collection.DETAILED?DetailedAccessoryMeshes.build(style):build(style));}
    private static class Shape {
        final Builder main=new Builder(),trim=new Builder(),detail=new Builder(),shine=new Builder();int detailPieces,shinePieces;
        void box(boolean accent,float x,float y,float z,float w,float h,float d){box(accent,x,y,z,w,h,d,0,0,0);}
        void box(boolean accent,float x,float y,float z,float w,float h,float d,float rx,float ry,float rz){
            var box=new Builder();box.box(w,h,d,0,0);(accent?trim:main).add(box,new Point(x,y,z),rx,ry,rz);
        }
        void ring(boolean accent,float radius,float y,float thick,int count){int pieces=Math.min(16,count);for(int i=0;i<pieces;i++){float a=i*(float)Math.PI*2/pieces;box(accent,(float)Math.cos(a)*radius,y,(float)Math.sin(a)*radius,(float)Math.PI*2*radius/pieces+.12f,thick,thick,0,-a-(float)Math.PI/2,0);}}
        void star(boolean accent,float x,float y,float z,float size){var outline=new ArrayList<Point>();for(int i=0;i<10;i++){float a=(float)(-Math.PI/2+i*Math.PI/5),radius=size*(i%2==0?.55f:.25f);outline.add(new Point(x+(float)Math.cos(a)*radius,y+(float)Math.sin(a)*radius,z));}panel(accent,outline,Math.max(.35f,size*.25f),8);}
        void detail(boolean highlight,float x,float y,float z,float w,float h,float d){
            var b=new Builder();b.box(w,h,d,0,0);(highlight?shine:detail).add(b,new Point(x,y,z),0,0,0);if(highlight)shinePieces++;else detailPieces++;
        }
        void loft(boolean accent,List<Point> path,float[] widths,float[] depths,int material){float widest=0;for(float width:widths)widest=Math.max(widest,width);(accent?trim:main).pixelLoft(path,widths,depths,widest>=2?1:.5f,material);}
        void panel(boolean accent,List<Point> outline,float thickness,int material){float min=Float.MAX_VALUE,max=-min;for(var p:outline){min=Math.min(min,p.x());max=Math.max(max,p.x());}(accent?trim:main).pixelPanel(outline,thickness,max-min>=6?1:.5f,material);}
        void rib(boolean accent,Point a,Point b,Point c,float width,int material){loft(accent,List.of(a,b,c),new float[]{width,width*.82f,width*.3f},new float[]{width,width*.82f,width*.3f},material);}
        void roundBox(boolean accent,float x,float y,float z,float w,float h,float d){box(accent,x,y,z,w,h,d);}
        void ellipsoid(boolean accent,float x,float y,float z,float w,float h,float d){var path=new ArrayList<Point>();float[] widths=new float[9],depths=new float[9];for(int i=0;i<9;i++){float t=i/8f,profile=(float)Math.sqrt(Math.max(.001,1-(t*2-1)*(t*2-1)));path.add(new Point(x,y+(t-.5f)*h,z));widths[i]=w*profile;depths[i]=d*profile;}loft(accent,path,widths,depths,6);}
        void gem(boolean accent,float x,float y,float z,float w,float h,float d){var builder=accent?trim:main;var center=new Point(x,y,z);var edge=List.of(new Point(0,-h/2,0),new Point(w/2,0,0),new Point(0,h/2,0),new Point(-w/2,0,0));Point front=center.plus(new Point(0,0,-d/2)),back=center.plus(new Point(0,0,d/2));for(int i=0;i<4;i++){Point a=center.plus(edge.get(i)),b=center.plus(edge.get((i+1)%4));builder.triangle(front,b,a,8+i%2);builder.triangle(back,a,b,8+i%2);}}
        Mesh mesh(){var a=main.bounds();var b=trim.bounds();var bounds=new Bounds(Math.min(a.minX(),b.minX()),Math.min(a.minY(),b.minY()),Math.min(a.minZ(),b.minZ()),Math.max(a.maxX(),b.maxX()),Math.max(a.maxY(),b.maxY()),Math.max(a.maxZ(),b.maxZ()));return new Mesh(AccessoryModelBake.bake(main),AccessoryModelBake.bake(trim),detailPieces>0?AccessoryModelBake.bake(detail):null,shinePieces>0?AccessoryModelBake.bake(shine):null,null,null,null,null,bounds);}
    }
    private static Mesh build(AccessoryCatalog.Style style){
        var s=new Shape();int v=style.variant();
        switch(style.kind()){
            case "feather"->feather(s,v);
            case "membrane"->membrane(s,v);
            case "bone"->bone(s);
            case "butterfly"->butterfly(s,v);
            case "crystal"->crystal(s);
            case "leaf"->leaf(s);
            case "mechanical"->mechanical(s);
            case "halo"->halo(s,v);
            case "ears"->ears(s,v);
            case "horns"->horns(s,v);
            case "hat"->hat(s,v);
            case "face"->face(s,v);
            case "hair"->hair(s,v);
            case "neck"->neck(s,v);
            case "back"->back(s,v);
            case "tail"->tail(s,v);
            case "arms"->arms(s,v);
            case "feet"->feet(s,v);
            case "animal"->animal(s,v);
            case "aura"->aura(s,v);
            case "outfit"->outfit(s,v);
            case "head"->head(s,v);
            case "pants"->pants(s,v);
            default->throw new IllegalArgumentException("Unknown original accessory shape: "+style.kind());
        }
        var mesh=s.mesh();
        if(style.kind().equals("outfit")&&(v==1||v==2)){
            var sleeve=new Shape();sleeve.box(false,0,4,0,4.8f,12.8f,4.8f);sleeve.box(true,0,9.7f,0,4.95f,.7f,4.95f);var arm=sleeve.mesh();var skirt=v==2?GarmentMeshes.skirt(false):null;
            return new Mesh(mesh.primary(),mesh.accent(),mesh.details(),mesh.highlights(),arm.primary(),arm.accent(),skirt==null?null:skirt[0],skirt==null?null:skirt[1],mesh.bounds());
        }
        return mesh;
    }
    private static void feather(Shape s,int v){
        int layers=v==1?2:1;
        for(int layer=0;layer<layers;layer++){
            float scale=layer==1?.68f:1,offset=layer*8;
            for(int i=0;i<12;i++){
                float x=(1.1f+i*1.13f)*scale,y=(-1.5f-i*.7f)*scale+offset;
                float endX=(2.7f+i*1.75f)*scale,endY=(12.5f-i*1.58f-(v==2?i*.2f:0))*scale+offset;
                plume(s,false,new Point(x,y,.4f),new Point(endX,endY,2.2f+i*.075f),2.6f*scale,.85f*scale,4);
            }
            // Two broad rows retain layered feathers without a noisy miniature fur texture.
            for(int face:new int[]{-1,1})for(int row=0;row<2;row++)for(int i=0;i<6;i++){
                float x=(1.2f+i*2+row*.4f)*scale,y=(-2-i*1.02f+row*1.8f)*scale+offset,z=face*(1.15f-row*.18f)+.6f;
                plume(s,row==0&&i<3,new Point(x,y,z),new Point(x+2.6f*scale,y+3.5f*scale,z+face*.45f),3*scale,1*scale,row==0?6:4);
            }
            s.rib(true,new Point(.4f,0,.5f),new Point(6*scale,-5*scale+offset,.2f),new Point(14*scale,-10*scale+offset,1),.6f,6);
        }
    }
    private static void plume(Shape s,boolean accent,Point start,Point end,float width,float depth,int material){
        List<Point> path=new ArrayList<>();float[] widths=new float[8],depths=new float[8];
        for(int j=0;j<8;j++){float t=j/7f;var p=start.times(1-t).plus(end.times(t));path.add(p.plus(new Point((float)Math.sin(t*Math.PI)*.45f,0,(float)Math.sin(t*Math.PI)*.45f)));float profile=(float)Math.sin((.13+t*.87)*Math.PI);widths[j]=Math.max(.09f,width*profile);depths[j]=Math.max(.06f,depth*profile);}
        s.loft(accent,path,widths,depths,material);
    }
    private static void membrane(Shape s,int v){
        float scale=v==1?1.16f:1;float[][] contour={{.5f,1},{3,-5},{7,-9},{12,-11},{20,-14},{19,-8},{17,-2},{15,1},{13,-.2f},{11,1},{9,6},{7,3.5f},{5,4.4f},{2,9}};
        var outline=new ArrayList<Point>();for(var p:contour)outline.add(new Point(p[0]*scale,p[1]*scale,.012f*p[0]*p[0]));s.panel(false,outline,.65f,12);
        var root=new Point(1,1,0);s.rib(true,root,new Point(7*scale,-9*scale,.7f),new Point(20*scale,-14*scale,4.8f),1.1f,8);
        for(var tip:List.of(new Point(17,-2,3.5f),new Point(9,6,1),new Point(2,9,.1f)))s.rib(true,new Point(7*scale,-9*scale,.7f),new Point((7+tip.x())*.5f*scale,(-9+tip.y())*.5f*scale,tip.z()*.5f),tip.times(scale),.65f,8);
        if(v==1)for(int i=0;i<3;i++)plume(s,true,new Point(7+i*4,-9-i*1.2f,.6f+i),new Point(8+i*4,-13-i*1.5f,1+i),1.2f,.7f,8);
    }
    private static void bone(Shape s){
        s.rib(false,new Point(0,1,0),new Point(7,-6,.8f),new Point(18,-11,3),1.2f,8);
        for(int i=0;i<6;i++){float x=2+i*2.5f,y=-2-i*1.3f;s.rib(false,new Point(x,y,.15f*i),new Point(x+2,y+3,.5f+i*.35f),new Point(x+3.5f,y+8-i*.6f,2+i*.4f),.95f,8);s.box(true,x,y,.15f*i,1.7f,1.4f,1.6f);}
    }
    private static void butterfly(Shape s,int v){
        for(int lobe=0;lobe<2;lobe++){
            float cx=lobe==0?8:6.4f,cy=lobe==0?-6:8,rx=lobe==0?8:6.2f,ry=(lobe==0?11:7)*(v==2?.83f:1);var outline=new ArrayList<Point>();
            if(lobe==0){curve(outline,new Point(.4f,2,0),new Point(.4f,-5,0),new Point(12,-20,0),new Point(17,-16,0));curve(outline,new Point(17,-16,0),new Point(22,-7,0),new Point(9,3,0),new Point(.4f,2,0));}
            else{curve(outline,new Point(.4f,3,0),new Point(7,1,0),new Point(15,6,0),new Point(11,13,0));curve(outline,new Point(11,13,0),new Point(6,19,0),new Point(.3f,13,0),new Point(.4f,3,0));}
            for(int i=0;i<outline.size();i++){var p=outline.get(i);float x=p.x()*(v==2?.86f:1),y=p.y()*(v==2?.88f:1);outline.set(i,new Point(x,y,.013f*x*x));}
            s.panel(false,outline,.7f,12);
            for(int face:new int[]{-1,1}){
                // Separate inlaid color cells make the wing readable beyond its silhouette.
                for(int cell=0;cell<3;cell++){
                    var tip=outline.get(4+cell*4);float hingeY=lobe==0?0:4;
                    var path=List.of(new Point(1.4f,hingeY,face*1.3f),new Point(tip.x()*.55f,tip.y()*.55f+hingeY*.3f,.013f*tip.x()*tip.x()*.3f+face*1.3f),new Point(tip.x()*.83f,tip.y()*.83f,.013f*tip.x()*tip.x()*.7f+face*1.3f));
                    s.loft(true,path,new float[]{.22f,lobe==0?2.1f:1.55f,.15f},new float[]{.12f,.14f,.1f},6);
                }
                for(int i=0;i<outline.size();i++){var a=outline.get(i);var b=outline.get((i+1)%outline.size());s.rib(true,a.plus(new Point(0,0,face*.4f)),a.plus(b).times(.5f).plus(new Point(0,0,face*.45f)),b.plus(new Point(0,0,face*.4f)),.42f,8);}
                for(int i=0;i<4;i++){var tip=outline.get(4+i*3);s.rib(true,new Point(.6f,lobe==0?0:3,face*.5f),new Point(tip.x()*.5f,tip.y()*.55f,face*.55f+.013f*tip.x()*tip.x()*.3f),tip.plus(new Point(0,0,face*.42f)),.42f,8);}
                for(int i=0;i<3;i++){float x=cx+.8f+i*1.65f,y=cy-ry*.25f+i*.9f;s.box(true,x,y,.013f*x*x+face*.55f,v==2?.65f:1,1,.4f,0,0,.785f);}
            }
        }
        if(v==1)for(int face:new int[]{-1,1})s.rib(true,new Point(7,11,face),new Point(8,18,face+1),new Point(6,23,face+2),.55f,6);
    }
    private static void curve(List<Point> outline,Point a,Point b,Point c,Point d){for(int i=0;i<16;i++){float t=i/16f,u=1-t;outline.add(a.times(u*u*u).plus(b.times(3*u*u*t)).plus(c.times(3*u*t*t)).plus(d.times(t*t*t)));}}
    private static void crystal(Shape s){for(int i=0;i<7;i++){float x=2+i*2.3f,y=-i*.85f;Point root=new Point(x,y,0),mid=new Point(x+1.8f,y+3,1.2f),end=new Point(x+4,y+9-i*.7f,2.8f);s.loft(false,List.of(root,mid,end),new float[]{.2f,2.8f,.08f},new float[]{.2f,2.1f,.08f},8);s.rib(true,root,mid.plus(new Point(0,0,-1.05f)),end,.32f,8);}s.rib(true,new Point(.5f,0,0),new Point(7,-4,.2f),new Point(17,-8,1),.55f,8);}
    private static void leaf(Shape s){for(int i=0;i<7;i++){float x=1.5f+i*1.7f,y=-3-i*.8f;plume(s,false,new Point(x,y,i*.25f),new Point(x+3,y+10-i*.4f,2+i*.3f),3.4f,1.1f,12);s.rib(true,new Point(x,y,-.2f+i*.25f),new Point(x+1.3f,y+4,.5f+i*.3f),new Point(x+2.8f,y+8.7f,2+i*.3f),.24f,8);}}
    private static void mechanical(Shape s){s.rib(false,new Point(1,0,0),new Point(7,-4,1),new Point(18,-7,2.5f),2,8);for(int i=0;i<6;i++){float x=4+i*2.5f,y=-2-i*.8f;var path=List.of(new Point(x,y,.35f*i),new Point(x+1.5f,y+3,.8f+i*.45f),new Point(x+3.7f,y+10-i*.6f,2.5f+i*.35f));s.loft(false,path,new float[]{1.3f,2.8f,.45f},new float[]{1.5f,1.8f,.65f},8);s.rib(true,path.getFirst().plus(new Point(0,0,-.7f)),path.get(1).plus(new Point(0,0,-.9f)),path.getLast().plus(new Point(0,0,-.3f)),.35f,8);s.box(true,x,y,-.6f+i*.35f,.8f,.8f,.3f);}s.ring(true,1.4f,0,.45f,16);}
    private static void halo(Shape s,int v){
        if(v<3)s.ring(false,6,0,.65f,24);
        if(v==1){s.ring(true,4.6f,-1.5f,.4f,24);s.ring(false,7.3f,1.6f,.35f,24);}
        if(v==2)for(int i=0;i<8;i++){float a=(float)(i*Math.PI/4);s.star(true,(float)Math.cos(a)*6,-1,(float)Math.sin(a)*6,1.8f);}
        if(v==3){for(int i=0;i<19;i++){float a=(float)(i*Math.PI*2/24);s.box(false,(float)Math.cos(a)*5,0,(float)Math.sin(a)*5,1.5f,.8f,.8f,0,-a-(float)Math.PI/2,0);}s.star(true,3,-1,-4,2.5f);}
        s.box(true,0,.1f,6,.8f,.4f,.8f);
    }
    private static void ears(Shape s,int v){
        if(v==4){s.box(false,1,0,0,6,2,1.4f,0,0,-.32f);s.box(true,2,-.3f,-.8f,3,.65f,.1f,0,0,-.32f);return;}
        if(v==5){s.box(false,1.4f,-1.4f,0,5.6f,1.6f,1.4f,0,0,-.36f);s.box(true,1.7f,-1.6f,-.8f,4.2f,.7f,.2f,0,0,-.36f);return;}
        float length=v==3?10.5f:v==2?6.9f:v==1?6.1f:4.8f;
        if(v==3){s.loft(false,List.of(new Point(0,0,0),new Point(.1f,-3,.35f),new Point(.5f,-7,1),new Point(.8f,-10.5f,1.25f)),new float[]{2.4f,3.1f,2.7f,.35f},new float[]{2,1.9f,1.55f,.45f},4);s.loft(true,List.of(new Point(.1f,-1,-1.06f),new Point(.2f,-4,-.7f),new Point(.7f,-9,.2f)),new float[]{1.2f,1.5f,.25f},new float[]{.12f,.16f,.1f},6);return;}
        s.panel(false,List.of(new Point(-2.6f,0,.2f),new Point(-2.5f,-1.8f,.15f),new Point(.45f,-length,1.15f),new Point(2.5f,-2,.2f),new Point(2.6f,.1f,.25f)),2.6f,4);
        s.panel(true,List.of(new Point(-1.45f,-.65f,-1.18f),new Point(.35f,-length+1.3f,-.15f),new Point(1.45f,-.8f,-1.15f)),.15f,6);
        for(int i=0;i<3;i++)plume(s,false,new Point(1.7f,-.6f-i*.45f,.5f),new Point(2.8f,-1.4f-i*.45f,.3f),.65f,.6f,4);
    }
    private static void horns(Shape s,int v){var path=List.of(new Point(0,0,0),new Point(.7f,-2,.35f),new Point(1.8f,-4,1.1f),new Point(3.6f,-6,1.55f),new Point(4.2f,-8.2f,1.1f));s.loft(false,path,new float[]{2.2f,2,1.65f,1,.12f},new float[]{2.2f,2,1.65f,1,.12f},8);if(v==1)for(int i=1;i<4;i++){var p=path.get(i);s.rib(true,p,p.plus(new Point(1.4f,-.6f,.25f)),p.plus(new Point(2.5f,-1.5f,.5f)),.75f,8);}s.ring(true,1.15f,-.7f,.3f,16);}
    private static void hat(Shape s,int v){
        switch(v){
            case 0->{for(int side:new int[]{-1,1}){s.box(false,side*4.1f,-1,0,.7f,2.2f,8.9f);s.box(false,0,-1,side*4.1f,8.9f,2.2f,.7f);for(int i=-1;i<=1;i++){s.loft(false,List.of(new Point(i*3,-1,side*4.1f),new Point(i*3,-2,side*4.1f),new Point(i*3,-4+(i==0?-1:0),side*4.1f)),new float[]{1.5f,1.2f,.15f},new float[]{.8f,.8f,.2f},8);s.gem(true,i*3,-1,side*4.5f,i==0?1.45f:.9f,i==0?1.75f:1.1f,.9f);}}s.ring(false,4.5f,.1f,.35f,32);}
            case 1->{s.ring(true,4.8f,0,.5f,16);for(int i=0;i<8;i++){float a=(float)(i*Math.PI/4),x=(float)Math.cos(a)*4.7f,z=(float)Math.sin(a)*4.7f;for(int petal=0;petal<6;petal++){float p=petal*(float)Math.PI/3;plume(s,false,new Point(x,-.45f,z),new Point(x+(float)Math.cos(p)*1.4f,-.75f,z+(float)Math.sin(p)*1.4f),1.15f,.55f,6);}s.box(true,x,-1,z,.65f,.55f,.65f);}}
            case 2->{s.roundBox(false,0,.3f,0,11,.8f,11);s.roundBox(false,0,-4,0,7.8f,8,7.8f);s.box(true,0,-1,0,8,1.5f,8);s.gem(true,0,-1,-4.2f,2,2,1);plume(s,true,new Point(3,-2,-4),new Point(5,-7,-3),1.3f,.6f,4);}
            case 3->{s.roundBox(false,0,0,0,14,.8f,13);s.loft(false,List.of(new Point(0,-.5f,0),new Point(.3f,-2.5f,.2f),new Point(.9f,-6,.4f),new Point(2,-9.5f,.8f),new Point(4,-10.8f,.9f)),new float[]{8.8f,7.9f,5.4f,2.7f,.2f},new float[]{8.8f,7.8f,5.2f,2.5f,.2f},0);s.ring(true,4.25f,-1.2f,.6f,32);s.gem(true,0,-2,-4.25f,2,2.3f,.75f);for(int i=0;i<3;i++)s.star(true,1.7f-i*.7f,-4.1f-i*.7f,-3.3f+i*.3f,.8f);}
            case 4->{s.roundBox(false,0,0,0,14,.8f,12);s.roundBox(false,0,-2.3f,0,8.6f,4.3f,8.6f);s.box(true,0,-.9f,0,8.8f,1,8.8f);for(int side:new int[]{-1,1})plume(s,true,new Point(3,-.8f,-4.5f),new Point(3+side*1.65f,-1,-4.8f),1.3f,.5f,6);s.gem(true,3,-1,-4.85f,.65f,.75f,.4f);}
            case 5->{s.ellipsoid(false,0,-.9f,.1f,9.2f,5.4f,9.2f);s.roundBox(false,0,.9f,-5.5f,7.8f,.75f,4);s.gem(true,0,-1.4f,-4.5f,1.7f,1.6f,.55f);}
            case 6->{s.roundBox(false,0,-1.5f,0,9.1f,3.8f,9.1f);s.box(true,0,0,0,9.4f,1.2f,9.4f);s.ellipsoid(true,.7f,-4.7f,.3f,3.2f,3.2f,3.2f);for(int side:new int[]{-1,1})s.box(true,side*3.5f,-1.5f,-4.6f,.35f,2,.15f);}
            case 7->{s.box(false,0,.2f,0,14,.8f,11);s.box(false,0,-2.5f,0,8.5f,5,7.5f);s.box(true,0,-.8f,0,8.7f,.9f,7.7f);for(int side:new int[]{-1,1})s.box(false,side*6,-.4f,0,2.4f,.8f,10,0,0,side*.25f);}
            case 8->{s.box(false,0,-1.5f,0,9,4,9);s.box(true,0,-1.8f,-4.6f,9,2,.5f);for(int side:new int[]{-1,1})s.box(true,side*4.6f,1,.3f,1.8f,5,5);}
            case 9->{s.box(false,0,-.6f,0,9.1f,1.5f,9.1f);for(int side:new int[]{-1,1})for(int i=0;i<5;i++){s.box(side==1,side*(i*.9f+1),-i*.9f-1,0,3-i*.45f,2,3-i*.45f,0,0,side*.3f);if(i==4)s.box(side==-1,side*5,-4.5f,0,1.5f,1.5f,1.5f);}}
            case 10->{s.box(false,0,-.5f,0,9,.6f,2);for(int side:new int[]{-1,1}){s.box(false,side*4.5f,3,0,1.2f,6,2);s.box(true,side*5.1f,3,0,.65f,4,3.5f);}s.box(true,5,5,-2,.45f,.45f,4);}
            default->throw new IllegalArgumentException("hat "+v);
        }
    }
    private static void face(Shape s,int v){
        if(v<=2){for(int side:new int[]{-1,1}){if(v==2&&side==-1)continue;float x=side*2;
            if(v==1){s.box(false,x,-3,-4.35f,3.5f,1.7f,.4f);s.box(true,x,-3,-4.6f,2.7f,1.05f,.1f);}
            else{for(int i=0;i<8;i++){float a=(float)(i*Math.PI/4);s.box(false,x+(float)Math.cos(a)*1.55f,-3+(float)Math.sin(a)*1.1f,-4.4f,.7f,.6f,.3f);}s.box(true,x,-3,-4.32f,2,1.1f,.12f);}
            s.box(false,side*4.2f,-3,-1,.3f,.4f,6.7f);
        }s.box(false,0,-3,-4.4f,1,.35f,.3f);if(v==2)s.box(true,3.3f,-.3f,-4.4f,.25f,4,.25f);return;}
        if(v==3){s.box(false,0,-1.2f,-4.4f,5.9f,2.2f,.5f);for(int side:new int[]{-1,1})s.box(true,side*3.3f,-1.2f,-4.5f,.5f,.9f,.2f);}
        if(v==4){for(int side:new int[]{-1,1})for(int i=0;i<3;i++)s.box(false,side*2.6f,-1.3f-i*.45f,-4.4f,2.8f,.2f,.2f,0,0,side*(i-1)*.17f);s.box(true,0,-1.5f,-4.7f,.9f,.6f,.3f);}
        if(v==5)for(int side:new int[]{-1,1}){s.box(false,side*2.6f,-1.2f,-4.22f,1.6f,.8f,.1f);s.box(true,side*2.6f,-1.2f,-4.3f,.45f,.65f,.1f);}
    }
    private static void hair(Shape s,int v){
        s.box(false,0,-7.8f,0,8.5f,.65f,8.5f);for(int i=0;i<5;i++)s.box(true,-3+i*1.5f,-6.8f,-4.25f,1.2f,1.7f,.5f,0,0,(i-2)*.08f);
        if(v==0)for(int i=0;i<7;i++){float x=-3.8f+i*1.25f;s.loft(false,List.of(new Point(x,-7,4),new Point(x,-3,5),new Point(x+(i-3)*.2f,2,4.8f),new Point(x+(i-3)*.35f,4+i%2,4.1f)),new float[]{1.7f,1.65f,1.4f,.3f},new float[]{1.4f,1.7f,1.4f,.35f},4);}
        if(v==1){s.box(true,0,-5.7f,4.6f,2.7f,1,1);for(int i=0;i<5;i++)s.box(false,(float)Math.sin(i*.45)*.6f,-5+i*1.6f,5.4f+i*.3f,2-i*.2f,2.2f,1.9f);}
        if(v==2)for(int side:new int[]{-1,1}){s.box(false,side*4.5f,-7,1,3,3,3);s.box(true,side*4.6f,-6,1,3.2f,.6f,3.2f);}
        if(v==3)for(int i=-2;i<=2;i++)s.box(false,i*1.8f,-9.2f-Math.abs(i)*.2f,0,1.8f,3,6,0,0,-i*.17f);
    }
    private static void neck(Shape s,int v){
        if(v==0){s.box(false,0,1,0,8.7f,2.4f,5.4f);s.box(true,0,1.2f,-2.85f,8.2f,.55f,.2f);s.box(false,2.5f,5,-2.8f,2.4f,7,.6f,0,0,-.08f);s.box(true,2.5f,8.2f,-3.2f,2.5f,.6f,.2f);}
        if(v==1){for(int side:new int[]{-1,1})s.loft(false,List.of(new Point(0,1.6f,-2.6f),new Point(side*1.5f,1.5f,-2.9f),new Point(side*2.6f,1.6f,-2.7f)),new float[]{.4f,2.3f,1.65f},new float[]{.6f,1.1f,.65f},6);s.roundBox(true,0,1.6f,-3.05f,.85f,.9f,.8f);}
        if(v==2){s.box(false,0,.8f,0,8.1f,.6f,4.5f);s.box(true,0,1.2f,-2.6f,1.3f,1.3f,.35f,0,0,.785f);s.box(false,.7f,2.3f,-2.5f,.6f,2,.2f,0,0,.25f);}
        if(v==3){for(int i=0;i<7;i++){float xx=(i-3)*.9f,yy=2.3f-(float)Math.abs(i-3)*.45f;for(int side:new int[]{-1,1}){s.box(false,xx+side*.35f,yy,-2.5f,.25f,.95f,.3f);s.box(false,xx,yy+side*.35f,-2.5f,.95f,.25f,.3f);}}s.gem(true,0,3.2f,-2.8f,1.9f,2.3f,1);}
    }
    private static void back(Shape s,int v){
        if(v==0){s.box(false,0,6,4,6.5f,8,3);s.box(true,0,7.8f,5.6f,5.5f,3.3f,.8f);s.box(true,0,2.8f,5.65f,5.5f,1.2f,.4f);for(int side:new int[]{-1,1})s.box(true,side*2.7f,6,2.2f,.5f,9,.5f);return;}
        if(v==1){s.box(false,0,6,4,3.4f,8,3.4f,0,0,-.2f);for(int i=0;i<4;i++){s.box(true,-1+i*.7f,.7f,4,.25f,5,.25f,0,0,-.2f);s.box(true,-1+i*.7f,-1,4,.7f,1.2f,.3f);}return;}
        if(v>=2&&v<=4){int count=v==4?2:1;for(int side=0;side<count;side++){float angle=count==2?(side==0?-.55f:.55f):-.5f;float x=side==1?1:-1;s.box(false,x,6,4,v==2?2:1.2f,19,.6f,0,0,angle);s.box(true,x-4*(float)Math.sin(angle),6+4*(float)Math.cos(angle),4,5.5f,.8f,1,0,0,angle);s.box(true,x+6*(float)Math.sin(angle),6-6*(float)Math.cos(angle),4,1,5,1,0,0,angle);}return;}
        if(v==5){s.box(false,0,5,4,.9f,22,.9f,0,0,-.35f);s.box(true,-4,-4,4,9,1.3f,.6f,0,0,.35f);s.box(true,-8,-2,4,1.3f,5,.6f,0,0,.35f);return;}
        if(v==6){s.box(false,0,5,4,.8f,19,.8f,0,0,-.25f);s.box(true,-2.3f,-4,4,3,4,1.7f,0,0,.785f);return;}
        s.box(false,0,5,4,10,6,3);for(int side:new int[]{-1,1}){s.box(true,side*3,5,5.7f,3.3f,3.3f,.5f);s.box(false,side*3,5,6,2,2,.2f);}s.box(true,0,1.5f,4,4,.5f,1);
    }
    private static void tail(Shape s,int v){
        if(v==3){s.box(false,0,10,4.2f,4,4,4);s.box(true,0,10,6.3f,2,2,.3f);return;}
        int tails=v==4?9:1;for(int t=0;t<tails;t++){
            List<Point> path=new ArrayList<>();float[] widths=new float[9],depths=new float[9];float spread=tails==1?0:(t-4)*.31f;
            for(int i=0;i<9;i++){float x=(float)Math.sin(i*.29+spread)*i*.65f,y=10+i*.55f-(v==0?i*i*.08f:0),z=3+i*1.05f;if(tails>1){x=(t-4)*2.2f*(float)Math.sin(i*Math.PI/16);y=10-i*.3f-(4-Math.abs(t-4))*i*.1f;z=3+i*1.2f;}path.add(new Point(x,y,z));float width=v==0?1.2f:v==1?3.8f:v==2?2.8f:v==5?2.8f:2.5f;float profile=i==8?.14f:(float)Math.sin((.3f+i/8f*.7f)*Math.PI);widths[i]=Math.max(.15f,width*profile);depths[i]=widths[i];}
            s.loft(false,path.subList(0,7),Arrays.copyOfRange(widths,0,7),Arrays.copyOfRange(depths,0,7),4);s.loft(true,path.subList(6,9),Arrays.copyOfRange(widths,6,9),Arrays.copyOfRange(depths,6,9),6);
            if(v==5)for(int i=1;i<7;i+=2){var p=path.get(i);plume(s,true,p.plus(new Point(0,-widths[i]/2,0)),p.plus(new Point(0,-widths[i]/2-1.5f,-.5f)),1,.7f,8);}
        }
    }
    private static void arms(Shape s,int v){
        if(v==0){s.box(false,0,8.5f,0,5.15f,.7f,5.15f);s.box(true,0,8.5f,-2.8f,1.2f,1.2f,.4f,0,0,.785f);}
        if(v==1){s.box(false,0,8.9f,0,5.15f,3.5f,5.15f);s.box(true,0,7.2f,0,5.3f,.8f,5.3f);}
        if(v==2){s.box(false,0,8.9f,0,5.3f,3.6f,5.3f);s.box(true,0,8.7f,-2.8f,2,1.7f,.3f);for(int i=-1;i<=1;i++)s.box(true,i*1.25f,7.3f,-2.8f,.65f,.7f,.2f);}
    }
    private static void feet(Shape s,int v){
        s.box(false,0,v==0?9.1f:10.5f,-.4f,5.25f,v==0?6:3.2f,6.1f);s.box(true,0,12.2f,-.4f,5.4f,.6f,6.3f);
        if(v==0){s.box(true,0,8,-3.4f,2,1.5f,.3f);for(int side:new int[]{-1,1})s.box(true,side*.8f,8,-3.5f,1.5f,.7f,.2f,0,0,side*.3f);}
        if(v==1)for(int i=0;i<3;i++)s.box(true,0,9.7f,-1.3f-i*.65f,2.2f,.3f,.3f);
        if(v==2){s.box(true,0,10.2f,-3.4f,1.6f,1.2f,.3f);for(int i=-1;i<=1;i++)s.box(true,i*1.25f,9.3f,-3.4f,.7f,.6f,.2f);}
    }
    private static void animal(Shape s,int v){
        s.roundBox(false,0,0,0,v==3?4:3.8f,3.2f,3.8f);s.roundBox(false,0,-2.6f,-.7f,v==1?4.9f:4.3f,4,3.9f);
        for(int side:new int[]{-1,1}){
            s.detail(true,side*.9f,-2.8f,-2.77f,1.1f,1.2f,.2f);s.detail(false,side*.9f,-2.8f,-2.91f,.6f,.85f,.15f);s.detail(true,side*.9f-.12f,-3,-3.02f,.2f,.22f,.1f);
            s.box(false,side*1.1f,2,0,.8f,1.5f,.9f);
        }
        if(v==0){for(int side:new int[]{-1,1}){plume(s,false,new Point(side*1.3f,-4,-.7f),new Point(side*1.75f,-6.3f,-.2f),1.4f,1,4);s.gem(true,side*1.4f,-4.8f,-1.35f,.7f,1.1f,.35f);s.box(true,side*1.45f,-1.9f,-2.72f,.8f,.35f,.15f);for(int whisker=0;whisker<2;whisker++)s.detail(false,side*1.65f,-1.7f+whisker*.4f,-2.8f,1.35f,.14f,.12f);}s.gem(true,0,-1.8f,-2.65f,.6f,.45f,.45f);s.rib(false,new Point(1.3f,.5f,1.7f),new Point(2.3f,.4f,3.2f),new Point(1.7f,-1.7f,4.1f),.8f,4);}
        if(v==1){for(int side:new int[]{-1,1})s.box(true,side*2.2f,0,.2f,.7f,3,3);s.box(true,0,-1.8f,-2.6f,.8f,.8f,.8f,0,0,.785f);}
        if(v==2){for(int side:new int[]{-1,1}){plume(s,true,new Point(side*1.7f,-1,1),new Point(side*4.7f,-2.2f,1.5f),2,.6f,12);s.rib(true,new Point(side*1.25f,-4,0),new Point(side*1.4f,-5.5f,.3f),new Point(side*1.6f,-6.3f,.7f),.7f,8);}s.roundBox(false,0,-1.5f,-2.6f,2.4f,1.6f,1.8f);s.rib(false,new Point(0,1,1.6f),new Point(0,1.8f,3.1f),new Point(.7f,.6f,5),.9f,4);}
        if(v==3){for(int i=-1;i<=1;i++)s.box(true,0,.1f,i*1.05f,4.1f,3.4f,.4f);for(int side:new int[]{-1,1})s.detail(true,side*2.5f,-2.8f,0,2.7f,.35f,2);s.box(true,0,-1.5f,2.3f,.6f,.6f,1.5f);}
    }
    private static void aura(Shape s,int v){
        if(v==0)s.star(false,0,0,0,2);
        if(v==1){s.ellipsoid(false,-.46f,-.32f,0,1.15f,1.15f,.9f);s.ellipsoid(false,.46f,-.32f,0,1.15f,1.15f,.9f);s.gem(false,0,.65f,0,1.7f,1.65f,.85f);}
        if(v==2)s.box(false,0,0,0,1.8f,.4f,1.2f,.4f,.6f,.7f);
        if(v==3)s.gem(false,0,0,0,1.4f,2.6f,1.2f);
        s.box(true,0,0,-.3f,.5f,.5f,.3f);
    }
    private static void outfit(Shape s,int v){
        float height=12.8f;
        for(int side:new int[]{-1,1})s.box(false,side*4.1f,6,0,.6f,height,5.2f);
        s.box(false,0,6,2.4f,8.8f,height,.4f);s.box(false,0,6,-2.4f,8.8f,height,.4f);
        if(v<3)for(int side:new int[]{-1,1})s.box(true,side*2.5f,8.5f,-2.86f,2.2f,.45f,.3f);
        if(v==3){s.box(true,0,7,-2.85f,6.5f,9,.4f);s.box(true,0,2,-2.85f,4,3,.4f);for(int side:new int[]{-1,1})s.box(true,side*2.7f,7,2.9f,3,1,.6f,0,0,side*.3f);}
        else{for(int side:new int[]{-1,1})s.box(true,side*1.8f,2.5f,-2.85f,2.3f,5,.4f,0,0,side*.23f);for(int i=0;i<4;i++)s.box(true,0,4.5f+i*2,-2.9f,.45f,.5f,.2f);}
        if(v==1)s.box(true,0,10,-2.85f,7.5f,.7f,.4f);
        if(v==2){s.box(true,0,11,-2.85f,8.5f,.8f,.5f);s.star(true,2,5,-3.1f,1.8f);}
    }
    private static void head(Shape s,int v){
        if(v==5){
            for(int side:new int[]{-1,1}){s.box(false,side*4.8f,-4,0,.8f,9.8f,10);s.box(false,0,-4,side*4.8f,10,9.8f,.8f);}
            s.box(false,0,-8.7f,0,10,.8f,10);s.box(false,0,.7f,0,10,.8f,10);s.box(true,0,-4,-5.3f,8.2f,7.8f,.25f);
        }else{
            s.box(false,0,-4,0,9.2f,8.8f,9.2f);s.box(true,0,-4,-4.7f,7,3.2f,.3f);
            if(v==0){for(int i=-1;i<=1;i++)s.box(true,i*2.7f,-7,-4.8f,.7f,.6f,.3f);for(int side:new int[]{-1,1})s.box(true,side*4.8f,-3,0,.5f,4,4.5f);}
            if(v==1){for(int side:new int[]{-1,1}){s.box(false,side*3,-9.4f,.1f,2.6f,3.4f,2);s.box(true,side*3,-9.6f,-1,1.1f,2,.2f);s.box(false,side*4,-1.5f,-3.7f,2.2f,2.2f,2);for(int i=0;i<2;i++)s.detail(false,side*2.4f,-2.3f+i*.65f,-4.95f,2.2f,.2f,.2f);}s.box(true,0,-2,-4.95f,.7f,.5f,.2f);}
            if(v==2){s.box(false,0,-1.6f,-5.8f,5,2.5f,3.5f);for(int side:new int[]{-1,1}){s.box(true,side*3,-9.5f,1,.9f,4,.9f,0,0,side*.2f);s.detail(false,side*1.1f,-1.5f,-7.65f,.45f,.4f,.2f);}}
            if(v==3){s.box(false,0,-2,-5,6.4f,3.3f,.8f);for(int i=-2;i<=2;i++)s.detail(false,i,-1.8f,-5.5f,.3f,1.7f,.2f);s.box(true,0,-9.6f,.6f,1,2.5f,7);}
            if(v==4){for(int i=0;i<16;i++){float a=(float)(i*Math.PI/8);s.box(true,(float)Math.cos(a)*3.5f,-4+(float)Math.sin(a)*3.5f,-4.9f,1,1,.4f,0,0,a);}for(int side:new int[]{-1,1})s.box(true,side*4.9f,-3,0,.7f,3,3);}
        }
        for(int side:new int[]{-1,1}){s.detail(false,side*1.5f,-4.2f,-5.35f,1.1f,.7f,.15f);s.detail(true,side*1.6f,-4.4f,-5.46f,.22f,.2f,.1f);}
    }
    private static void pants(Shape s,int v){
        float height=v==1?5.8f:10.5f;s.box(false,0,height/2,0,4.8f,height,4.8f);s.box(true,0,height-.25f,0,4.95f,.8f,4.95f);
        if(v==0)s.box(true,1.35f,2.2f,-2.25f,1.5f,2,.2f);
        if(v==1)s.box(true,0,3.2f,-2.3f,2.5f,.4f,.2f);
        if(v==2){s.star(true,0,4,-2.35f,1.5f);s.star(true,.8f,7,-2.35f,.9f);}
        if(v==3)for(int i=0;i<5;i++){s.box(true,0,i*2+.7f,-2.23f,4.4f,.45f,.15f);s.box(true,0,i*2+.7f,2.23f,4.4f,.45f,.15f);for(int side:new int[]{-1,1})s.box(true,side*1.25f,5.25f,-2.26f,.3f,10.5f,.15f);}
        if(v==4){for(int side:new int[]{-1,1})s.box(true,side*.65f,2,-2.45f,1.5f,.9f,.3f,0,0,side*.3f);s.box(true,0,2,-2.7f,.5f,.65f,.2f);}
    }
}
