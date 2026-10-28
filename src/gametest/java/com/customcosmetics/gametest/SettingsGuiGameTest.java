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
            if ((Integer)read(ClientUiAccess.screen(client),"panelWidth") != StudioLayout.of(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()).width()) throw new AssertionError("Preview panel was not widened");
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
        bottom(context); context.takeScreenshot("nick-options-capitalized");
        click(context,"Chroma",PinkUi.Action.class);
        context.runOnClient(client -> {
            for (var child : ClientUiAccess.screen(client).children()) if (child instanceof PinkUi.Slider slider)
                if (!Character.isUpperCase(slider.getMessage().getString().charAt(0))) throw new AssertionError("Lowercase NickTweaks option");
        });
        bottom(context); context.takeScreenshot("chroma-options-capitalized");
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
        checkTheme(context);
        context.getInput().resizeWindow(854,480); context.waitTicks(3);
        System.out.println("SETTINGS_GUI_MEMORY_DEV_LAYOUT_AND_FILES_OK");
    }

    public static void checkLocalizationAndEntrance(ClientGameTestContext context)throws Exception{
        context.getInput().resizeWindow(1600,1000);context.waitTicks(3);
        String gameLanguage=context.computeOnClient(client->client.options.languageCode);
        context.runOnClient(client->{ConfigManager.uiLanguage="auto";ConfigManager.uiAnimationsEnabled=true;ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SYNC));});
        context.waitFor(client->{try{return (Float)read(ClientUiAccess.screen(client),"entranceScale")<.999f;}catch(Exception e){throw new AssertionError(e);}},20);
        context.takeScreenshot("interface-opening-spring");
        // Click at the visible transformed position, while the entrance is still moving.
        var point=context.computeOnClient(client->{var screen=ClientUiAccess.screen(client);var w=widget(client,"Interface",PinkUi.Action.class);double scale=(Float)read(screen,"entranceScale"),cx=(Integer)read(screen,"panelX")+(Integer)read(screen,"panelWidth")/2d,cy=(Integer)read(screen,"panelY")+(Integer)read(screen,"panelHeight")/2d,pixels=client.getWindow().getGuiScale();return new double[]{(cx+(w.getX()+w.getWidth()/2d-cx)*scale)*pixels,(cy+(w.getY()+w.getHeight()/2d-cy)*scale)*pixels};});
        context.getInput().setCursorPos(point[0],point[1]);context.getInput().pressMouse(ClientInput.MOUSE_LEFT);context.waitTicks(5);
        context.runOnClient(client->{if(read(ClientUiAccess.screen(client),"page")!=CosmeticsScreen.Page.THEME)throw new AssertionError("Opening transform broke pointer dispatch");});
        click(context,"Settings",PinkUi.Action.class);click(context,"Language:",PinkUi.Action.class);context.getInput().setCursorPos(1595,995);context.waitTicks(5);
        context.runOnClient(client->{
            if(!ConfigManager.uiLanguage.equals("zh_cn")||!UiText.language().equals("zh_cn"))throw new AssertionError("Language switch did not take effect immediately");
            if(!gameLanguage.equals(client.options.languageCode))throw new AssertionError("Mod language altered Minecraft's language");
            var action=widget(client,"Settings",PinkUi.Action.class);if(!"设置".equals(read(action,"cachedLabel")))throw new AssertionError("Sidebar still rendered its English label");
            if(!com.customcosmetics.utils.MessageUtils.createMessageWithPrefix("Syncing remote data...").getString().contains("正在同步"))throw new AssertionError("Mod feedback was not translated");
        });context.takeScreenshot("interface-chinese-settings");
        click(context,"Interface",PinkUi.Action.class);click(context,"Mode: Dark",PinkUi.Action.class);context.getInput().setCursorPos(1595,995);context.waitTicks(4);context.takeScreenshot("interface-chinese-light-theme");
        click(context,"Mode: Light",PinkUi.Action.class);click(context,"Settings",PinkUi.Action.class);
        type(context,"Search Settings","护符");context.waitTicks(4);context.takeScreenshot("interface-chinese-search");
        click(context,"Accessory Helper Tooltip",PinkUi.Action.class);
        context.runOnClient(client->{if(read(ClientUiAccess.screen(client),"page")!=CosmeticsScreen.Page.ITEMS)throw new AssertionError("Chinese search did not reach the setting");});
        click(context,"NickTweaks",PinkUi.Action.class);
        context.runOnClient(client->{ConfigManager.nickTweaksNickChanger="Settings";ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.NICK));});context.waitTicks(3);
        context.runOnClient(client->{if(!((PinkUi.Field)widget(client,"Nick Changer",PinkUi.Field.class)).getValue().equals("Settings"))throw new AssertionError("Localization changed user text");});
        context.takeScreenshot("interface-chinese-nickname");
        click(context,"Rank",PinkUi.Action.class);context.waitTicks(4);
        context.runOnClient(client->{
            for(var color:com.customcosmetics.features.nickname.RankSettings.PlusColor.values()){
                var button=widget(client,color.label,PinkUi.Action.class);
                if(!UiText.tr(color.label).equals(read(button,"cachedLabel")))throw new AssertionError("Rank color still English: "+color.label);
            }
            if(SystemUiFont.loaded()!=SystemUiFont.source().isPresent())throw new AssertionError("System font load state does not match availability");
            var own=UiFonts.get(client.font);
            String sample="微软雅黑中文护符设置";
            int actual=own.width(sample);
            var styled=net.minecraft.network.chat.Component.literal(sample).withStyle(style->style.withFont(UiFonts.CHINESE));
            if(actual<=0||actual!=client.font.width(styled))throw new AssertionError("GUI font provider differs from mod-owned styled text");
            System.out.println("CHINESE_FONT_NATIVE_OK systemYaHei="+SystemUiFont.loaded()+" width="+actual);
        });context.takeScreenshot("interface-chinese-rank-font");
        context.runOnClient(client->ClientUiAccess.setScreen(client,new WardrobeScreen(null,"back")));context.waitTicks(4);
        context.runOnClient(client->{if(ClientUiAccess.screen(client).children().stream().filter(PinkUi.Action.class::isInstance).map(PinkUi.Action.class::cast).noneMatch(w->w.getMessage().getString().equals("背部")))throw new AssertionError("Body slot was translated as navigation Back");});
        context.takeScreenshot("interface-chinese-wardrobe");
        context.runOnClient(client->ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.RANK)));context.waitTicks(4);
        click(context,"Settings",PinkUi.Action.class);click(context,"Language:",PinkUi.Action.class);context.waitTicks(4);
        context.runOnClient(client->{if(!ConfigManager.uiLanguage.equals("en_us")||!UiText.tr("Settings").equals("Settings"))throw new AssertionError("Explicit English selection failed");ConfigManager.nickTweaksNickChanger="";ClientUiAccess.setScreen(client,null);});
        ConfigManager.awaitPendingSaves();var settings=JsonParser.parseString(Files.readString(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("config.json"))).getAsJsonObject().getAsJsonObject("settings");
        if(!settings.get("uiLanguage").getAsString().equals("en_us"))throw new AssertionError("Language choice was not saved");
        context.runOnClient(client->{ConfigManager.uiAnimationsEnabled=false;ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SYNC));});context.waitTicks(2);
        context.runOnClient(client->{if((Float)read(ClientUiAccess.screen(client),"entranceScale")!=1)throw new AssertionError("Reduced motion still scaled the panel");ConfigManager.uiAnimationsEnabled=true;ClientUiAccess.setScreen(client,null);});
        System.out.println("CHINESE_ENGLISH_LANGUAGE_OVERRIDE_SEARCH_USER_TEXT_PERSISTENCE_AND_OPENING_INPUT_OK");
    }

    /** Clicking beyond one pane must not leave that pane hidden by the input clipping mask. */
    public static void checkViewportVisibility(ClientGameTestContext context) throws Exception {
        context.getInput().resizeWindow(1280,800);context.waitTicks(3);
        for(String mode:java.util.List.of("light","dark")){
            context.runOnClient(client->{client.options.guiScale().set(2);ConfigManager.uiThemeMode=mode;ConfigManager.uiAnimationsEnabled=false;
                ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.THEME));});
            click(context,"Accent Color",PinkUi.Field.class);
            context.runOnClient(client->{
                var screen=ClientUiAccess.screen(client);var field=widget(client,"Accent Color",PinkUi.Field.class);
                if(field.getY()+field.getHeight()/2<=(Integer)read(screen,"navBottom"))throw new AssertionError("Fixture did not click beyond the sidebar viewport");
                assertPaneVisibility(screen,"sidebarControls",(Integer)read(screen,"navTop"),(Integer)read(screen,"navBottom"));
            });
            click(context,"Search Settings",PinkUi.Field.class);
            context.runOnClient(client->{var screen=ClientUiAccess.screen(client);
                assertPaneVisibility(screen,"controls",(Integer)read(screen,"viewTop"),(Integer)read(screen,"viewBottom"));
                assertPaneVisibility(screen,"sidebarControls",(Integer)read(screen,"navTop"),(Integer)read(screen,"navBottom"));});
            context.getInput().setCursorPos(1270,790);context.waitTicks(3);
            assertSidebarPixels(context,mode);
            context.takeScreenshot("theme-"+mode+"-sidebar-after-field-and-search-clicks");
        }
        context.runOnClient(client->{ConfigManager.uiThemeMode="dark";ConfigManager.uiAnimationsEnabled=true;ClientUiAccess.setScreen(client,null);});
        System.out.println("THEME_LIGHT_DARK_SIDEBAR_PIXELS_AND_CLIPPED_INPUT_VISIBILITY_RESTORE_OK");
    }
    private static void assertPaneVisibility(Object screen,String listName,int top,int bottom)throws Exception{
        for(Object placement:(java.util.List<?>)read(screen,listName)){
            var method=placement.getClass().getDeclaredMethod("widget");method.setAccessible(true);var w=(AbstractWidget)method.invoke(placement);
            int inset=listName.equals("controls")&&w instanceof PinkUi.Field field?field.insetY():0;
            boolean expected=w.getBottom()+inset>top&&w.getY()-inset<bottom;
            if(w.visible!=expected)throw new AssertionError(listName+" visibility was not restored after a clipped click: "+w.getMessage().getString());
        }
    }
    private static void assertSidebarPixels(ClientGameTestContext context,String mode)throws Exception{
        var count=new java.util.concurrent.CompletableFuture<Integer>();
        context.runOnClient(client->{
            var w=widget(client,"NickTweaks",PinkUi.Action.class);double scale=client.getWindow().getGuiScale();
            int expected=PinkUi.TEXT,left=(int)((w.getX()+11)*scale),top=(int)((w.getY()+(w.getHeight()-9)/2)*scale);
            java.lang.reflect.Method method;
            try{method=client.gameRenderer.getClass().getMethod("mainRenderTarget");}catch(NoSuchMethodException legacy){method=client.getClass().getMethod("getMainRenderTarget");}
            var target=method.invoke(method.getDeclaringClass().isInstance(client.gameRenderer)?client.gameRenderer:client);
            net.minecraft.client.Screenshot.takeScreenshot((com.mojang.blaze3d.pipeline.RenderTarget)target,image->{try(image){int found=0;
                for(int y=top;y<top+(int)(9*scale);y++)for(int x=left;x<left+(int)(client.font.width("NickTweaks")*scale);x++){
                    int c=image.getPixel(x,y);if(Math.abs((c>>16&255)-(expected>>16&255))<15&&Math.abs((c>>8&255)-(expected>>8&255))<15&&Math.abs((c&255)-(expected&255))<15)found++;
                }count.complete(found);
            }catch(Throwable error){count.completeExceptionally(error);}});
        });context.waitFor(client->count.isDone());
        if(count.join()<40)throw new AssertionError(mode+" sidebar label is not visibly rendered in the theme text color: "+count.join());
    }

    /** Native theme editing, persistent local preferences, and both high/low contrast modes. */
    private static void checkTheme(ClientGameTestContext context) throws Exception {
        context.getInput().resizeWindow(1600,1000);context.waitTicks(3);
        context.runOnClient(client->{
            ConfigManager.uiThemeMode="dark";ConfigManager.uiAccentColor=0x6DB29A;
            ConfigManager.uiAnimationsEnabled=true;ConfigManager.uiBackgroundBlur=false;
            ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.THEME));
        });context.getInput().setCursorPos(1595,995);context.waitTicks(5);context.takeScreenshot("interface-dark");
        var hoverBounds=context.computeOnClient(client->{var w=widget(client,"Ocean",PinkUi.Action.class);return new int[]{w.getX(),w.getY(),w.getWidth(),w.getHeight()};});
        var hoverPoint=context.computeOnClient(client->{double scale=client.getWindow().getGuiScale();return new double[]{(hoverBounds[0]+hoverBounds[2]/2d)*scale,(hoverBounds[1]+hoverBounds[3]/2d)*scale};});
        context.getInput().setCursorPos(hoverPoint[0],hoverPoint[1]);context.waitTicks(6);
        context.runOnClient(client->{var w=widget(client,"Ocean",PinkUi.Action.class);
            if(w.getX()!=hoverBounds[0]||w.getY()!=hoverBounds[1]||w.getWidth()!=hoverBounds[2]||w.getHeight()!=hoverBounds[3]||!w.isMouseOver(hoverBounds[0]+1,hoverBounds[1]+1))
                throw new AssertionError("Hover feedback moved the logical button bounds or broke edge clicks");
        });
        context.takeScreenshot("interface-spring-hover-fixed-hitbox");
        type(context,"Accent Color","#729FD4");
        context.runOnClient(client->{if(ConfigManager.uiAccentColor!=0x729FD4||PinkUi.PINK!=0xFF729FD4)throw new AssertionError("Typed theme color was not applied");});
        type(context,"Accent Color","#invalid");
        context.runOnClient(client->{if(ConfigManager.uiAccentColor!=0x729FD4)throw new AssertionError("Invalid color overwrote the theme");});
        type(context,"Accent Color","#729FD4");
        click(context,"Mode: Dark",PinkUi.Action.class);context.waitTicks(5);
        context.runOnClient(client->{if(!ConfigManager.uiThemeMode.equals("light")||PinkUi.PANEL!=0xFFFAFBFC)throw new AssertionError("Light palette was not rebuilt");});
        context.takeScreenshot("interface-light");
        click(context,"Interface Animations",PinkUi.Toggle.class);
        context.runOnClient(client->{if(ConfigManager.uiAnimationsEnabled)throw new AssertionError("Reduced motion did not apply");});
        click(context,"Background Blur",PinkUi.Toggle.class);
        context.runOnClient(client->{if(!ConfigManager.uiBackgroundBlur)throw new AssertionError("Optional blur did not apply");});
        context.getInput().pressKey(ClientInput.ESCAPE);context.waitTicks(3);
        ConfigManager.awaitPendingSaves();
        var saved=JsonParser.parseString(Files.readString(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("config.json"))).getAsJsonObject().getAsJsonObject("settings");
        if(!saved.get("uiThemeMode").getAsString().equals("light")||saved.get("uiAccentColor").getAsInt()!=0x729FD4||saved.get("uiAnimationsEnabled").getAsBoolean()||!saved.get("uiBackgroundBlur").getAsBoolean())
            throw new AssertionError("Closing the theme menu failed to persist preferences");
        context.runOnClient(client->ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.THEME)));
        click(context,"Reset Theme",PinkUi.Action.class);
        context.runOnClient(client->{if(!ConfigManager.uiAnimationsEnabled||ConfigManager.uiBackgroundBlur||!ConfigManager.uiThemeMode.equals("dark")||ConfigManager.uiAccentColor!=0xFFA8CA)throw new AssertionError("Theme reset failed");});
        click(context,"Overview",PinkUi.Action.class);context.getInput().setCursorPos(1595,995);context.waitTicks(5);context.takeScreenshot("interface-home");
        click(context,"Animations",PinkUi.Action.class);context.getInput().setCursorPos(1595,995);context.waitTicks(5);context.takeScreenshot("interface-settings");
        context.runOnClient(client->ClientUiAccess.setScreen(client,null));context.waitTicks(5);
        System.out.println("UI_THEME_NATIVE_EDIT_INVALID_INPUT_LIGHT_DARK_REDUCED_MOTION_BLUR_PERSISTENCE_AND_RESET_OK");
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
            CosmeticsScreen.Page.MAP, CosmeticsScreen.Page.SYNC, CosmeticsScreen.Page.MODULES, CosmeticsScreen.Page.THEME)) {
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
