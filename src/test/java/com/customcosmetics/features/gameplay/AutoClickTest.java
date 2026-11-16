// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoClickTest {
    @Test void jitterDoesNotExceedTheTickRateOrStopClickingEntirely() {
        assertEquals(1, AutoClick.adjustedCps(5, -6));
        assertEquals(12, AutoClick.adjustedCps(10, 2));
        assertEquals(20, AutoClick.adjustedCps(20, 6));
    }

    @Test void theRequiredKeyCanGateBothClicksOrInvertTheCondition() {
        assertTrue(AutoClick.requirementAllows(false, false, false));
        assertTrue(AutoClick.requirementAllows(false, true, true));
        assertFalse(AutoClick.requirementAllows(true, false, false));
        assertTrue(AutoClick.requirementAllows(true, false, true));
        assertTrue(AutoClick.requirementAllows(true, true, false));
        assertFalse(AutoClick.requirementAllows(true, true, true));
    }
}
