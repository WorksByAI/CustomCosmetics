// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.shortcuts.*;
import com.customcosmetics.gui.PinkUi;
import com.customcosmetics.gui.ShortcutsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real Fabric command processing and mixed-in input handlers in a disposable local world. */
public final class ShortcutsGameTest implements FabricClientGameTest {
    private static final String PROBE = "cc_shortcut_probe";
    private static final List<String> RECEIVED = new ArrayList<>();
    private static final int PRESS = constant("PRESS"), RELEASE = constant("RELEASE"), REPEAT = constant("REPEAT");
    private static final int CTRL = constant("KEY_LCONTROL"), F8 = constant("KEY_F8"), F9 = constant("KEY_F9");
    private static final int CONTROL = constant("MOD_CONTROL"), MOUSE4 = constant("MOUSE_BUTTON_4");
    private static Method keyboard, mouse;
    private static Field windowFocus;

    @Override public void runTest(ClientGameTestContext context) {
        var previous = new AtomicReference<ShortcutStore.Data>();
        var previousFocus = new AtomicReference<Boolean>();
        context.runOnClient(client -> {
            ConfigManager.offlineMode = true; ConfigManager.syncEnabled = false;
            ConfigManager.partyEspEnabled = false; ConfigManager.partyChatEnabled = false;
            previous.set(Shortcuts.data());
        });
        try (var world = context.worldBuilder().create()) {
            context.waitTicks(20);
            context.runOnClient(client -> {
                RECEIVED.clear();
                var dispatcher = ClientCommands.getActiveDispatcher();
                check(dispatcher != null, "Client command dispatcher is missing");
                dispatcher.register(ClientCommands.literal(PROBE).then(
                    ClientCommands.argument("text", StringArgumentType.greedyString()).executes(command -> {
                        RECEIVED.add(StringArgumentType.getString(command, "text")); return 1;
                    })));
                save(new ShortcutStore.Data(true, entries()));
                ClientUiAccess.setScreen(client, null);
                previousFocus.set(client.isWindowActive());
            });
            for (String command : List.of("customcosmetics shortcuts", "ccos shortcuts")) {
                context.runOnClient(client -> client.getConnection().sendCommand(command));
                context.waitTicks(2);
                context.runOnClient(client -> {
                    check(ClientUiAccess.screen(client) instanceof ShortcutsScreen, "/" + command + " did not open the manager");
                    ClientUiAccess.setScreen(client, null);
                });
            }
            context.runOnClient(client -> {
                client.getConnection().sendCommand("cctestalias Alice remainder");
                client.getConnection().sendCommand("cctestposition first second");
                client.getConnection().sendCommand("cctesttokens @friend @friend! user@friend");
                check(RECEIVED.equals(List.of("alias Alice remainder", "position second first",
                    "tokens Alice @friend! user@friend")), "Aliases or exact argument tokens did not pass through Fabric: " + RECEIVED);
                RECEIVED.clear();
                focus(client, false);
                key(client, F8, PRESS, 0); key(client, F8, RELEASE, 0);
                check(RECEIVED.isEmpty(), "Unfocused window triggered a shortcut");
                // Change only the fixture's focus state without stealing desktop focus.
                focus(client, true);
                key(client, F8, PRESS, 0);
                key(client, F8, REPEAT, 0);
                key(client, F8, PRESS, 0);
                check(RECEIVED.equals(List.of("keyboard")), "A held key repeated a shortcut");
                key(client, F8, RELEASE, 0);
                key(client, F8, PRESS, 0); key(client, F8, RELEASE, 0);
                check(RECEIVED.size() == 2, "A second fresh press did not run");
                RECEIVED.clear();
                key(client, CTRL, PRESS, CONTROL);
                key(client, F9, PRESS, CONTROL); key(client, F9, REPEAT, CONTROL);
                key(client, F9, RELEASE, CONTROL); key(client, CTRL, RELEASE, 0);
                check(RECEIVED.equals(List.of("combination")), "Keyboard combination did not run exactly once");
                RECEIVED.clear();
                key(client, CTRL, PRESS, CONTROL);
                button(client, MOUSE4, PRESS, CONTROL); button(client, MOUSE4, PRESS, CONTROL);
                button(client, MOUSE4, RELEASE, CONTROL); key(client, CTRL, RELEASE, 0);
                check(RECEIVED.equals(List.of("mouse")), "Mouse combination did not run exactly once");
                RECEIVED.clear();
                ClientUiAccess.setScreen(client, new ShortcutsScreen(null));
                key(client, F8, PRESS, 0); key(client, F8, RELEASE, 0);
                key(client, CTRL, PRESS, CONTROL); button(client, MOUSE4, PRESS, CONTROL);
                button(client, MOUSE4, RELEASE, CONTROL); key(client, CTRL, RELEASE, 0);
                check(RECEIVED.isEmpty(), "GUI typing triggered a shortcut");
                ClientUiAccess.setScreen(client, null);
                save(new ShortcutStore.Data(false, entries()));
                key(client, F8, PRESS, 0); key(client, F8, RELEASE, 0);
                check(RECEIVED.isEmpty(), "Disabled shortcuts still handled a key");
                check(ClientSendMessageEvents.ALLOW_COMMAND.invoker().allowSendCommandMessage("cctestalias unchanged"),
                    "Disabled shortcuts still rewrote a command");
                save(new ShortcutStore.Data(true, entries()));
                var next = new ArrayList<>(entries());
                next.add(new ShortcutRules.Entry("server", ShortcutRules.Kind.ARGUMENT, "/say", "/help", true));
                next.add(new ShortcutRules.Entry("visit", ShortcutRules.Kind.ARGUMENT, "/v", "/visit", true));
                save(new ShortcutStore.Data(true, next));
                check(ClientSendMessageEvents.MODIFY_COMMAND.invoker().modifySendCommandMessage("say hello").equals("help hello"),
                        "An existing server command was not replaced");
                check(ClientSendMessageEvents.MODIFY_COMMAND.invoker().modifySendCommandMessage("v @friend").equals("visit Alice"),
                        "The /v server command was not replaced with /visit");
                boolean protectedOwnCommand = false;
                try {
                    next.add(new ShortcutRules.Entry("own", ShortcutRules.Kind.ARGUMENT, "/ccos", "/help", true));
                    save(new ShortcutStore.Data(true, next));
                } catch (IllegalArgumentException expected) { protectedOwnCommand = true; }
                check(protectedOwnCommand, "The shortcut manager command could be overridden");
                save(new ShortcutStore.Data(true, entries()));
            });
            for (int[] size : new int[][]{{640, 480}, {1600, 1000}}) {
                context.getInput().resizeWindow(size[0], size[1]); context.waitTicks(3);
                context.runOnClient(client -> {
                    client.options.guiScale().set(2);
                    ClientUiAccess.setScreen(client, new ShortcutsScreen(null));
                    withinScreen(client);
                });
                context.waitTicks(2); context.takeScreenshot("shortcuts-manager-" + size[0]);
                context.runOnClient(client -> {
                    var screen = ClientUiAccess.screen(client);
                    var create = screen.children().stream().filter(PinkUi.Action.class::isInstance)
                        .map(PinkUi.Action.class::cast).filter(widget -> widget.getMessage().getString().equals("New"))
                        .findFirst().orElseThrow();
                    create.keyPressed(new KeyEvent(constant("KEY_RETURN"), 0, 0));
                    withinScreen(client);
                });
                context.waitTicks(2); context.takeScreenshot("shortcuts-editor-" + size[0]);
            }
            context.runOnClient(client -> {
                ClientUiAccess.setScreen(client, null);
                save(previous.get());
                focus(client, previousFocus.get());
            });
            System.out.println("SHORTCUTS_COMMAND_ALIASES_ARGUMENT_TOKENS_KEYBOARD_MOUSE_GUI_GATES_AND_RESPONSIVE_EDITOR_OK");
        }
    }
    private static List<ShortcutRules.Entry> entries() {
        return List.of(
            new ShortcutRules.Entry("alias", ShortcutRules.Kind.ALIAS, "cctestalias", "/" + PROBE + " alias", true),
            new ShortcutRules.Entry("position", ShortcutRules.Kind.ALIAS, "cctestposition", "/" + PROBE + " position $2 $1", true),
            new ShortcutRules.Entry("tokens", ShortcutRules.Kind.ALIAS, "cctesttokens", "/" + PROBE + " tokens", true),
            new ShortcutRules.Entry("friend", ShortcutRules.Kind.ARGUMENT, "@friend", "Alice", true),
            new ShortcutRules.Entry("keyboard", ShortcutRules.Kind.KEY, "key.keyboard.f8", "/" + PROBE + " keyboard", true),
            new ShortcutRules.Entry("combination", ShortcutRules.Kind.KEY, "key.keyboard.f9 + key.keyboard.left.control", "/" + PROBE + " combination", true),
            new ShortcutRules.Entry("mouse", ShortcutRules.Kind.KEY, "key.keyboard.left.control + key.mouse.4", "/" + PROBE + " mouse", true));
    }
    private static void key(Minecraft client, int key, int action, int modifiers) {
        try {
            if (keyboard == null) { keyboard = KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class); keyboard.setAccessible(true); }
            keyboard.invoke(client.keyboardHandler, client.getWindow().handle(), action, new KeyEvent(key, 0, modifiers));
        } catch (ReflectiveOperationException error) { throw new AssertionError("Keyboard callback failed", error); }
    }
    private static void button(Minecraft client, int button, int action, int modifiers) {
        try {
            if (mouse == null) { mouse = MouseHandler.class.getDeclaredMethod("onButton", long.class, MouseButtonInfo.class, int.class); mouse.setAccessible(true); }
            mouse.invoke(client.mouseHandler, client.getWindow().handle(), new MouseButtonInfo(button, modifiers), action);
        } catch (ReflectiveOperationException error) { throw new AssertionError("Mouse callback failed", error); }
    }
    private static void focus(Minecraft client, boolean focused) {
        try {
            if (windowFocus == null) {
                // Fabric gametest cancels native focus callbacks. Its fabric_focus helper
                // writes this field too, but provides no corresponding unfocus helper.
                windowFocus = Window.class.getDeclaredField("focused");
                windowFocus.setAccessible(true);
            }
            windowFocus.setBoolean(client.getWindow(), focused);
            check(client.isWindowActive() == focused, "Window focus fixture did not update its state");
        } catch (ReflectiveOperationException error) { throw new AssertionError("Window focus fixture failed", error); }
    }
    private static void save(ShortcutStore.Data data) {
        try { Shortcuts.save(data); }
        catch (IOException error) { throw new AssertionError("Could not persist the isolated shortcut fixture", error); }
    }
    private static void withinScreen(Minecraft client) {
        var screen = ClientUiAccess.screen(client);
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= screen.width && widget.getBottom() <= screen.height,
                "Shortcut control escaped the screen: " + widget.getMessage().getString());
        }
    }
    private static int constant(String name) {
        try { return InputConstants.class.getField(name).getInt(null); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
