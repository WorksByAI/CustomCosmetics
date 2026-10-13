// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.customcosmetics.features.accessories.AccessoryGeometry.*;
import static org.junit.jupiter.api.Assertions.*;

class AccessoryGeometryTest {
    @Test void pixelFeathersHaveClosedDepthAndAxisAlignedFaces(){var shape=new Builder();shape.pixelLoft(List.of(new Point(0,0,0),new Point(2,5,1),new Point(4,9,2)),new float[]{2,3,.2f},new float[]{1,1.5f,.2f},.5f,4);assertClosed(shape);assertTrue(shape.bounds().maxZ()-shape.bounds().minZ()>=2);for(var face:shape.faces()){var n=face.normal();assertEquals(1,Math.abs(n.x())+Math.abs(n.y())+Math.abs(n.z()),1e-5);for(var p:face.points())assertEquals(Math.rint(p.x()*2),p.x()*2,1e-5);}}
    @Test void pixelWingPanelSealsItsBackAndRimAndIsDeterministic(){var points=List.of(new Point(0,0,0),new Point(8,-5,2),new Point(6,4,1.2f),new Point(3,1,.5f),new Point(0,5,0));var a=new Builder();var b=new Builder();a.pixelPanel(points,.8f,.5f,12);b.pixelPanel(points,.8f,.5f,12);assertClosed(a);assertEquals(a.faces(),b.faces());assertTrue(a.bounds().maxZ()-a.bounds().minZ()>2);}
    @Test void overlappingFeathersShareOneClosedSurfaceInsteadOfFightingAtTheSameDepth(){var path=List.of(new Point(0,0,0),new Point(2,5,1),new Point(4,9,2));var widths=new float[]{2,3,.2f};var depths=new float[]{1,1.5f,.2f};var shape=new Builder();shape.pixelLoft(path,widths,depths,1,4);var count=shape.faces().size();shape.pixelLoft(path,widths,depths,1,6);assertEquals(count,shape.faces().size());assertClosed(shape);var copy=new Builder();copy.add(shape,new Point(0,0,0),0,0,0);assertEquals(shape.faces(),copy.faces());}
    @Test void invalidPixelDimensionsAreRejectedBeforeRasterization(){var points=List.of(new Point(0,0,0),new Point(4,0,0),new Point(4,4,0));for(float pixel:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY}){assertThrows(IllegalArgumentException.class,()->new Builder().pixelPanel(points,1,pixel,0));assertThrows(IllegalArgumentException.class,()->new Builder().pixelLoft(points,new float[]{1,1,1},new float[]{1,1,1},pixel,0));}}
    @Test void boundsIncludeTheRotatedModelRatherThanItsUntransformedDimensions(){var shape=new Builder();shape.box(2,10,3,.1f,0);var rotated=new Builder();rotated.add(shape,new Point(0,-4,0),0,0,(float)Math.PI/2);var bounds=rotated.bounds();assertEquals(10,bounds.maxX()-bounds.minX(),1e-4);assertEquals(-5,bounds.minY(),1e-4);assertEquals(-3,bounds.maxY(),1e-4);}
    @Test void loftHasCapsOutwardNormalsAndActualSideThickness(){
        var b=new Builder();b.loft(List.of(new Point(0,0,0),new Point(0,4,0)),new float[]{4,4},new float[]{2,2},8,4);
        for(var f:b.faces()){
            var n=f.normal();var center=f.a().plus(f.b()).plus(f.c()).plus(f.d()).times(.25f);assertEquals(1,n.length(),1e-5);
            if(Math.abs(n.y())<.01)assertTrue(n.x()*center.x()+n.z()*center.z()>0,"Loft is inside out");
        }
        assertTrue(b.faces().stream().anyMatch(f->f.normal().y()<-.99));assertTrue(b.faces().stream().anyMatch(f->f.normal().y()>.99));
        assertEquals(2,extent(b,2),1e-5);assertClosed(b);
    }
    @Test void chamferedBoxHasClosedVolumeAndDiagonalEdgeHighlights(){
        var b=new Builder();b.box(4,6,3,.2f,0);assertClosed(b);assertEquals(4,extent(b,0),1e-5);assertEquals(6,extent(b,1),1e-5);assertEquals(3,extent(b,2),1e-5);
        assertTrue(b.faces().stream().anyMatch(f->{var n=f.normal();return Math.abs(n.x())>.1&&Math.abs(n.y())>.1&&Math.abs(n.z())>.1;}));
        for(var f:b.faces()){var c=f.a().plus(f.b()).plus(f.c()).plus(f.d());var n=f.normal();assertTrue(c.x()*n.x()+c.y()*n.y()+c.z()*n.z()>0);}
    }
    @Test void curvedScallopedPanelTriangulatesBothFacesAndSealsItsRim(){
        var b=new Builder();b.panel(List.of(new Point(0,0,0),new Point(8,-5,2),new Point(6,4,1.2f),new Point(3,1,.5f),new Point(0,5,0)),.8f,12);assertClosed(b);
        assertTrue(b.faces().stream().anyMatch(f->f.normal().z()<-.5));assertTrue(b.faces().stream().anyMatch(f->f.normal().z()>.5));assertTrue(extent(b,2)>2.5f);
    }
    @Test void torusIsSolidAndNormalsAreFiniteOnBothInnerAndOuterRim(){var b=new Builder();b.torus(5,.7f,-2,8);assertClosed(b);assertEquals(.7f,extent(b,1),1e-5);for(var f:b.faces())assertEquals(1,f.normal().length(),1e-5);}
    @Test void transformsKeepFacesAndRejectInvalidInputs(){var a=new Builder();a.box(2,3,4,.1f,0);var b=new Builder();b.add(a,new Point(4,3,2),.2f,.3f,.4f);assertEquals(a.faces().size(),b.faces().size());assertClosed(b);assertThrows(IllegalArgumentException.class,()->new Point(Float.NaN,0,0));assertThrows(IllegalArgumentException.class,()->new Builder().loft(List.of(new Point(0,0,0)),new float[]{1},new float[]{1},8,0));}
    private static float extent(Builder b,int axis){var values=b.faces().stream().flatMap(f->f.points().stream()).mapToDouble(p->axis==0?p.x():axis==1?p.y():p.z()).summaryStatistics();return (float)(values.getMax()-values.getMin());}
    /** Every nondegenerate edge must have an opposite partner, including cap triangulation. */
    private static void assertClosed(Builder b){var edges=new HashMap<String,Integer>();for(var f:b.faces()){var p=f.points();for(int i=0;i<4;i++){var a=p.get(i);var z=p.get((i+1)%4);if(a.equals(z))continue;String ka=key(a),kb=key(z),edge=ka.compareTo(kb)<0?ka+"/"+kb:kb+"/"+ka;edges.merge(edge,ka.compareTo(kb)<0?1:-1,Integer::sum);}}for(var e:edges.entrySet())assertEquals(0,e.getValue(),"Unsealed surface edge: "+e.getKey());}
    private static String key(Point p){return Math.round(p.x()*10000)+","+Math.round(p.y()*10000)+","+Math.round(p.z()*10000);}
}
