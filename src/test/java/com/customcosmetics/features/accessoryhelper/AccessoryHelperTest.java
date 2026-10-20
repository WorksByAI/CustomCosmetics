// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessoryhelper;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessoryHelperTest {
    @Test void bundledCatalogKeepsKnownFamilyOrderAndExcludesUnrelatedItems() {
        var catalog = AccessoryCatalog.bundled();
        assertTrue(catalog.size() >= 400);
        assertEquals("SPEED", catalog.get("SPEED_TALISMAN").family());
        assertEquals(4, catalog.get("SPEED_ARTIFACT").highestTier());
        assertNull(catalog.get("NOT_A_SKYBLOCK_ACCESSORY"));
    }
    @Test void fiveStatusesRespectHighestOwnedFamilyTier() {
        var catalog = AccessoryCatalog.bundled();
        var ownTalisman = catalog.highestOwned(Set.of("SPEED_TALISMAN"));
        assertEquals(AccessoryCatalog.Status.UPGRADABLE, catalog.report("SPEED_TALISMAN", ownTalisman).status());
        assertEquals(AccessoryCatalog.Status.UPGRADE, catalog.report("SPEED_RING", ownTalisman).status());
        var ownRelic = catalog.highestOwned(Set.of("SPEED_TALISMAN", "SPEED_RELIC"));
        assertEquals(AccessoryCatalog.Status.COLLECTED, catalog.report("SPEED_RELIC", ownRelic).status());
        assertEquals(AccessoryCatalog.Status.DOWNGRADE, catalog.report("SPEED_RING", ownRelic).status());
        assertEquals(AccessoryCatalog.Status.MISSING, catalog.report("SPEED_RING", Map.of()).status());
        assertEquals(2, catalog.report("SPEED_RING", ownTalisman).candidateTier());
    }
    @Test void ownershipIsPerProfileAndRequiresEveryBagPageBeforeAbsenceIsTrusted() {
        var catalog = AccessoryCatalog.bundled();
        var index = new AccessoryBagIndex(catalog);
        assertTrue(index.update("player:Coconut", 1, 2, Set.of("SPEED_TALISMAN")));
        assertFalse(index.view("player:Coconut").complete());
        assertEquals(1, index.view("player:Coconut").highest().get("SPEED"));
        assertFalse(index.update("player:Coconut", 1, 2, Set.of("SPEED_TALISMAN")));
        assertTrue(index.update("player:Coconut", 2, 2, Set.of("ABICASE")));
        assertTrue(index.view("player:Coconut").complete());
        assertFalse(index.view("player:Apple").complete());
        var restored = AccessoryBagIndex.decode(index.encode(), catalog);
        assertEquals(index.view("player:Coconut"), restored.view("player:Coconut"));
        assertTrue(restored.update("player:Coconut", 1, 2, Set.of("SPEED_ARTIFACT")));
        assertEquals(3, restored.view("player:Coconut").highest().get("SPEED"));
        assertTrue(restored.clear("player:Coconut"));
        assertFalse(restored.view("player:Coconut").complete());
    }
    @Test void changingPageCountInvalidatesEarlierCompleteSnapshot() {
        var index = new AccessoryBagIndex(AccessoryCatalog.bundled());
        index.update("player:Coconut", 1, 1, Set.of("SPEED_ARTIFACT"));
        assertTrue(index.view("player:Coconut").complete());
        index.update("player:Coconut", 1, 2, Set.of());
        assertFalse(index.view("player:Coconut").complete());
        assertTrue(index.view("player:Coconut").highest().isEmpty());
    }
}
