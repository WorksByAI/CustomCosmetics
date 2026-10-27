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
    @Test void compactPreviewFitsItsViewportWhileThePinnedModelKeepsItsScale() {
        assertEquals(184, PreviewLayout.playerCardHeight(180, true));
        assertEquals(52, PreviewLayout.playerModelScale(184));
        for (int viewport : new int[]{120, 150, 184, 200, 400}) {
            int height = PreviewLayout.playerCardHeight(viewport, false);
            assertTrue(height + 8 <= viewport);
            float expected = 52f * (height - 57) / 127;
            assertEquals(expected, PreviewLayout.playerModelScale(height), .5f);
        }
    }
}
