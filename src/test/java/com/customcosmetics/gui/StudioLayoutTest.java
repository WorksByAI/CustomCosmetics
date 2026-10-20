package com.customcosmetics.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudioLayoutTest {
    @Test void supportedScaledWindowsKeepUsableAndSeparatedViewports() {
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {640, 360}, {800, 500}, {1920, 1080}}) {
            var layout = StudioLayout.of(size[0], size[1]);
            assertTrue(layout.x() >= 0 && layout.y() >= 0);
            assertTrue(layout.x() + layout.width() <= size[0]);
            assertTrue(layout.y() + layout.height() <= size[1]);
            assertTrue(layout.contentWidth() >= 160, "Controls must remain readable at minimum GUI size");
            assertTrue(layout.contentBottom() - layout.contentTop() >= 110);
            assertTrue(layout.navigationBottom() - layout.navigationTop() >= 100);
            assertTrue(layout.contentX() >= layout.x() + layout.sidebarWidth() + 12);
            assertTrue(layout.contentX() + layout.contentWidth() + 12 < layout.x() + layout.width());
        }
    }
    @Test void viewportHitTestsExcludeTheClippedEdges() {
        assertTrue(StudioLayout.contains(20, 30, 20, 30, 50, 60));
        assertFalse(StudioLayout.contains(50, 40, 20, 30, 50, 60));
        assertFalse(StudioLayout.contains(35, 60, 20, 30, 50, 60));
        assertFalse(StudioLayout.contains(19, 40, 20, 30, 50, 60));
    }
}
