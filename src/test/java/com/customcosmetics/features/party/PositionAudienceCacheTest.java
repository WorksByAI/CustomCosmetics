package com.customcosmetics.features.party;

import com.customcosmetics.config.PlayerBlocklist;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PositionAudienceCacheTest {
    @Test void reuseNormalizedRecipientsAndInvalidateEveryPermissionChange() {
        var cache = new PositionAudienceCache();
        var alice = new PlayerBlocklist.Entry(null, "Alice");
        var bob = new PlayerBlocklist.Entry(null, "Bob");
        var targets = new ArrayList<>(List.of(alice));
        var original = cache.get(true, "selected", targets, false);
        for (int i = 0; i < 1000; i++) assertSame(original, cache.get(true, "selected", targets, false));
        // Also catches callers mutating a list in place rather than replacing it.
        targets.set(0, bob);
        var changed = cache.get(true, "selected", targets, false);
        assertNotSame(original, changed);
        assertFalse(changed.sendsTo(UUID.randomUUID(), "Alice", PartyRoster.EMPTY, false));
        assertTrue(changed.sendsTo(UUID.randomUUID(), "Bob", PartyRoster.EMPTY, false));
        assertTrue(cache.get(true, "selected", targets, true).receive());
        assertEquals("public", cache.get(true, "public", targets, true).mode());
        assertSame(PositionAudience.PARTY, cache.get(false, "public", targets, false));
        assertFalse(cache.get(true, "selected", List.of(), false).publishes(PartyRoster.EMPTY, false));
    }
    @Test void unusedRecipientsDoNotInvalidatePublicOrDisabledAudience() {
        var cache = new PositionAudienceCache();
        var audience = cache.get(true, "public", List.of(), true);
        assertSame(audience, cache.get(true, "public", List.of(new PlayerBlocklist.Entry(null, "Alice")), true));
        assertSame(PositionAudience.PARTY, cache.get(false, "selected", List.of(), false));
    }
}
