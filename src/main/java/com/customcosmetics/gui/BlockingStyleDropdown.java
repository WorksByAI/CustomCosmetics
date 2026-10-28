// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.blocking.BlockingStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import static com.customcosmetics.gui.PinkUi.*;

/** Local first-person style selection, with the same popup/keyboard behavior as blocking lists. */
final class BlockingStyleDropdown extends PinkUi.Action {
    private boolean open;
    private BlockingStyle displayed;
    private int highlighted, menuTop;
    private static final int MENU_HEIGHT = 60;
    BlockingStyleDropdown(int x, int y, int width) {
        super(x, y, width, 24, "Blocking Style", false, () -> {});
    }
    private void toggle() {
        open = !open;
        highlighted = BlockingStyle.parse(ConfigManager.swordBlockingStyle).ordinal();
    }
    void closeMenu() { open = false; }
    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { toggle(); }
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        var choice=BlockingStyle.parse(ConfigManager.swordBlockingStyle);
        if(displayed!=choice){displayed=choice;setMessage(Component.literal("Blocking Style: " + choice.label + "  ▾"));}
        super.extractWidgetRenderState(g, mx, my, dt);
    }
    void drawMenu(GuiGraphicsExtractor g, int mx, int my, int top, int bottom) {
        if (!open || !visible) return;
        menuTop = Math.clamp(getBottom() + 4, top, Math.max(top, bottom - MENU_HEIGHT));
        g.nextStratum();
        outline(g, getX(), menuTop, getWidth(), MENU_HEIGHT, 6, PINK, PANEL);
        for (var style : BlockingStyle.values()) {
            int y = menuTop + 4 + style.ordinal() * 26;
            boolean hovered = mx >= getX() && mx < getRight() && my >= y && my < y + 26;
            if (hovered || style.ordinal() == highlighted) rounded(g, getX() + 4, y, getWidth() - 8, 26, 3, PINK_DARK);
            uiText(g, UiFonts.get(Minecraft.getInstance().font), style.label, getX() + 12, y + 8,
                style == BlockingStyle.parse(ConfigManager.swordBlockingStyle) ? PINK : TEXT, getWidth() - 24);
        }
    }
    boolean menuClick(MouseButtonEvent event) {
        if (!open) return false;
        if (event.button() == ClientInput.MOUSE_LEFT && event.x() >= getX() && event.x() < getRight()
                && event.y() >= menuTop + 4 && event.y() < menuTop + MENU_HEIGHT - 4)
            choose(BlockingStyle.values()[(int)(event.y() - menuTop - 4) / 26]);
        else open = false;
        return true;
    }
    private void choose(BlockingStyle style) {
        ConfigManager.swordBlockingStyle = style.value(); ConfigManager.requestSave(); open = false;
    }
    boolean menuKey(KeyEvent event) {
        if (!open) return false;
        if (event.key() == ClientInput.ESCAPE) { open = false; return true; }
        if (event.key() == ClientInput.UP || event.key() == ClientInput.DOWN) {
            highlighted = Math.floorMod(highlighted + (event.key() == ClientInput.UP ? -1 : 1), BlockingStyle.values().length);
            return true;
        }
        if (event.key() == ClientInput.ENTER || event.key() == ClientInput.NUMPAD_ENTER || event.key() == ClientInput.SPACE) {
            choose(BlockingStyle.values()[highlighted]); return true;
        }
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (menuKey(event)) return true;
        if (event.key() == ClientInput.ENTER || event.key() == ClientInput.NUMPAD_ENTER || event.key() == ClientInput.SPACE
                || event.key() == ClientInput.UP || event.key() == ClientInput.DOWN) { toggle(); return true; }
        return false;
    }
}
