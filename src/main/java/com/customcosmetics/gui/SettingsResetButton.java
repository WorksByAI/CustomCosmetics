// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

final class SettingsResetButton extends PinkUi.Action {
    static final int WIDTH = 88;
    private static final Component RESET = Component.literal("Reset Settings");
    private static final Component CONFIRM = Component.literal("Confirm Reset");
    private final Confirmation confirmation;
    private int heldKey = -1;

    SettingsResetButton(int right, int y, int height, Runnable reset) {
        this(right, y, height, new Confirmation(reset));
    }

    private SettingsResetButton(int right, int y, int height, Confirmation confirmation) {
        super(right - WIDTH, y, WIDTH, height, RESET.getString(), false, confirmation::press);
        this.confirmation = confirmation;
        selected(confirmation::armed);
        setTooltip(Tooltip.create(UiText.guiComponent("Click again to restore this module's defaults.")));
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        Component label = confirmation.armed() ? CONFIRM : RESET;
        if (getMessage() != label) setMessage(label);
        super.extractWidgetRenderState(graphics, mouseX, mouseY, tick);
    }

    @Override public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            confirmation.firstClick = 0;
            heldKey = -1;
        }
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == heldKey) return true;
        if (!super.keyPressed(event)) return false;
        heldKey = event.key();
        return true;
    }

    @Override public boolean keyReleased(KeyEvent event) {
        if (event.key() == heldKey) heldKey = -1;
        return super.keyReleased(event);
    }

    private static final class Confirmation {
        private final Runnable reset;
        private long firstClick;

        Confirmation(Runnable reset) { this.reset = reset; }

        boolean armed() { return firstClick != 0 && Util.getMillis() - firstClick < 5_000; }

        void press() {
            long now = Util.getMillis();
            if (!armed()) firstClick = now;
            else if (now - firstClick >= 300) {
                firstClick = 0;
                reset.run();
            }
        }
    }
}
