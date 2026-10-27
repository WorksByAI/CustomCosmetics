// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class WindowRefreshRateTest {
    public record Legacy(int rate) { public int getRefreshRate() { return rate; } }
    public record Mode(float rate) { public float getRefreshRate() { return rate; } }
    public record Modern(Mode mode) { public Mode getActiveVideoMode() { return mode; } }

    @Test void legacyAndModernApisPreserveTheDisplayRefreshRate() {
        assertEquals(144, WindowRefreshRate.read(WindowRefreshRate.reader(Legacy.class), new Legacy(144)));
        assertEquals(165, WindowRefreshRate.read(WindowRefreshRate.reader(Modern.class), new Modern(new Mode(164.9f))));
        assertEquals(30, WindowRefreshRate.read(WindowRefreshRate.reader(Legacy.class), new Legacy(24)));
        assertEquals(1000, WindowRefreshRate.read(WindowRefreshRate.reader(Legacy.class), new Legacy(2000)));
    }

    @Test void unavailableModesHaveASafeFallback() {
        var reader = WindowRefreshRate.reader(Modern.class);
        assertEquals(60, WindowRefreshRate.read(reader, new Modern(null)));
        assertEquals(60, WindowRefreshRate.read(reader, new Modern(new Mode(Float.NaN))));
        assertEquals(60, WindowRefreshRate.read(reader, new Modern(new Mode(0))));
    }
}
