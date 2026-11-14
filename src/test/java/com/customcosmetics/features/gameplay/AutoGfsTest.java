// SPDX-License-Identifier: GPL-3.0-only
package com.customcosmetics.features.gameplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutoGfsTest {
    @Test void refillsOnlyItemsAlreadyInTheInventory() {
        for (int limit : new int[] {16, 64}) {
            assertFalse(InventoryAutomation.needsRefill(0, limit, true, 50, -1));
            assertFalse(InventoryAutomation.needsRefill(0, limit, false, 50, -1));
            assertTrue(InventoryAutomation.needsRefill(1, limit, true, 50, -1));
            assertTrue(InventoryAutomation.needsRefill(1, limit, false, 50, -1));
        }
    }

    @Test void aFullStackNeverRequestsRefill() {
        assertFalse(InventoryAutomation.needsRefill(16, 16, true, 50, -1));
        assertFalse(InventoryAutomation.needsRefill(16, 16, false, 50, -1));
    }

    @Test void unchangedInventoryDoesNotRepeatTheSameRequest() {
        assertTrue(InventoryAutomation.needsRefill(7, 16, false, 50, -1));
        assertFalse(InventoryAutomation.needsRefill(7, 16, false, 50, 7));
        assertFalse(InventoryAutomation.needsRefill(8, 16, false, 50, -1));
        assertTrue(InventoryAutomation.needsRefill(8, 16, true, 50, -1));
    }
}
