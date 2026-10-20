// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

/** Shared responsive geometry for the studio and its pointer clipping. */
public record StudioLayout(int x, int y, int width, int height, int sidebarWidth,
                           int contentX, int contentWidth, int contentTop, int contentBottom,
                           int navigationTop, int navigationBottom) {
    public static StudioLayout of(int screenWidth, int screenHeight) {
        int width = Math.min(740, Math.max(1, screenWidth - 16));
        int height = Math.min(480, Math.max(1, screenHeight - 16));
        int x = (screenWidth - width) / 2, y = (screenHeight - height) / 2;
        int sidebar = width < 440 ? 112 : 146;
        int gap = width < 440 ? 12 : 20;
        int contentX = x + sidebar + gap;
        return new StudioLayout(x, y, width, height, sidebar, contentX,
            Math.max(1, width - sidebar - gap - 20), y + 88, y + height - 16,
            y + 59, y + height - 55);
    }

    public static boolean contains(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }
}
