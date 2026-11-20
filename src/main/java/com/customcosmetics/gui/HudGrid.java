// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class HudGrid {
    static final int SPACING = 5;
    private static final int MAJOR_SPACING = SPACING * 5;

    private HudGrid() {}

    static int snap(double position) {
        return (int) Math.round(position / SPACING) * SPACING;
    }

    static float fraction(double position, int viewport, int maximum) {
        return (float) Math.clamp(snap(position), 0, maximum / SPACING * SPACING) / viewport;
    }

    static void draw(GuiGraphicsExtractor graphics) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        for (int x = SPACING; x < width; x += SPACING) {
            graphics.fill(x, 0, x + 1, height, lineColor(x));
        }
        for (int y = SPACING; y < height; y += SPACING) {
            graphics.fill(0, y, width, y + 1, lineColor(y));
        }
    }

    private static int lineColor(int position) {
        return position % MAJOR_SPACING == 0 ? 0x49BACBE3 : 0x1DBACBE3;
    }
}
