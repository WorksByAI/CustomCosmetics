// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

/** Keep the full preview name inside its own row, independently of the model's facing. */
public final class PreviewLayout {
    private PreviewLayout() {}
    public static float nameScale(int textWidth, int availableWidth) {
        return Math.min(1.35f, Math.max(1, availableWidth) / (float)Math.max(1, textWidth));
    }
    /** Reserve the title/name rows while fitting the full model into a short scroll viewport. */
    public static int playerCardHeight(int viewportHeight, boolean pinned) {
        return pinned ? 184 : Math.clamp(viewportHeight - 8, 104, 184);
    }
    public static int playerModelScale(int cardHeight) {
        return Math.max(1, Math.round(52f * Math.max(1, cardHeight - 57) / 127));
    }
}
