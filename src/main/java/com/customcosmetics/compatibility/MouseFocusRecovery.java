// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class MouseFocusRecovery {
    public interface Cursor {
        boolean focused(long window);
        boolean captured(long window);
        void capture(long window);
    }

    public enum GlfwCursor implements Cursor {
        INSTANCE;

        @Override public boolean focused(long window) {
            return GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        }

        @Override public boolean captured(long window) {
            return GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED;
        }

        @Override public void capture(long window) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        }
    }

    // A fullscreen transition or a native focus event can settle after the first tick.
    private static final int REFOCUS_TICKS = 6;
    private static Cursor cursor;
    private static int remainingTicks;

    private MouseFocusRecovery() {}

    public static void onFocus(boolean focused, Cursor backend) {
        cursor = focused ? backend : null;
        remainingTicks = focused ? REFOCUS_TICKS : 0;
    }

    public static void tick(Minecraft client) {
        if (remainingTicks == 0) return;
        var backend = cursor;
        if (--remainingTicks == 0) cursor = null;
        if (client.player == null || client.level == null || ClientUiAccess.screen(client) != null) {
            remainingTicks = 0;
            cursor = null;
            return;
        }

        var window = client.getWindow();
        if (!client.isWindowActive() || window.isIconified() || ClientUiAccess.hasOverlay(client) ||
            !backend.focused(window.handle())) return;

        var mouse = client.mouseHandler;
        if (!mouse.isMouseGrabbed()) mouse.grabMouse();
        if (!backend.captured(window.handle())) {
            // The game's grab flag and the native cursor mode can diverge across a focus change.
            mouse.cursorEntered();
            backend.capture(window.handle());
        }
    }
}
