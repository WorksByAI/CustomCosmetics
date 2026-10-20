// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.shortcuts;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.CosmeticsConfigDir;
import com.customcosmetics.gui.ShortcutsScreen;
import com.customcosmetics.utils.MessageUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import java.io.IOException;
import java.util.*;

/** All command sends originate from one explicit chat send or a fresh input press. */
public final class Shortcuts {
    private static ShortcutStore store;
    private static ShortcutStore.Data data = new ShortcutStore.Data(true, List.of());
    private static ShortcutRules rules = new ShortcutRules(List.of());
    private static ShortcutKeyState<InputConstants.Key> keys = new ShortcutKeyState<>(List.of());
    private static boolean initialized, opening, dispatching;
    private static String status = "";
    // Read vanilla constants at runtime; 26.3 moved input handling from GLFW to SDL.
    private static final int PRESS = inputConstant("PRESS"), REPEAT = inputConstant("REPEAT");
    private Shortcuts() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        store = new ShortcutStore(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("shortcuts.json"));
        reload();
        ClientSendMessageEvents.MODIFY_COMMAND.register(Shortcuts::modifyCommand);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> keys.clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (opening) { opening = false; ClientUiAccess.setScreen(client, new ShortcutsScreen(null)); }
            if (!keys.empty() && !canUseKeys(client)) keys.clear();
        });
    }

    public static int openNextTick() { opening = true; return 1; }
    public static ShortcutStore.Data data() { return data; }
    public static String status() { return status; }
    public static boolean writable() { return store != null && store.writable(); }
    public static void reload() {
        try { apply(store.load()); status = ""; }
        catch (IOException | IllegalArgumentException error) {
            store.protect();
            apply(new ShortcutStore.Data(false, List.of()));
            status = error.getMessage();
        }
    }
    public static void save(ShortcutStore.Data next) throws IOException {
        ShortcutRules.validate(next.entries(), Set.of());
        compileKeys(next); // Validate key names before touching the file.
        store.save(next);
        apply(next);
        status = "";
    }
    private static void apply(ShortcutStore.Data next) {
        ShortcutKeyState<InputConstants.Key> compiled = compileKeys(next);
        data = next;
        rules = new ShortcutRules(next.enabled() ? next.entries() : List.of());
        keys = compiled;
    }
    private static ShortcutKeyState<InputConstants.Key> compileKeys(ShortcutStore.Data next) {
        List<ShortcutKeyState.Binding<InputConstants.Key>> bindings = new ArrayList<>();
        for (ShortcutRules.Entry entry : next.entries()) if (entry.kind() == ShortcutRules.Kind.KEY) {
            List<InputConstants.Key> bound = new ArrayList<>();
            for (String name : ShortcutRules.keys(entry.trigger())) {
                InputConstants.Key key;
                try { key = InputConstants.getKey(name); }
                catch (RuntimeException error) { throw new IllegalArgumentException("Unknown key: " + name); }
                if (key.equals(InputConstants.UNKNOWN)) throw new IllegalArgumentException("Unknown key: " + name);
                bound.add(key);
            }
            if (next.enabled() && entry.enabled()) bindings.add(new ShortcutKeyState.Binding<>(entry.id(), List.copyOf(bound), entry.command()));
        }
        return new ShortcutKeyState<>(bindings);
    }
    private static String modifyCommand(String command) {
        if (dispatching || rules.empty()) return command;
        try {
            return rules.expand(command);
        } catch (IllegalArgumentException error) {
            MessageUtils.sendMessage("Shortcuts: " + error.getMessage());
            return command;
        }
    }
    private static boolean connected(Minecraft client) {
        return client.player != null && client.level != null && client.getConnection() != null
            && client.getConnection().getConnection().isConnected();
    }
    private static boolean canUseKeys(Minecraft client) {
        return connected(client) && client.isWindowActive() && ClientUiAccess.screen(client) == null;
    }
    private static void send(String command) {
        var client = Minecraft.getInstance();
        if (!connected(client)) return;
        dispatching = true;
        try { client.getConnection().sendCommand(command); }
        finally { dispatching = false; }
    }
    public static boolean keyboard(long window, int action, KeyEvent event) {
        if (keys.empty()) return false;
        var client = Minecraft.getInstance();
        if (window != client.getWindow().handle()) return false;
        return input(InputConstants.getKey(event), action, client);
    }
    public static boolean mouse(long window, MouseButtonInfo button, int action) {
        if (keys.empty()) return false;
        var client = Minecraft.getInstance();
        if (window != client.getWindow().handle()) return false;
        return input(InputConstants.Type.MOUSE.getOrCreate(button.button()), action, client);
    }
    private static boolean input(InputConstants.Key key, int action, Minecraft client) {
        String command = keys.event(key, action == PRESS || action == REPEAT, action == REPEAT, canUseKeys(client));
        if (command == null) return false;
        try {
            String raw = ShortcutRules.withoutSlash(command);
            send(rules.expand(raw));
        } catch (IllegalArgumentException error) { MessageUtils.sendMessage("Shortcuts: " + error.getMessage()); }
        return true;
    }
    private static int inputConstant(String name) {
        try { return InputConstants.class.getField(name).getInt(null); }
        catch (ReflectiveOperationException exception) { throw new ExceptionInInitializerError(exception); }
    }
}
