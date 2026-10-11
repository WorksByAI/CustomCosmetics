// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.blocking;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockhitClockTest {
    @Test void slowSpeedAlsoSlowsRecoveryInsteadOfWhippingThroughHalfTheArcInOneTick() {
        var clock = new BlockhitClock();
        for (int tick = 0; tick <= 22; tick++) clock.tick(true, 44);
        assertEquals(.5f, clock.progress(1), .00001);
        clock.tick(true, 44); assertEquals(.5f + .5f / 7, clock.progress(1), .00001);
        float previous = clock.progress(1);
        for (int tick = 24; tick <= 29; tick++) {
            clock.tick(true, 44); float current = clock.progress(1);
            assertTrue(current > previous && current - previous < .08f); previous = current;
        }
        assertEquals(1, previous, .00001);
    }
    @Test void repeatsWhileHeldWithoutVanillaSwingEvents() {
        var clock = new BlockhitClock();
        for (int tick = 0; tick < 60; tick++) {
            clock.tick(true, 6);
            float end = tick > 0 && tick % 4 == 0 ? 1 : (tick % 4) / 6f;
            assertEquals(end, clock.progress(1), .00001);
        }
    }
    @Test void releaseOrTargetChangeStopsAndNextPressStartsAFreshCycle() {
        var clock = new BlockhitClock(); clock.tick(true, 6); clock.tick(true, 6);
        assertTrue(clock.progress(.5f) > 0);
        clock.tick(false, 6); assertEquals(0, clock.progress(.5f));
        clock.tick(true, 6); assertEquals(0, clock.progress(.5f));
    }
    @Test void durationChangesAndExtremePartialTicksStayWithinTheCycle() {
        var clock = new BlockhitClock();
        for (int tick = 0; tick < 10; tick++) clock.tick(true, 10);
        clock.tick(true, 3); assertEquals(1, clock.progress(1), .00001);
        for (int duration : new int[] {0, 1, 3, 12}) {
            clock.tick(true, duration);
            assertTrue(clock.progress(-2) >= 0 && clock.progress(2) <= 1);
        }
    }
    @Test void retriggerCompletesTheRemainingArcWithinOneRenderTick() {
        var clock = new BlockhitClock();
        for (int tick = 0; tick < 4; tick++) clock.tick(true, 6);
        assertEquals(.5f, clock.progress(1), .00001);
        clock.tick(true, 6);
        assertEquals(.5f, clock.progress(0), .00001);
        assertEquals(.75f, clock.progress(.5f), .00001);
        assertEquals(1f, clock.progress(1), .00001);
        clock.tick(true, 6); assertEquals(0, clock.progress(0));
    }
}
