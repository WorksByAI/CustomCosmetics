// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.client.ClientEdition;
import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.gui.CosmeticsScreen;
import com.customcosmetics.gui.PinkUi;
import net.minecraft.client.Minecraft;
import com.customcosmetics.sync.SyncCommands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;

public final class LegitEditionGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ConfigManager.offlineMode = true;
            verify(ClientEdition.installed() == ClientEdition.LEGIT, "Wrong test edition");
            verify(!FabricLoader.getInstance().isModLoaded("customskyblock-dungeon"), "Legit loaded Quoi");
            ConfigManager.gameplayPanelConfirmed = true;
            ConfigManager.gameplayModules = java.util.Set.of("AUTO_CLICKER");
            ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.MAIN));
            var screen = ClientUiAccess.screen(client);
            verify(screen.getTitle().getString().equals("CustomSkyBlock"), "Wrong settings title");
            verify(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .noneMatch(widget -> widget.getMessage().getString().equals("Cheating")), "Cheating button visible with an old config");
            var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
            SyncCommands.register(dispatcher);
            var root = dispatcher.getRoot().getChild("customskyblock");
            for (String name : new String[]{"cheating", "autoclicker", "leap"}) {
                verify(root.getChild(name) == null, "Legit registered " + name);
            }
            verify(root.getChild("waypoint") != null && root.getChild("shortcuts") != null, "Missing QOL commands");
            for (String alias : new String[]{"customskyblock", "csb"}) {
                try { verify(dispatcher.execute(alias, (FabricClientCommandSource) null) == 1, "Missing settings command " + alias); }
                catch (CommandSyntaxException error) { throw new AssertionError(error); }
            }
        });
        try (var world = context.worldBuilder().create()) {
            context.waitTicks(6);
            context.runOnClient(client -> {
                ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.ZOOM));
                verify(ClientUiAccess.screen(client).children().stream().filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).anyMatch(widget -> widget.getMessage().getString().equals("Zoom")),
                    "Missing QOL controls");
                verifyWindowTitle(client);
                ClientUiAccess.setScreen(client, null);
            });
            context.waitTicks(4);
        }
        System.out.println("CSB_LEGIT_EDITION_OK");
    }

    private static void verifyWindowTitle(Minecraft client) {
        ConfigManager.uiAnimationsEnabled = false;
        ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.WINDOW));
        var screen = ClientUiAccess.screen(client);
        var controls = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
        var field = (PinkUi.Field) controls.stream().filter(widget -> widget.getMessage().getString().equals("Window Title"))
            .findFirst().orElseThrow();
        var apply = controls.stream().filter(widget -> widget.getMessage().getString().equals("Apply Window Title"))
            .findFirst().orElseThrow();
        try {
            var viewTop = CosmeticsScreen.class.getDeclaredField("viewTop");
            var viewBottom = CosmeticsScreen.class.getDeclaredField("viewBottom");
            viewTop.setAccessible(true);
            viewBottom.setAccessible(true);
            int top = viewTop.getInt(screen);
            int bottom = viewBottom.getInt(screen);
            if (apply.getBottom() >= bottom) {
                screen.mouseScrolled(apply.getX() + 5, (top + bottom) / 2.0, 0, -(apply.getBottom() - bottom + 4) / 48.0);
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not find the title settings viewport", error);
        }
        var click = new net.minecraft.client.input.MouseButtonEvent(apply.getX() + apply.getWidth() / 2.0,
            apply.getY() + apply.getHeight() / 2.0, new net.minecraft.client.input.MouseButtonInfo(ClientInput.MOUSE_LEFT, 0));
        String title = "CustomSkyBlock 窗口测试";
        field.setValue(title);
        verify(screen.mouseClicked(click, false), "Could not apply the window title");
        client.updateTitle();
        verify(title.equals(ConfigManager.windowTitle), "The selected window title did not persist");
        if (org.lwjgl.system.Platform.get() == org.lwjgl.system.Platform.WINDOWS) {
            verify(title.equals(nativeWindowTitle(client)), "The OS window did not use the selected title");
        }
        field.setValue("");
        verify(screen.mouseClicked(click, false), "Could not reset the window title");
        verify(ConfigManager.windowTitle.isEmpty(), "Blank title did not restore Minecraft's title");
        if (org.lwjgl.system.Platform.get() == org.lwjgl.system.Platform.WINDOWS) {
            verify(!title.equals(nativeWindowTitle(client)), "The OS window kept the previous title after reset");
        }
        System.out.println("CSB_LEGIT_WINDOW_TITLE_OK");
    }

    private static String nativeWindowTitle(Minecraft client) {
        long handle = client.getWindow().handle();
        try {
            var version = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion();
            long nativeHandle;
            if (version.getFriendlyString().startsWith("26.3")) {
                var video = Class.forName("org.lwjgl.sdl.SDLVideo");
                int properties = (int) video.getMethod("SDL_GetWindowProperties", long.class).invoke(null, handle);
                var accessor = Class.forName("org.lwjgl.sdl.SDLProperties")
                    .getMethod("SDL_GetPointerProperty", int.class, CharSequence.class, long.class);
                nativeHandle = (long) accessor.invoke(null, properties, video.getField("SDL_PROP_WINDOW_WIN32_HWND_POINTER").get(null), 0L);
            } else {
                nativeHandle = (long) Class.forName("org.lwjgl.glfw.GLFWNativeWin32")
                    .getMethod("glfwGetWin32Window", long.class).invoke(null, handle);
            }
            var text = new char[256];
            com.sun.jna.NativeLibrary.getInstance("user32").getFunction("GetWindowTextW")
                .invokeInt(new Object[]{new com.sun.jna.Pointer(nativeHandle), text, text.length});
            return com.sun.jna.Native.toString(text);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not read the Windows window title", error);
        }
    }

    private static void verify(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
