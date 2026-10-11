// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.blocking;

import java.util.Locale;

/** Local first-person rendering preference; never included in remote presence data. */
public enum BlockingStyle {
    DEFAULT("Default"), JIGSAW("Jigsaw");
    public final String label;
    BlockingStyle(String label) { this.label = label; }
    public String value() { return name().toLowerCase(Locale.ROOT); }
    public static BlockingStyle parse(String value) {
        if (value != null) for (var style : values()) if (style.value().equalsIgnoreCase(value.strip())) return style;
        return DEFAULT;
    }
}
