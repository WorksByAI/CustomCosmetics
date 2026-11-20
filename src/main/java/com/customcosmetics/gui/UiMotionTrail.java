// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A short directional blur for moving shapes. Text and hitboxes stay at the current position. */
final class UiMotionTrail {
    private static final float MAX_TRAVEL = 5f;

    private UiMotionTrail() {}

    static void edge(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
                     int color, float dx, float dy) {
        float distance = Math.max(Math.abs(dx), Math.abs(dy));
        if (distance < .5f) return;
        float reach = Math.min(1, MAX_TRAVEL / distance);
        float strength = Math.min(1, distance / 3);
        PinkUi.outline(graphics, x - Math.round(dx * reach), y - Math.round(dy * reach),
            width, height, radius, PinkUi.alpha(color, Math.round(13 * strength)), 0);
        PinkUi.outline(graphics, x - Math.round(dx * reach / 2), y - Math.round(dy * reach / 2),
            width, height, radius, PinkUi.alpha(color, Math.round(27 * strength)), 0);
    }

    static void resize(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius,
                       int color, float dx, float dy) {
        int horizontal = Math.round(Math.clamp(dx, -MAX_TRAVEL, MAX_TRAVEL));
        int vertical = Math.round(Math.clamp(dy, -MAX_TRAVEL, MAX_TRAVEL));
        if (horizontal == 0 && vertical == 0) return;
        PinkUi.outline(graphics, x + horizontal, y + vertical, width - 2 * horizontal, height - 2 * vertical,
            radius, PinkUi.alpha(color, 13), 0);
        PinkUi.outline(graphics, x + horizontal / 2, y + vertical / 2,
            width - 2 * (horizontal / 2), height - 2 * (vertical / 2), radius, PinkUi.alpha(color, 23), 0);
    }

    static void knob(GuiGraphicsExtractor graphics, float x, int y, int width, int height, int radius,
                     int color, float dx) {
        int base = Math.round(x);
        float subpixel = x - base;
        float distance = Math.min(Math.abs(dx), MAX_TRAVEL);
        if (subpixel == 0 && distance < .5f) {
            PinkUi.rounded(graphics, base, y, width, height, radius, color);
            return;
        }
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(subpixel, 0);
        if (distance >= .5f) {
            int shift = Math.round(Math.copySign(distance, dx));
            PinkUi.rounded(graphics, base - shift, y, width, height, radius, PinkUi.alpha(color, 24));
            PinkUi.rounded(graphics, base - shift / 2, y, width, height, radius, PinkUi.alpha(color, 46));
        }
        PinkUi.rounded(graphics, base, y, width, height, radius, color);
        pose.popMatrix();
    }
}
