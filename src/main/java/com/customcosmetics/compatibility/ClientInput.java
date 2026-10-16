package com.customcosmetics.compatibility;

import com.mojang.blaze3d.platform.InputConstants;

/** Resolve vanilla constants at runtime: 26.3 uses SDL instead of GLFW. */
public final class ClientInput {
    public static final int ENTER = value("KEY_RETURN");
    public static final int NUMPAD_ENTER = value("KEY_NUMPADENTER");
    public static final int SPACE = value("KEY_SPACE");
    public static final int ESCAPE = value("KEY_ESCAPE");
    public static final int LEFT = value("KEY_LEFT"), RIGHT = value("KEY_RIGHT");
    public static final int UP = value("KEY_UP"), DOWN = value("KEY_DOWN");
    public static final int PAGE_UP = value("KEY_PAGEUP"), PAGE_DOWN = value("KEY_PAGEDOWN");
    public static final int MOUSE_LEFT = value("MOUSE_BUTTON_LEFT");
    public static final int MOUSE_RIGHT = value("MOUSE_BUTTON_RIGHT");
    private ClientInput() {}
    private static int value(String name) {
        try { return InputConstants.class.getField(name).getInt(null); }
        catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
}
