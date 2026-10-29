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

    /** Optional title access is linked only when a title announcement actually arrives. */
    private static final class Titles {
        static final Method[] METHODS=link();
        private static Method[] link(){
            try{var type=HUD==null?Gui.class:HUD.getType();return new Method[]{type.getMethod("setTimes",int.class,int.class,int.class),type.getMethod("setSubtitle",net.minecraft.network.chat.Component.class),type.getMethod("setTitle",net.minecraft.network.chat.Component.class)};}
            catch(ReflectiveOperationException unavailable){return null;}
        }
    }
    public static boolean title(Minecraft client,net.minecraft.network.chat.Component title,net.minecraft.network.chat.Component subtitle){
        var methods=Titles.METHODS;if(methods==null)return false;
        try{var hud=HUD==null?client.gui:HUD.get(client.gui);methods[0].invoke(hud,10,80,10);methods[1].invoke(hud,subtitle);methods[2].invoke(hud,title);return true;}
        catch(ReflectiveOperationException unavailable){return false;}
    }

}
