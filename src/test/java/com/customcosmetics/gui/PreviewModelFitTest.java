package com.customcosmetics.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PreviewModelFitTest {
    @Test void depthChangesAreVisibleFromTheDefaultAndSideCameras(){
        double normal=PreviewModelFit.projectedWidth(1.05,.6,WardrobeCamera.DEFAULT.yaw());
        assertTrue(PreviewModelFit.projectedWidth(1.05,1.8,WardrobeCamera.DEFAULT.yaw())>normal*1.3);
        assertEquals(.6,PreviewModelFit.projectedWidth(1.05,.6,90),.00001);
        assertEquals(1.8,PreviewModelFit.projectedWidth(1.05,1.8,90),.00001);
        assertEquals(1.05,PreviewModelFit.projectedWidth(1.05,1.8,0),.00001);
    }
    @Test void normalAndSmolModelsFillThePortraitInsteadOfKeepingTheOldTinyScale(){
        for(double[] model:new double[][]{{1.05,1.8},{.6,1.15}}){
            double scale=PreviewModelFit.scale(180,280,model[0],model[1]);
            assertTrue(scale*model[1]>280*.70);assertTrue(scale*model[1]<=280-8);assertTrue(scale*model[0]<=180-12);
        }
    }
    @Test void wideCreaturesAndExtremePlayerProportionsStayInsideBothBounds(){
        for(double[] model:new double[][]{{16,8},{4,4},{.6,2.9},{.105,5.4},{3.15,.18},{.06,.115}}){
            double scale=PreviewModelFit.scale(160,250,model[0],model[1]);
            assertTrue(Double.isFinite(scale)&&scale>0);assertTrue(scale*model[0]<=148.001);assertTrue(scale*model[1]<=242.001);
        }
    }
}
