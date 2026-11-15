// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowAppearanceTest {
    @Test void usesANativeIconSizeAtCommonDisplayScales() {
        assertEquals(32, WindowAppearance.taskbarIconSize(1));
        assertEquals(40, WindowAppearance.taskbarIconSize(1.25f));
        assertEquals(48, WindowAppearance.taskbarIconSize(1.5f));
        assertEquals(64, WindowAppearance.taskbarIconSize(2));
        assertEquals(32, WindowAppearance.taskbarIconSize(Float.NaN));
        assertEquals(256, WindowAppearance.taskbarIconSize(9));
    }
}
