package com.customcosmetics.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PreviewModelFitTest {
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
