// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.shortcuts;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShortcutKeyStateTest {
    private ShortcutKeyState.Binding<String> binding(String id, String... keys) {
        return new ShortcutKeyState.Binding<>(id, List.of(keys), id);
    }
    @Test void oneFreshPressSendsOnceAndRepeatCannotRetrigger() {
        var state = new ShortcutKeyState<>(List.of(binding("hub", "H")));
        assertEquals("hub", state.event("H", true, false, true));
        assertNull(state.event("H", true, true, true));
        assertNull(state.event("H", true, false, true));
        assertNull(state.event("H", false, false, true));
        assertEquals("hub", state.event("H", true, false, true));
    }
    @Test void combinationWinsOverSingleKeyAndSupportsMouse() {
        var state = new ShortcutKeyState<>(List.of(binding("single", "Mouse4"), binding("combo", "Ctrl", "Mouse4")));
        assertNull(state.event("Ctrl", true, false, true));
        assertEquals("combo", state.event("Mouse4", true, false, true));
        assertNull(state.event("Mouse4", true, true, true));
        state.event("Mouse4", false, false, true);
        assertEquals("combo", state.event("Mouse4", true, false, true));
    }
    @Test void newUnrelatedKeyDoesNotFireHeldChordAgain() {
        var state = new ShortcutKeyState<>(List.of(binding("combo", "Ctrl", "K")));
        state.event("Ctrl", true, false, true);
        assertEquals("combo", state.event("K", true, false, true));
        assertNull(state.event("W", true, false, true));
    }
    @Test void guiAndConnectionGateClearsHeldInputs() {
        var state = new ShortcutKeyState<>(List.of(binding("combo", "Ctrl", "K")));
        state.event("Ctrl", true, false, true);
        assertNull(state.event("K", true, false, false));
        assertNull(state.event("K", true, false, true));
        state.clear();
        assertNull(state.event("Ctrl", true, false, false));
        assertNull(state.event("K", true, false, false));
    }
    @Test void emptyStateDoesNotAccumulateKeys() {
        var state = new ShortcutKeyState<String>(List.of());
        for (int i = 0; i < 1000; i++) assertNull(state.event("key" + i, true, false, true));
        assertTrue(state.empty());
    }
}
