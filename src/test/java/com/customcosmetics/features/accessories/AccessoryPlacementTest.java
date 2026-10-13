// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.customcosmetics.features.accessories.AccessoryGeometry.*;
class AccessoryPlacementTest {
    @Test void wingsSweepBehindThePlayerAtEveryFlapPhaseAndSize(){
        var b=new Bounds(-1,-16,-2,23,14,6);
        for(double size:new double[]{.5,1,2})for(int side:new int[]{-1,1})for(int phase=0;phase<300;phase++){
            var m=AccessoryPlacement.wing(b,size,.6,phase,3,side);
            assertTrue(m.yaw()*side<0,"Wrong yaw folds mirrored wings through the chest");
            for(float x:new float[]{b.minX(),b.maxX()})for(float z:new float[]{b.minZ(),b.maxZ()}){
                double rear=-Math.sin(m.yaw())*side*x*size/16+Math.cos(m.yaw())*z*size/16+m.z();
                assertTrue(rear>=.6+AccessoryPlacement.GAP-1e-6);
            }
        }
    }
    @Test void backItemsAndWingsStackOutsideTheCoat(){
        var coat=new Bounds(-4,0,-3,4,13,3);var pack=new Bounds(-4,0,2,4,10,7);
        var settings=AccessoriesSettings.NONE.with("outfit",AccessoriesSettings.NONE.part("outfit").enabled(true)).with("back",AccessoriesSettings.NONE.part("back").enabled(true).scale(2));
        double coatRear=AccessoryPlacement.rear(settings,.15,s->s.kind().equals("outfit")?coat:pack);
        double packOffset=AccessoryPlacement.backOffset(pack,2,coatRear);
        assertEquals(coatRear+AccessoryPlacement.GAP,packOffset+pack.minZ()*2/16,1e-6);
        assertEquals(packOffset+pack.maxZ()*2/16,AccessoryPlacement.wingRear(settings,.15,s->s.kind().equals("outfit")?coat:pack),1e-6);
    }
    @Test void tailsKeepTheirOwnCorridorBetweenBothWings(){
        var wing=new Bounds(-1,-16,-2,23,14,6);var tail=new Bounds(-3,5,2,7,14,12);
        var settings=AccessoriesSettings.NONE.with("tail",AccessoriesSettings.NONE.part("tail").enabled(true));
        double center=AccessoryPlacement.wingX(settings,wing,1,s->tail);
        double tailWidth=0;for(float angle:new float[]{-.13f,.13f}){var b=AccessoryPlacement.rotated(tail,0,angle,0);tailWidth=Math.max(tailWidth,Math.max(Math.abs(b.minX()),Math.abs(b.maxX()))/16);}
        for(int phase=0;phase<300;phase++){var m=AccessoryPlacement.wing(wing,1,.6,phase,3,1);double angle=-m.yaw();double inner=center+(Math.cos(angle)*wing.minX()-Math.sin(angle)*wing.maxZ())/16;assertTrue(inner>=tailWidth+AccessoryPlacement.GAP-1e-6);}
    }
    @Test void sleevesFitTheAsymmetricWideAndSlimArmCentersWithoutGrowingInLength(){
        for(float[] arm:new float[][]{{-3,1},{-1,3},{-2,1},{-1,2}})for(double size:new double[]{.5,1,2}){
            var b=new Bounds(arm[0],-2,-2,arm[1],10,2);
            var fit=AccessoryPlacement.garment(b,size,0,4.8,12.8,4.8,4,true);
            assertEquals((arm[0]+arm[1])/32,fit.centerX(),1e-6);
            assertTrue(fit.x()*4.8>b.maxX()-b.minX()+.5);
            assertEquals(1,fit.y(),1e-6);
        }
    }
    @Test void headLayersPreserveSelectionsWhileFullMasksCoverHairAndGlasses(){
        var settings=AccessoriesSettings.NONE.with("head",AccessoriesSettings.NONE.part("head").enabled(true)).with("hair",AccessoriesSettings.NONE.part("hair").enabled(true));
        assertTrue(AccessoryPlacement.covered("hair",settings));assertTrue(AccessoryPlacement.covered("face",settings));assertFalse(AccessoryPlacement.covered("hat",settings));assertTrue(settings.part("hair").enabled());
    }
    @Test void hatBrimAndEarsAreSeparatedAtAllSupportedSizes(){
        var hat=new Bounds(-7,-11,-7,7,1,7);var ear=new Bounds(-3,-7,-2,3,1,2);
        for(double size:new double[]{.5,1,2}){
            var settings=AccessoriesSettings.NONE.with("hat",AccessoriesSettings.NONE.part("hat").enabled(true).scale(size)).with("ears",AccessoriesSettings.EARS.enabled(true).scale(size));
            var rotated=AccessoryPlacement.rotated(ear,0,0,.13f);
            assertTrue(AccessoryHeadLayout.earX(settings,s->s.kind().equals("hat")?hat:ear)+rotated.minX()*size/16>=hat.maxX()*size/16+AccessoryPlacement.GAP-1e-6);
            double hatBottom=-AccessoryHeadLayout.hatHeight(settings,false,s->s.kind().equals("hat")?hat:ear)+hat.maxY()*size/16;
            assertTrue(hatBottom<-.525);
        }
    }
}
