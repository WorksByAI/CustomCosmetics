// SPDX-License-Identifier: GPL-3.0-only
package com.customcosmetics.features.gameplay;

import java.util.Map;
import java.util.Set;

public record AutoLeapPreset(boolean byName, boolean swapBack, int clickDelay, boolean waitForGate, boolean melody,
                             boolean stopAfterBlood, Set<String> triggers, Map<String, String> names,
                             Map<String, String> classes, Fast fast) {
    public AutoLeapPreset {
        clickDelay = Math.clamp(clickDelay, 0, 10);
        triggers = triggers == null ? Set.of() : Set.copyOf(triggers);
        names = names == null ? Map.of() : Map.copyOf(names);
        classes = classes == null ? Map.of() : Map.copyOf(classes);
    }

    public record Fast(String fallback, Set<String> triggers, Map<String, String> names,
                       Map<String, String> classes, boolean stopAfterBlood, boolean melody) {
        public Fast {
            fallback = fallback == null || !fallback.matches("[A-Za-z0-9_]{1,16}") ? "" : fallback;
            triggers = triggers == null ? Set.of() : Set.copyOf(triggers);
            names = names == null ? Map.of() : Map.copyOf(names);
            classes = classes == null ? Map.of() : Map.copyOf(classes);
        }
    }
}
