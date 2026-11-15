// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMouse;

public enum SdlCursor implements MouseFocusRecovery.Cursor {
    INSTANCE;

    @Override public boolean focused(long window) {
        return SDLKeyboard.SDL_GetKeyboardFocus() == window;
    }

    @Override public boolean captured(long window) {
        return SDLMouse.SDL_GetWindowRelativeMouseMode(window);
    }

    @Override public void capture(long window) {
        SDLMouse.SDL_SetWindowRelativeMouseMode(window, true);
    }
}
