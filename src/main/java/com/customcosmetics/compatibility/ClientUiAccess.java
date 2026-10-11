package com.customcosmetics.compatibility;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Cached access to the GUI/HUD split introduced in 26.2. */
public final class ClientUiAccess {
    private static final Field LEGACY_SCREEN;
    private static final Field HUD;
    private static final Method SCREEN, SET_SCREEN, CHAT;
    static {
        try {
            Field legacy = null, hud = null;
            Method screen = null, setScreen, chat;
            try { legacy = Minecraft.class.getField("screen"); }
            catch (NoSuchFieldException ignored) { /* 26.2+ keeps the screen in Gui. */ }
            if (legacy != null) {
                setScreen = Minecraft.class.getMethod("setScreen", Screen.class);
                chat = Gui.class.getMethod("getChat");
            } else {
                screen = Gui.class.getMethod("screen");
                setScreen = Gui.class.getMethod("setScreen", Screen.class);
                hud = Gui.class.getField("hud");
                chat = hud.getType().getMethod("getChat");
            }
            LEGACY_SCREEN = legacy; HUD = hud;
            SCREEN = screen; SET_SCREEN = setScreen; CHAT = chat;
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }

    private ClientUiAccess() {}

    public static Screen screen(Minecraft client) {
        try { return (Screen) (LEGACY_SCREEN != null ? LEGACY_SCREEN.get(client) : SCREEN.invoke(client.gui)); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot read Minecraft screen", e); }
    }

    public static void setScreen(Minecraft client, Screen screen) {
        try { SET_SCREEN.invoke(LEGACY_SCREEN != null ? client : client.gui, screen); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot open Minecraft screen", e); }
    }

    public static ChatComponent chat(Minecraft client) {
        try { return (ChatComponent) CHAT.invoke(HUD == null ? client.gui : HUD.get(client.gui)); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot access Minecraft chat", e); }
    }

}
