// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.blocking;

import java.util.Locale;

/** Local first-person rendering preference; never included in remote presence data. */
public enum BlockingStyle {
    DEFAULT("Vanilla"), JIGSAW("Smooth");
    public final String label;
    private final String value;
    private static final BlockingStyle[] OPTIONS=values();
    BlockingStyle(String label) { this.label = label; this.value=name().toLowerCase(Locale.ROOT); }
    public String value() { return value; }
    public static BlockingStyle parse(String value) {
        if (value != null) { value=value.strip();for (var style : OPTIONS) if (style.value.equalsIgnoreCase(value)||style.label.equalsIgnoreCase(value)) return style; }
        return DEFAULT;
    }
}
