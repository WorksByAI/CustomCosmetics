package com.customcosmetics.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewLayoutTest {
    @Test void largeCreaturesFitThePreviewWithoutShrinkingOrdinaryModels() {
        assertEquals(52, PreviewLayout.creatureModelScale(184, 280, .6f, 1.8f));
        for (float[] bounds : new float[][]{{16,8},{4,4},{3.6f,12},{.4f,.7f}}) {
            int scale = PreviewLayout.creatureModelScale(184, 280, bounds[0], bounds[1]);
            assertTrue(scale > 0 && scale <= 52);
            assertTrue(scale * bounds[0] * 1.4f <= 280);
            assertTrue(scale * bounds[1] * 1.2f <= 127);
        }
    }
    @Test void fullNamesFitWideAndNarrowPreviewsWithoutTruncatingTheText() {
        for (int available : new int[]{120, 220, 460, 580}) {
            for (int text : new int[]{1, 96, 300, 768}) {
                float scale = PreviewLayout.nameScale(text, available);
                assertTrue(scale > 0 && scale <= 1.35f);
                assertTrue(text * scale <= available + .001f, text + " into " + available);
            }
        }
        assertEquals(1.35f, PreviewLayout.nameScale(96, 580));
        assertTrue(Float.isFinite(PreviewLayout.nameScale(0, 0)));
    }
    @Test void widePortraitSharesOneViewportWithCompactControls() {
        for(int width:new int[]{430,520,634})for(int viewport:new int[]{180,300,420}){
            var panel=PreviewLayout.playerPanel(100,88,width,viewport);
            assertTrue(panel.sideBySide());assertEquals(100,panel.previewX());assertEquals(88,panel.previewY());
            assertTrue(panel.previewWidth()>0&&panel.previewWidth()<=190);assertTrue(panel.previewHeight()<=360);assertTrue(panel.previewHeight()+16<=viewport);
            assertTrue(panel.previewHeight()>panel.previewWidth());assertEquals(panel.previewX()+panel.previewWidth()+16,panel.controlsX());
            assertEquals(panel.previewY(),panel.controlsY());assertEquals(100+width,panel.controlsX()+panel.controlsWidth());assertTrue(panel.controlsWidth()>=254);
        }
    }
    @Test void narrowPortraitStaysCenteredAndScrollsWithTheControlsBelow() {
        for(int width:new int[]{180,267,400})for(int viewport:new int[]{116,240,400}){
            var panel=PreviewLayout.playerPanel(30,88,width,viewport);
            assertFalse(panel.sideBySide());assertTrue(panel.previewWidth()<=180);assertTrue(panel.previewHeight()>panel.previewWidth());assertTrue(panel.previewHeight()+16<=viewport);
            assertEquals(30+(width-panel.previewWidth())/2,panel.previewX());assertEquals(30,panel.controlsX());assertEquals(width,panel.controlsWidth());
            assertEquals(panel.previewY()+panel.previewHeight()+14,panel.controlsY());
        }
    }
}
