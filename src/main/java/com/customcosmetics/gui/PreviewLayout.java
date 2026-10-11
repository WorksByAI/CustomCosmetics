// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

/** Keep the full preview name inside its own row, independently of the model's facing. */
public final class PreviewLayout {
    private PreviewLayout() {}
    public static float nameScale(int textWidth, int availableWidth) {
        return Math.min(1.35f, Math.max(1, availableWidth) / (float)Math.max(1, textWidth));
    }
}
