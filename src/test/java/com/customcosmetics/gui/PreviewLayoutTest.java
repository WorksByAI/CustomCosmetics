package com.customcosmetics.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewLayoutTest {
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
}
