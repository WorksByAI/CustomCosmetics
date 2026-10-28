package com.customcosmetics.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UiEntranceTest {
    @Test void drawingAndPointerCoordinatesRemainInverseDuringTheSpring(){
        for(float progress:new float[]{0,.25f,.75f,1,1.12f})for(double logical:new double[]{12,28,240,780}){
            float scale=UiEntrance.scale(progress);double visible=400+(logical-400)*scale;
            assertEquals(logical,UiEntrance.pointer(visible,400,scale),.00001);
        }
        assertEquals(1,UiEntrance.scale(1));assertEquals(23,UiEntrance.pointer(23,400,1));
    }
}
