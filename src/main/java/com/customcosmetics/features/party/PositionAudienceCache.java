// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import com.customcosmetics.config.PlayerBlocklist;
import java.util.List;
import java.util.Objects;

/** Rebuild normalized recipient sets only when the settings change. */
public final class PositionAudienceCache {
    private boolean enabled, receive;
    private String mode;
    private List<PlayerBlocklist.Entry> targets = List.of();
    private PositionAudience value;

    public PositionAudience get(boolean enabled, String mode, List<PlayerBlocklist.Entry> targets, boolean receive) {
        String effectiveMode = enabled ? PositionAudience.mode(mode) : "party";
        List<PlayerBlocklist.Entry> effectiveTargets = enabled && effectiveMode.equals("selected") && targets != null
            ? targets : List.of();
        if (value != null && this.enabled == enabled && this.receive == receive
            && Objects.equals(this.mode, effectiveMode) && this.targets.equals(effectiveTargets)) return value;
        this.enabled = enabled;
        this.receive = receive;
        this.mode = effectiveMode;
        this.targets = List.copyOf(effectiveTargets);
        return value = !enabled && !receive ? PositionAudience.PARTY
            : PositionAudience.settings(enabled, effectiveMode, this.targets, receive);
    }
}
