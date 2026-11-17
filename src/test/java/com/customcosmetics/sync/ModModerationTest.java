// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModModerationTest {
    @Test void banDurationOmitsZeroUnits() {
        assertEquals("90d 59s", ModModeration.duration(90L * 86400 + 59));
        assertEquals("1d 2m", ModModeration.duration(86400 + 120));
        assertEquals("1h", ModModeration.duration(3600));
        assertEquals("0s", ModModeration.duration(0));
    }
}
