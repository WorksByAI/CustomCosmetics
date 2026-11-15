// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.window;

public final class WindowTitle {
    public static final int MAX_LENGTH = 120;

    private WindowTitle() {}

    public static String normalize(String value) {
        if (value == null || value.isEmpty()) return "";
        var clean = new StringBuilder();
        value.codePoints().filter(point -> !Character.isISOControl(point)).limit(MAX_LENGTH)
            .forEach(clean::appendCodePoint);
        return clean.toString().strip();
    }
}
