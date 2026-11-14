// SPDX-License-Identifier: GPL-3.0-only
package com.customcosmetics.features.gameplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookCombineTest {
    private static final String WISE = "ultimate_wise";

    @Test void upgradesOnlyAnExactPairWithTheExpectedResult() {
        var one = new InventoryAutomation.Enchant(WISE, 1);
        var four = new InventoryAutomation.Enchant(WISE, 4);
        var five = new InventoryAutomation.Enchant(WISE, 5);
        var otherFour = new InventoryAutomation.Enchant("ultimate_wisdom", 4);

        assertTrue(InventoryAutomation.upgradedPair(four, four, four, five));
        assertFalse(InventoryAutomation.upgradedPair(four, four, one, five));
        assertFalse(InventoryAutomation.upgradedPair(four, one, four, five));
        assertFalse(InventoryAutomation.upgradedPair(four, four, otherFour, five));
        assertFalse(InventoryAutomation.upgradedPair(four, four, four, four));
        assertFalse(InventoryAutomation.upgradedPair(four, four, four, null));
        assertFalse(InventoryAutomation.upgradedPair(null, four, four, five));
    }
}
