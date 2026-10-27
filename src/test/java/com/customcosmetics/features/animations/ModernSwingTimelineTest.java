// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.animations;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ModernSwingTimelineTest {
    @Test void modernDurationIsFrozenAndCompletionKeepsTheNativeEndpointFrame() {
        var clock = new SwingTimeline(); clock.modern(true); clock.request(2);
        clock.tick(99); assertEquals(0, clock.progress(1));
        clock.tick(99); assertEquals(.5f, clock.progress(1));
        clock.tick(99); assertEquals(1, clock.progress(1));
        clock.tick(99); assertEquals(1, clock.progress(0)); assertEquals(1, clock.progress(1));
        clock.tick(99); assertEquals(0, clock.progress(1));
    }

    @Test void restartEligibilityUsesTheActiveDurationAndNewAnimationsResetTheirPhase() {
        var clock = new SwingTimeline(); clock.modern(true); clock.request(6);
        for (int i = 0; i < 3; i++) clock.tick(6);
        assertFalse(clock.request(1));
        clock.tick(6); assertTrue(clock.request(6, false));
        assertEquals(0, clock.progress(0)); assertEquals(0, clock.progress(1));
    }
}
