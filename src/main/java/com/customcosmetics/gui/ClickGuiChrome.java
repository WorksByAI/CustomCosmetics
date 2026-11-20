// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.config.ConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

final class ClickGuiChrome {
    private static final int GLASS_RADIUS = 11;
    private static final Identifier REFLECTION = Identifier.fromNamespaceAndPath("customcosmetics", "textures/gui/glass-reflection.png");

    private ClickGuiChrome() {}

    static void pushEntrance(GuiGraphicsExtractor g, int x, int y, int width, int height, float scale, float travel) {
        float motion = UiEntrance.scale(1 + travel) - 1;
        int dx = Math.round(width * (1 - scale) / 2);
        int dy = Math.round(height * (1 - scale) / 2);
        UiMotionTrail.resize(g, x + dx, y + dy, width - 2 * dx, height - 2 * dy,
            ConfigManager.uiGlassTheme ? GLASS_RADIUS : 5, PinkUi.TEXT, width * motion / 2, height * motion / 2);
        float centerX = x + width / 2f, centerY = y + height / 2f;
        g.pose().pushMatrix();
        g.pose().translate(centerX, centerY);
        g.pose().scale(scale, scale);
        g.pose().translate(-centerX, -centerY);
    }

    static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int sidebarWidth, boolean solidSidebar) {
        if (!drawGlassPanel(graphics, x, y, width, height)) {
            PinkUi.outline(graphics, x, y, width, height, 5, PinkUi.BORDER, PinkUi.PANEL);
            if (solidSidebar) PinkUi.rounded(graphics, x + 1, y + 1, sidebarWidth, height - 2, 4, PinkUi.SIDEBAR);
            return;
        }
        int divider = x + sidebarWidth;
        graphics.fill(divider, y + 10, divider + 1, y + height - 10, PinkUi.alpha(PinkUi.TEXT, lightTheme() ? 29 : 23));
    }

    static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int solidRadius) {
        if (!drawGlassPanel(graphics, x, y, width, height)) {
            PinkUi.outline(graphics, x, y, width, height, solidRadius, PinkUi.BORDER, PinkUi.PANEL);
        }
    }

    static int previewTint() {
        return ConfigManager.uiGlassTheme ? PinkUi.alpha(PinkUi.CARD, glassOpacity() * 96 / 100) : PinkUi.CARD;
    }

    private static boolean drawGlassPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (!ConfigManager.uiGlassTheme || width < 40 || height < 40) return false;
        boolean light = lightTheme();
        PinkUi.outline(graphics, x - 2, y - 2, width + 4, height + 4, GLASS_RADIUS + 2, 0x0602050D, 0);
        PinkUi.outline(graphics, x - 1, y - 1, width + 2, height + 2, GLASS_RADIUS + 1, 0x1002050D, 0);
        PinkUi.outline(graphics, x, y, width, height, GLASS_RADIUS, light ? 0x75FFFFFF : 0x30D9E9FC,
            PinkUi.alpha(light ? 0xE9EFF6 : 0x151821, glassOpacity() * 255 / 100));

        graphics.blit(REFLECTION, x + 3, y + 3, x + width - 3, y + height - 3, 0f, 1f, 0f, 1f);
        return true;
    }

    private static boolean lightTheme() {
        return "light".equals(ConfigManager.uiThemeMode);
    }

    private static int glassOpacity() {
        return Math.clamp(ConfigManager.uiGlassOpacity, 0, UiTheme.MAX_GLASS_OPACITY);
    }
}
