// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.*;
import com.customcosmetics.config.*;
import com.customcosmetics.gui.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

/** Click the compact controls and inspect saved files in the isolated test instance. */
final class SettingsGuiGameTest {
    static void check(ClientGameTestContext context) throws Exception {
        context.runOnClient(client -> {
            ConfigManager.syncEnabled = false;
            var overrides = CosmeticsConfigDir.getCosmeticsConfigDir().resolve("customremoteserver.json");
            Files.deleteIfExists(overrides);
            RemoteServerConfig.init(overrides);
            client.options.guiScale().set(2);
        });
        context.getInput().resizeWindow(1600,1000); context.waitTicks(3);
        var oldNickname = new AtomicReference<String>(); var oldNickEnabled = new AtomicReference<Boolean>();
        context.runOnClient(client -> {
            oldNickname.set(ConfigManager.nickTweaksNickChanger); oldNickEnabled.set(ConfigManager.nickTweaksEnabled);
            ConfigManager.nickTweaksEnabled = true; ConfigManager.nickTweaksNickChanger = "LongNicknamePreviewABCDEFGHIJKLMNOPQRSTUVWXYZ";
            ConfigManager.smolpeopleMode = true;
        });
        context.runOnClient(client -> ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SIZE)));
        context.waitTicks(3);
        context.runOnClient(client -> {
            if ((Integer)read(ClientUiAccess.screen(client),"panelWidth") != 740) throw new AssertionError("Preview panel was not widened");
            for (var child : ClientUiAccess.screen(client).children())
                if (child instanceof AbstractWidget widget && widget.getMessage().getString().contains("Friends"))
                    throw new AssertionError("Removed Friends control is still visible");
        });
        context.getInput().setCursorPos(970,150); context.waitTicks(3);
        context.takeScreenshot("smol-preview-wide-pink-full-name-front");
        context.getInput().setCursorPos(1580,990); context.waitTicks(3);
        context.takeScreenshot("smol-preview-wide-pink-full-name-turned");
        context.getInput().resizeWindow(854,480); context.waitTicks(3);
        context.runOnClient(client -> {
            var screen = ClientUiAccess.screen(client);
            int previewHeight = (Integer) read(screen, "playerPreviewHeight");
            int viewportHeight = (Integer) read(screen, "viewBottom") - (Integer) read(screen, "viewTop");
            if ((Boolean) read(screen, "pinnedSize") || previewHeight > viewportHeight)
                throw new AssertionError("Compact player preview does not fit its scrolling viewport");
            if (PreviewLayout.playerModelScale(previewHeight) >= 52)
                throw new AssertionError("Compact preview did not scale the actual rendered entity");
        });
        context.takeScreenshot("smol-preview-narrow-full-name");
        context.getInput().resizeWindow(1600,1000); context.waitTicks(3);
        bottom(context); context.takeScreenshot("smol-credit");
        context.runOnClient(client -> {
            ConfigManager.nickTweaksNickChanger = oldNickname.get(); ConfigManager.nickTweaksEnabled = oldNickEnabled.get();
            ConfigManager.smolpeopleMode = false;
        });
        click(context,"NickTweaks",PinkUi.Action.class);
        String longNickname = "&d" + "Cherry".repeat(10);
        type(context,"Nick Changer",longNickname);
        context.runOnClient(client -> {
            if (!ConfigManager.nickTweaksNickChanger.equals(longNickname.substring(0,40)))
                throw new AssertionError("Nickname input is not capped at 40 raw characters including format codes");
        });
        System.out.println("NICKNAME_GUI_FORTY_RAW_CHARACTERS_OK");
        bottom(context); context.takeScreenshot("nick-credit-capitalized");
        click(context,"Chroma",PinkUi.Action.class);
        context.runOnClient(client -> {
            for (var child : ClientUiAccess.screen(client).children()) if (child instanceof PinkUi.Slider slider)
                if (!Character.isUpperCase(slider.getMessage().getString().charAt(0))) throw new AssertionError("Lowercase NickTweaks option");
        });
        bottom(context); context.takeScreenshot("chroma-credit-capitalized");
        click(context,"Animations",PinkUi.Action.class);
        context.runOnClient(client -> ConfigManager.swordBlockingListsApplyToOthers = false);
        click(context,"Apply Lists to Other Players",PinkUi.Toggle.class);
        context.runOnClient(client -> { if (!ConfigManager.swordBlockingListsApplyToOthers) throw new AssertionError("Remote list toggle cannot be enabled"); });
        context.takeScreenshot("blocking-lists-apply-to-others");
        click(context,"Apply Lists to Other Players",PinkUi.Toggle.class);
        context.runOnClient(client -> { if (ConfigManager.swordBlockingListsApplyToOthers) throw new AssertionError("Remote list toggle cannot be disabled"); });
        context.runOnClient(client -> {
            var scale = widget(client,"Item Scale value",PinkUi.Field.class);
            var x = widget(client,"Position X value",PinkUi.Field.class);
            if (scale.getY() != x.getY() || scale.getX() == x.getX()) throw new AssertionError("Animations are not laid out in two columns");
            if (scale.getWidth() + 12 != 48) throw new AssertionError("Numeric field has not been compacted");
        });
        type(context,"Item Scale value","1.25");
        context.runOnClient(client -> { if (Math.abs(ConfigManager.animationItemScale - 1.25) > .001) throw new AssertionError("Compact numeric field cannot be clicked and edited"); });
        click(context,"Reset",PinkUi.Action.class);
        context.runOnClient(client -> { if (ConfigManager.animationItemScale != 1) throw new AssertionError("Compact reset button does not reset its cell"); });
        context.takeScreenshot("animations-two-columns");
        bottom(context);
        var remembered = new AtomicReference<Double>();
        context.runOnClient(client -> remembered.set((Double) read(ClientUiAccess.screen(client),"scroll")));
        context.getInput().pressKey(ClientInput.ESCAPE);
        context.runOnClient(client -> {
            ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.MAIN));
            var screen = ClientUiAccess.screen(client);
            if (read(screen,"page") != CosmeticsScreen.Page.ANIMATIONS || !read(screen,"scroll").equals(remembered.get()))
                throw new AssertionError("Closing and reopening does not restore the page and scroll");
        });
        context.runOnClient(client -> {
            var sessionField = CosmeticsScreen.class.getDeclaredField("SESSION"); sessionField.setAccessible(true);
            var session = sessionField.get(null); var time = ScreenSession.class.getDeclaredField("lastInteraction"); time.setAccessible(true);
            time.setLong(session,System.currentTimeMillis() - ScreenSession.IDLE_MILLIS - 1);
        });
        context.getInput().pressKey(ClientInput.ESCAPE);
        context.runOnClient(client -> {
            ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.MAIN));
            var screen = ClientUiAccess.screen(client);
            if (read(screen,"page") != CosmeticsScreen.Page.MAIN || (Double) read(screen,"scroll") != 0)
                throw new AssertionError("One minute of inactivity does not reset the GUI");
        });
        click(context,"Settings",PinkUi.Action.class);
        type(context,"Proxy Host","127.0.0.1"); type(context,"Proxy Port","7890"); type(context,"Proxy Auth","fixture:secret");
        click(context,"Proxy Source:",PinkUi.Action.class);
        context.runOnClient(client -> {
            if (!ConfigManager.syncProxyHost.equals("127.0.0.1") || ConfigManager.syncProxyPort != 7890
                || !ConfigManager.syncProxyAuth.equals("fixture:secret") || !ConfigManager.syncProxySource.equals("manual"))
                throw new AssertionError("Developer proxy controls do not update settings");
        });
        click(context,"Fallback Direct",PinkUi.Toggle.class);
        context.runOnClient(client -> { if (ConfigManager.syncProxyFallbackDirect) throw new AssertionError("Proxy fallback toggle has no effect"); });
        context.takeScreenshot("sync-dev-proxy-settings");
        context.runOnClient(client -> {
            ConfigManager.syncProxyHost = ""; ConfigManager.syncProxyPort = 0; ConfigManager.syncProxyAuth = "";
            ConfigManager.syncProxyFallbackDirect = true; ConfigManager.syncProxySource = "none";
            ConfigManager.saveConfig();
        });
        // Disk assertions run after the asynchronous writer drains, never on the client thread.
        ConfigManager.awaitPendingSaves();
        var file = CosmeticsConfigDir.getCosmeticsConfigDir();
        var settings = JsonParser.parseString(Files.readString(file.resolve("config.json"))).getAsJsonObject().getAsJsonObject("settings");
        for (String key : new String[]{"smolFriendList","smolFriendsEnabled","syncServerIp","syncServerPort","syncServerHttps","syncServerSni","syncUdpHost","syncUdpPort"})
            if (settings.has(key)) throw new AssertionError("Main configuration still writes " + key);
        var overrides = JsonParser.parseString(Files.readString(file.resolve("customremoteserver.json"))).getAsJsonObject();
        if (overrides.size() != 7 || overrides.entrySet().stream().anyMatch(entry -> !entry.getValue().getAsString().isEmpty()))
            throw new AssertionError("Custom server template is not blank");
        context.runOnClient(client -> ClientUiAccess.setScreen(client,null));
        checkStudioRedesign(context);
        context.getInput().resizeWindow(854,480); context.waitTicks(3);
        System.out.println("SETTINGS_GUI_MEMORY_DEV_LAYOUT_AND_FILES_OK");
    }

    /** Exercise clipping, independent navigation and catalogue destinations at real GUI scales. */
    public static void checkStudioRedesign(ClientGameTestContext context) throws Exception {
        for (int[] size : new int[][]{{1600, 1000}, {854, 480}, {640, 480}}) {
            context.getInput().resizeWindow(size[0], size[1]); context.waitTicks(3);
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.SIZE));
            });
            click(context, "Overview", PinkUi.Action.class);
            context.runOnClient(client -> {
                var screen = ClientUiAccess.screen(client);
                if (read(screen, "page") != CosmeticsScreen.Page.MAIN) throw new AssertionError("Overview did not open");
                if (screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .anyMatch(widget -> widget.getMessage().getString().toLowerCase(java.util.Locale.ROOT).contains("waypoint")
                        || widget.getMessage().getString().toLowerCase(java.util.Locale.ROOT).contains("shortcuts")))
                    throw new AssertionError("Command tools gained unwanted GUI buttons");
                int contentX = (Integer) read(screen, "contentX"), contentWidth = (Integer) read(screen, "contentWidth");
                if (contentWidth < 160) throw new AssertionError("Minimum studio content width was lost");
                for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.getX() >= contentX
                    && !widget.getMessage().getString().equals("×") && widget.getRight() > contentX + contentWidth)
                    throw new AssertionError("Overview control overflows its content area: " + widget.getMessage().getString());
            });
            context.waitTicks(2); context.takeScreenshot("studio-overview-" + size[0]);
            context.runOnClient(client -> {
                var screen = (CosmeticsScreen) ClientUiAccess.screen(client);
                int x = (Integer) read(screen, "panelX") + 20, top = (Integer) read(screen, "navTop");
                screen.mouseScrolled(x, top + 20, 0, -100);
                var sharing = widget(client, "Sharing", PinkUi.Action.class);
                if (!sharing.visible || sharing.getY() < (Integer) read(screen, "navTop")
                    || sharing.getBottom() > (Integer) read(screen, "navBottom"))
                    throw new AssertionError("Sidebar scrolling did not expose Sharing");
                screen.mouseScrolled(x, top + 20, 0, 100);
                if ((Integer) read(screen, "navScroll") != 0) throw new AssertionError("Sidebar did not return to top");
            });
        }
        context.getInput().resizeWindow(1600, 1000); context.waitTicks(3);
        for (var page : java.util.List.of(CosmeticsScreen.Page.SIZE, CosmeticsScreen.Page.NICK, CosmeticsScreen.Page.COLOR,
            CosmeticsScreen.Page.CHROMA, CosmeticsScreen.Page.PRESETS, CosmeticsScreen.Page.ANIMATIONS, CosmeticsScreen.Page.DAMAGE,
            CosmeticsScreen.Page.ITEMS, CosmeticsScreen.Page.ACCESSORIES, CosmeticsScreen.Page.PARTY, CosmeticsScreen.Page.SKINS,
            CosmeticsScreen.Page.MAP, CosmeticsScreen.Page.SYNC, CosmeticsScreen.Page.MODULES)) {
            context.runOnClient(client -> {
                ClientUiAccess.setScreen(client, new CosmeticsScreen(null, page));
                @SuppressWarnings("unchecked") var options = (java.util.List<SettingsSearch.Entry>) read(ClientUiAccess.screen(client), "searchOptions");
                for (var option : options) if (SettingsCatalog.entries().stream().noneMatch(entry -> entry.page().equals(page.name())
                    && SettingsSearch.sameSetting(option.title(), entry.title())))
                    throw new AssertionError("Setting missing from lightweight catalogue: " + page + " / " + option.title());
            });
        }
        checkSelectedCreatureSearch(context);
        context.runOnClient(client -> ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.SIZE)));
        type(context, "Search Settings", "Location Tracer Color");
        click(context, "Location Tracer Color", PinkUi.Action.class);
        context.runOnClient(client -> {
            var screen = ClientUiAccess.screen(client);
            var target = widget(client, "Location Tracer Color", PinkUi.Field.class);
            if (read(screen, "page") != CosmeticsScreen.Page.PARTY || !target.visible
                || target.getY() < (Integer) read(screen, "viewTop") || target.getBottom() > (Integer) read(screen, "viewBottom"))
                throw new AssertionError("Search did not reveal the current setting location");
            // A thumb press must retain the original grab point instead of recentering the list.
            var offset = CosmeticsScreen.class.getDeclaredField("scroll"); offset.setAccessible(true);
            var maximum = CosmeticsScreen.class.getDeclaredMethod("maxScroll"); maximum.setAccessible(true);
            var apply = CosmeticsScreen.class.getDeclaredMethod("positionControls"); apply.setAccessible(true);
            double max = (Double) maximum.invoke(screen); offset.setDouble(screen, Math.floor(max / 2)); apply.invoke(screen);
            var barMethod = CosmeticsScreen.class.getDeclaredMethod("contentScrollbar"); barMethod.setAccessible(true);
            Object bar = barMethod.invoke(screen);
            var startMethod = bar.getClass().getDeclaredMethod("thumbStart", int.class); startMethod.setAccessible(true);
            int thumbTop = (Integer) startMethod.invoke(bar, (int) offset.getDouble(screen));
            int x = (Integer) read(screen, "contentX") + (Integer) read(screen, "contentWidth") + 8;
            double before = offset.getDouble(screen);
            var info = new net.minecraft.client.input.MouseButtonInfo(ClientInput.MOUSE_LEFT, 0);
            var press = new net.minecraft.client.input.MouseButtonEvent(x, thumbTop + 2, info);
            if (!screen.mouseClicked(press, false)) throw new AssertionError("Scrollbar thumb was not clickable");
            if (Math.abs(offset.getDouble(screen) - before) > max / Math.max(1, (Integer) read(screen, "viewBottom") - (Integer) read(screen, "viewTop")) + 1)
                throw new AssertionError("Scrollbar jumped when grabbed away from its center");
            var end = new net.minecraft.client.input.MouseButtonEvent(x, (Integer) read(screen, "viewBottom") + 100, info);
            screen.mouseDragged(end, 0, 500); screen.mouseReleased(end);
            if (offset.getDouble(screen) != max) throw new AssertionError("Scrollbar drag did not reach the end");
        });
        context.takeScreenshot("studio-search-destination");
        context.runOnClient(client -> ClientUiAccess.setScreen(client, null));
        System.out.println("STUDIO_OVERVIEW_RESPONSIVE_NAVIGATION_AND_LIGHTWEIGHT_SEARCH_OK");
    }
    private static void checkSelectedCreatureSearch(ClientGameTestContext context) throws Exception {
        String previous = context.computeOnClient(client -> ConfigManager.creatureModel);
        try {
            context.runOnClient(client -> {
                ConfigManager.creatureModel = "chicken";
                ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.SYNC));
            });
            type(context, "Search Settings", "Chicken");
            click(context, "Chicken", PinkUi.Action.class);
            context.runOnClient(client -> {
                var screen = ClientUiAccess.screen(client);
                var target = widget(client, "✓ Chicken", PinkUi.Action.class);
                if (read(screen, "page") != CosmeticsScreen.Page.SIZE || (Double) read(screen, "scroll") <= 0
                    || !target.visible || target.getY() < (Integer) read(screen, "viewTop")
                    || target.getBottom() > (Integer) read(screen, "viewBottom"))
                    throw new AssertionError("Search did not reveal the currently selected creature model");
            });
        } finally {
            context.runOnClient(client -> {
                ConfigManager.creatureModel = previous;
                ClientUiAccess.setScreen(client, null);
            });
        }
    }
    private static Object read(Object object,String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static AbstractWidget widget(net.minecraft.client.Minecraft client,String prefix,Class<?> type) {
        return ClientUiAccess.screen(client).children().stream().filter(type::isInstance).map(child -> (AbstractWidget) child)
            .filter(child -> child.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow(() -> new AssertionError("Missing control " + prefix));
    }
    private static void click(ClientGameTestContext context,String prefix,Class<?> type) throws Exception {
        var position = new AtomicReference<double[]>();
        context.runOnClient(client -> {
            var screen = ClientUiAccess.screen(client); var widget = widget(client,prefix,type);
            if (widget.getX() < (Integer) read(screen, "contentX")) {
                if (widget instanceof RepositoryButton || widget.getMessage().getString().equals("Settings")) {
                    // Footer controls are fixed outside both scroll viewports.
                } else if (widget.getY() < (Integer) read(screen, "navTop") || widget.getBottom() > (Integer) read(screen, "navBottom")) {
                    var offset = CosmeticsScreen.class.getDeclaredField("navScroll"); offset.setAccessible(true);
                    var maximum = CosmeticsScreen.class.getDeclaredMethod("maxNavScroll"); maximum.setAccessible(true);
                    offset.setInt(screen, Math.clamp(offset.getInt(screen) + widget.getY() - (Integer) read(screen, "navTop"), 0, (Integer) maximum.invoke(screen)));
                    var apply = CosmeticsScreen.class.getDeclaredMethod("positionNavigation"); apply.setAccessible(true); apply.invoke(screen);
                }
            } else if (!(widget instanceof PinkUi.Field && widget.getMessage().getString().equals("Search Settings"))
                && (widget.getY() < (Integer)read(screen,"viewTop") || widget.getBottom() > (Integer)read(screen,"viewBottom"))) {
                var scroll = CosmeticsScreen.class.getDeclaredField("scroll"); scroll.setAccessible(true);
                var maximum = CosmeticsScreen.class.getDeclaredMethod("maxScroll"); maximum.setAccessible(true);
                scroll.setDouble(screen,Math.clamp((Double)scroll.get(screen) + widget.getY() - (Integer)read(screen,"viewTop") - 20,0,(Double)maximum.invoke(screen)));
                var apply = CosmeticsScreen.class.getDeclaredMethod("positionControls"); apply.setAccessible(true); apply.invoke(screen);
            }
            double scale = client.getWindow().getGuiScale();
            position.set(new double[]{(widget.getX() + widget.getWidth()/2.0)*scale,(widget.getY() + widget.getHeight()/2.0)*scale});
        });
        context.getInput().setCursorPos(position.get()[0],position.get()[1]); context.getInput().pressMouse(ClientInput.MOUSE_LEFT); context.waitTicks(2);
    }
    private static void type(ClientGameTestContext context,String name,String value) throws Exception {
        click(context,name,PinkUi.Field.class);
        context.runOnClient(client -> {
            var field = (PinkUi.Field)widget(client,name,PinkUi.Field.class);
            if (!field.isFocused()) throw new AssertionError("Click did not focus " + name);
            field.setValue("");
        });
        context.getInput().typeChars(value); context.waitTicks(2);
    }
    private static void bottom(ClientGameTestContext context) {
        context.getInput().setCursorPos(950,500); context.getInput().scroll(-100); context.waitTicks(2);
    }
}
