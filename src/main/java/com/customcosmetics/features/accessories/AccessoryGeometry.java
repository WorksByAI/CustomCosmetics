// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;

import java.util.*;

/** Original closed surfaces in model pixels, independent of the game's renderer. */
public final class AccessoryGeometry {
    public record Point(float x,float y,float z) {
        public Point {
            if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Non-finite accessory vertex");
        }
        public Point plus(Point p){return new Point(x+p.x,y+p.y,z+p.z);}
        public Point times(float s){return new Point(x*s,y*s,z*s);}
        public Point minus(Point p){return plus(p.times(-1));}
        public Point cross(Point p){return new Point(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
        public float length(){return (float)Math.sqrt(x*x+y*y+z*z);}
        public Point unit(){float l=length();if(l<1e-6f)throw new IllegalArgumentException("Degenerate accessory normal");return times(1/l);}
        public Point rotate(float rx,float ry,float rz){
            float a=y*cos(rx)-z*sin(rx),b=y*sin(rx)+z*cos(rx),c=x*cos(ry)+b*sin(ry),d=-x*sin(ry)+b*cos(ry);
            return new Point(c*cos(rz)-a*sin(rz),c*sin(rz)+a*cos(rz),d);
        }
    }
    public record Face(Point a,Point b,Point c,Point d,int material) {
        public Point normal(){return b.minus(a).cross(c.minus(a)).unit();}
        public List<Point> points(){return List.of(a,b,c,d);}
    }
    public record Bounds(float minX,float minY,float minZ,float maxX,float maxY,float maxZ){}
    private record Cell(int x,int y,int z){Cell neighbor(int dx,int dy,int dz){return new Cell(x+dx,y+dy,z+dz);}}
    public static final class Builder {
        private final List<Face> faces=new ArrayList<>();
        private final Map<Float,Map<Cell,Integer>> pixelVolumes=new TreeMap<>();
        private List<Face> compiled;
        public List<Face> faces(){if(compiled==null){var result=new ArrayList<>(faces);for(var volume:pixelVolumes.entrySet())result.addAll(pixelFaces(volume.getValue(),volume.getKey()));compiled=List.copyOf(result);}return compiled;}
        public Bounds bounds(){var surfaces=faces();if(surfaces.isEmpty())return new Bounds(0,0,0,0,0,0);float minX=Float.MAX_VALUE,minY=minX,minZ=minX,maxX=-minX,maxY=-minX,maxZ=-minX;for(var f:surfaces)for(var p:f.points()){minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);minZ=Math.min(minZ,p.z);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);maxZ=Math.max(maxZ,p.z);}return new Bounds(minX,minY,minZ,maxX,maxY,maxZ);}
        public void quad(Point a,Point b,Point c,Point d,int material){if(b.minus(a).cross(c.minus(a)).length()>1e-5f){faces.add(new Face(a,b,c,d,material));compiled=null;}}
        public void triangle(Point a,Point b,Point c,int material){quad(a,b,c,c,material);}
        public void add(Builder source,Point offset,float rx,float ry,float rz){for(var f:source.faces())quad(f.a.rotate(rx,ry,rz).plus(offset),f.b.rotate(rx,ry,rz).plus(offset),f.c.rotate(rx,ry,rz).plus(offset),f.d.rotate(rx,ry,rz).plus(offset),f.material);}
        /** Closed pixel volumes preserve depth without smooth, miniature-looking surfaces. */
        public void pixelLoft(List<Point> path,float[] widths,float[] depths,float pixel,int material){
            if(path.size()!=widths.length||path.size()!=depths.length||path.size()<2||!Float.isFinite(pixel)||pixel<=0)throw new IllegalArgumentException("Invalid pixel loft");
            for(int i=0;i<widths.length;i++)if(!Float.isFinite(widths[i])||!Float.isFinite(depths[i])||widths[i]<=0||depths[i]<=0)throw new IllegalArgumentException("Invalid pixel cross section");
            var cells=new HashSet<Cell>();
            for(int segment=0;segment<path.size()-1;segment++){
                var a=path.get(segment);var b=path.get(segment+1);var delta=b.minus(a);var tangent=delta.unit();Point transverse=new Point(0,0,1).cross(tangent);if(transverse.length()<.05f)transverse=new Point(0,1,0).cross(tangent);transverse=transverse.unit();var depth=tangent.cross(transverse).unit();int samples=Math.max(1,(int)Math.ceil(delta.length()/pixel*3));
                for(int sample=0;sample<=samples;sample++){
                    float t=sample/(float)samples;var center=a.plus(delta.times(t));float halfW=Math.max(pixel*.52f,(widths[segment]*(1-t)+widths[segment+1]*t)/2),halfD=Math.max(pixel*.52f,(depths[segment]*(1-t)+depths[segment+1]*t)/2),radius=Math.max(halfW,halfD)+pixel*.4f;
                    for(int x=(int)Math.floor((center.x-radius)/pixel);x<=(int)Math.floor((center.x+radius)/pixel);x++)for(int y=(int)Math.floor((center.y-radius)/pixel);y<=(int)Math.floor((center.y+radius)/pixel);y++)for(int z=(int)Math.floor((center.z-radius)/pixel);z<=(int)Math.floor((center.z+radius)/pixel);z++){
                        var relative=new Point((x+.5f)*pixel,(y+.5f)*pixel,(z+.5f)*pixel).minus(center);float along=dot(relative,tangent),across=dot(relative,transverse),deep=dot(relative,depth);
                        if(Math.abs(along)<=pixel*.7f&&across*across/(halfW*halfW)+deep*deep/(halfD*halfD)<=1.25f)cells.add(new Cell(x,y,z));
                    }
                }
            }
            pixels(cells,pixel,material);
        }
        public void pixelPanel(List<Point> outline,float thickness,float pixel,int material){
            if(!Float.isFinite(pixel)||pixel<=0||!Float.isFinite(thickness)||thickness<=0)throw new IllegalArgumentException("Invalid pixel panel");
            var shape=new Builder();shape.panel(outline,thickness,material);var bounds=shape.bounds();var front=shape.faces.stream().filter(face->face.normal().z<-.05f).toList();var cells=new HashSet<Cell>();
            for(int x=(int)Math.floor(bounds.minX/pixel);x<=(int)Math.ceil(bounds.maxX/pixel);x++)for(int y=(int)Math.floor(bounds.minY/pixel);y<=(int)Math.ceil(bounds.maxY/pixel);y++){
                float px=(x+.5f)*pixel,py=(y+.5f)*pixel;
                for(var face:front){Point p=new Point(px,py,0);float area=cross2(face.a,face.b,face.c);float a=cross2(p,face.b,face.c)/area,b=cross2(face.a,p,face.c)/area,c=1-a-b;
                    if(a<-.0001f||b<-.0001f||c<-.0001f)continue;float surface=a*face.a.z+b*face.b.z+c*face.c.z+thickness/2,half=Math.max(pixel*.52f,thickness/2);
                    for(int z=(int)Math.floor((surface-half)/pixel);z<=(int)Math.floor((surface+half)/pixel);z++)cells.add(new Cell(x,y,z));break;
                }
            }
            pixels(cells,pixel,material);
        }
        private void pixels(Set<Cell> cells,float pixel,int material){
            var volume=pixelVolumes.computeIfAbsent(pixel,ignored->new HashMap<>());for(var cell:cells)volume.put(cell,material);compiled=null;
        }
        private static List<Face> pixelFaces(Map<Cell,Integer> cells,float pixel){
            var unit=new Builder();unit.box(1,1,1,0,0);var result=new ArrayList<Face>();
            var sorted=cells.keySet().stream().sorted(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z)).toList();
            for(var cell:sorted)for(var face:unit.faces){var normal=face.normal();if(cells.containsKey(cell.neighbor(Math.round(normal.x),Math.round(normal.y),Math.round(normal.z))))continue;var offset=new Point((cell.x+.5f)*pixel,(cell.y+.5f)*pixel,(cell.z+.5f)*pixel);result.add(new Face(face.a.times(pixel).plus(offset),face.b.times(pixel).plus(offset),face.c.times(pixel).plus(offset),face.d.times(pixel).plus(offset),cells.get(cell)));}
            return result;
        }
        /** Chamfered boxes retain the Minecraft silhouette while catching light on their edges. */
        public void box(float w,float h,float d,float bevel,int material){
            float[] half={w/2,h/2,d/2};float r=Math.min(bevel,Math.min(w,Math.min(h,d))*.22f);
            if(r<=0){for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1})plane(axis,sign,half,0,material);return;}
            for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1})plane(axis,sign,half,r,material);
            for(int axis=0;axis<3;axis++)for(int sa:new int[]{-1,1})for(int sb:new int[]{-1,1}){
                int a=(axis+1)%3,b=(axis+2)%3;float[][] coords={{-half[axis]+r,sa*half[a],sb*(half[b]-r)},{half[axis]-r,sa*half[a],sb*(half[b]-r)},{half[axis]-r,sa*(half[a]-r),sb*half[b]},{-half[axis]+r,sa*(half[a]-r),sb*half[b]}};
                Point[] p=new Point[4];for(int i=0;i<4;i++)p[i]=axes(axis,coords[i][0],coords[i][1],coords[i][2]);outward(p,material+1);
            }
            for(int sx:new int[]{-1,1})for(int sy:new int[]{-1,1})for(int sz:new int[]{-1,1}){
                Point a=new Point(sx*half[0],sy*(half[1]-r),sz*(half[2]-r)),b=new Point(sx*(half[0]-r),sy*half[1],sz*(half[2]-r)),c=new Point(sx*(half[0]-r),sy*(half[1]-r),sz*half[2]);outward(new Point[]{a,b,c,c},material+1);
            }
        }
        private void plane(int axis,int sign,float[] half,float inset,int material){int a=(axis+1)%3,b=(axis+2)%3;outward(new Point[]{axes(axis,sign*half[axis],-half[a]+inset,-half[b]+inset),axes(axis,sign*half[axis],half[a]-inset,-half[b]+inset),axes(axis,sign*half[axis],half[a]-inset,half[b]-inset),axes(axis,sign*half[axis],-half[a]+inset,half[b]-inset)},material);}
        private void outward(Point[] p,int material){Point n=p[1].minus(p[0]).cross(p[2].minus(p[0])),center=p[0].plus(p[1]).plus(p[2]).plus(p[3]);if(n.x*center.x+n.y*center.y+n.z*center.z<0){if(p[2].equals(p[3]))triangle(p[2],p[1],p[0],material);else quad(p[3],p[2],p[1],p[0],material);}else quad(p[0],p[1],p[2],p[3],material);}
        /** A closed elliptical loft: feathers, fur, horns, petals and ribs have real thickness. */
        public void loft(List<Point> path,float[] widths,float[] depths,int sides,int material){
            if(path.size()!=widths.length||path.size()!=depths.length||path.size()<2||sides<4)throw new IllegalArgumentException("Invalid loft");
            List<Point[]> rings=new ArrayList<>();
            for(int i=0;i<path.size();i++){
                Point tangent=path.get(Math.min(i+1,path.size()-1)).minus(path.get(Math.max(i-1,0))).unit();
                Point transverse=new Point(0,0,1).cross(tangent);if(transverse.length()<.05f)transverse=new Point(0,1,0).cross(tangent);transverse=transverse.unit();Point depth=tangent.cross(transverse).unit();
                Point[] ring=new Point[sides];for(int j=0;j<sides;j++){float a=j*(float)Math.PI*2/sides;ring[j]=path.get(i).plus(transverse.times(cos(a)*widths[i]/2)).plus(depth.times(sin(a)*depths[i]/2));}rings.add(ring);
            }
            for(int i=0;i<rings.size()-1;i++)for(int j=0;j<sides;j++){int k=(j+1)%sides;quad(rings.get(i)[j],rings.get(i)[k],rings.get(i+1)[k],rings.get(i+1)[j],material+(j%3==0?1:0));}
            for(int j=0;j<sides;j++){int k=(j+1)%sides;triangle(path.getFirst(),rings.getFirst()[k],rings.getFirst()[j],material);triangle(path.getLast(),rings.getLast()[j],rings.getLast()[k],material+1);}
        }
        public void torus(float radius,float thickness,float y,int material){
            int segments=32,sides=8;for(int i=0;i<segments;i++)for(int j=0;j<sides;j++)quad(torusPoint(radius,thickness,y,i,j,segments,sides),torusPoint(radius,thickness,y,i,j+1,segments,sides),torusPoint(radius,thickness,y,i+1,j+1,segments,sides),torusPoint(radius,thickness,y,i+1,j,segments,sides),material+(j<4?1:0));
        }
        /** Ear-clipped, closed extrusion supports scalloped wing outlines without gaps. */
        public void panel(List<Point> outline,float thickness,int material){
            if(outline.size()<3||thickness<=0)throw new IllegalArgumentException("Invalid panel");
            var points=new ArrayList<>(outline);double area=0;for(int i=0;i<points.size();i++){var a=points.get(i);var b=points.get((i+1)%points.size());area+=a.x*b.y-b.x*a.y;}if(area<0)Collections.reverse(points);
            var remaining=new ArrayList<Integer>();for(int i=0;i<points.size();i++)remaining.add(i);
            while(remaining.size()>2){boolean clipped=false;for(int i=0;i<remaining.size();i++){
                int a=remaining.get(Math.floorMod(i-1,remaining.size())),b=remaining.get(i),c=remaining.get((i+1)%remaining.size());var pa=points.get(a);var pb=points.get(b);var pc=points.get(c);if(cross2(pa,pb,pc)<=1e-5)continue;
                boolean occupied=false;for(int other:remaining)if(other!=a&&other!=b&&other!=c&&inside(points.get(other),pa,pb,pc)){occupied=true;break;}if(occupied)continue;
                var front=new Point(0,0,-thickness/2);var back=new Point(0,0,thickness/2);triangle(pa.plus(front),pc.plus(front),pb.plus(front),material);triangle(pa.plus(back),pb.plus(back),pc.plus(back),material+1);remaining.remove(i);clipped=true;break;
            }if(!clipped)throw new IllegalArgumentException("Self intersecting accessory outline");}
            for(int i=0;i<points.size();i++){var a=points.get(i);var b=points.get((i+1)%points.size());var front=new Point(0,0,-thickness/2);var back=new Point(0,0,thickness/2);quad(a.plus(front),b.plus(front),b.plus(back),a.plus(back),material+2);}
        }
    }
    private static Point axes(int axis,float a,float b,float c){return switch(axis){case 0->new Point(a,b,c);case 1->new Point(c,a,b);default->new Point(b,c,a);};}
    private static Point torusPoint(float r,float thickness,float y,int i,int j,int count,int sides){float a=i*(float)Math.PI*2/count,b=j*(float)Math.PI*2/sides;return new Point(cos(a)*(r+cos(b)*thickness/2),y+sin(b)*thickness/2,sin(a)*(r+cos(b)*thickness/2));}
    private static float cross2(Point a,Point b,Point c){return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);}
    private static boolean inside(Point p,Point a,Point b,Point c){return cross2(a,b,p)>=-1e-5&&cross2(b,c,p)>=-1e-5&&cross2(c,a,p)>=-1e-5;}
    private static float dot(Point a,Point b){return a.x*b.x+a.y*b.y+a.z*b.z;}
    private static float sin(float a){return (float)Math.sin(a);}private static float cos(float a){return (float)Math.cos(a);}
    private AccessoryGeometry(){}
}
