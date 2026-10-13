// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.*;
import com.customcosmetics.config.*;
import com.customcosmetics.features.nickname.*;
import com.customcosmetics.gui.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import java.util.concurrent.atomic.AtomicReference;

/** Use real mouse/keyboard input and reload the on-disk preset library. */
final class NicknamePresetGameTest {
    static void check(ClientGameTestContext context) {
        try {
            context.getInput().resizeWindow(1600,1000);
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                ConfigManager.nickTweaksNickChanger="ToryHi";
                ConfigManager.nickTweaksGradientStyle="shimmer";
                ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.COLOR));
            }); context.waitTicks(3);
            type(context,"Shimmer Size value","0.35");
            type(context,"Shimmer Interval value","4.7");
            click(context,"Editing:",PinkUi.Action.class);
            type(context,"HEX color","#0055FF");
            click(context,"Highlight",PinkUi.Action.class); type(context,"HEX color","#66DDFF");
            context.runOnClient(client -> {
                if(ConfigManager.nickTweaksShimmerColor!=0x0055FF||ConfigManager.nickTweaksShimmerHighlightColor!=0x66DDFF||Math.abs(ConfigManager.nickTweaksShimmerSize-.35)>.0001||Math.abs(ConfigManager.nickTweaksShimmerInterval-4.7)>.0001)
                    throw new AssertionError("Shimmer color controls did not update both colors");
            }); context.takeScreenshot("nickname-custom-shimmer-colors");
            click(context,"Presets",PinkUi.Action.class); type(context,"Preset Name","ToryHi Blue");
            click(context,"Save / Update",PinkUi.Action.class);
            var saved=new AtomicReference<NicknameColors>();
            context.runOnClient(client -> {
                saved.set(NicknameColors.capture());
                var disk=new NicknamePresetStore(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("nickname-color-presets.json"));
                disk.load();if(!saved.get().equals(disk.entries().get("ToryHi Blue")))throw new AssertionError("Preset was not saved to disk");
                ConfigManager.nickTweaksGradientStartColor=0;ConfigManager.nickTweaksShimmerColor=0;ConfigManager.nickTweaksChromaEnabled=true;
                ConfigManager.nickTweaksNickChanger="KeepThis";
            });
            click(context,"Apply: ToryHi Blue",PinkUi.Action.class);
            context.runOnClient(client -> {
                if(!NicknameColors.capture().equals(saved.get()))throw new AssertionError("Apply lost saved colors or mode");
                if(!ConfigManager.nickTweaksNickChanger.equals("KeepThis"))throw new AssertionError("Applying colors changed the nickname");
            });context.takeScreenshot("nickname-color-presets");
            click(context,"Delete: ToryHi Blue",PinkUi.Action.class);
            context.runOnClient(client -> {
                var disk=new NicknamePresetStore(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("nickname-color-presets.json"));disk.load();
                if(disk.entries().containsKey("ToryHi Blue"))throw new AssertionError("Delete was not persisted");
                if(!NicknameColors.capture().equals(saved.get()))throw new AssertionError("Deleting a preset changed active colors");
            });
            context.getInput().resizeWindow(854,480);context.waitTicks(3);context.takeScreenshot("nickname-presets-compact");
            context.runOnClient(client -> ClientUiAccess.setScreen(client,null));
            System.out.println("NICKNAME_PRESETS_GUI_SAVE_APPLY_DELETE_AND_RELOAD_OK");
        } catch(Exception error){throw new AssertionError(error);}
    }
    private static Object read(Object object,String name)throws Exception{
        var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private static AbstractWidget widget(net.minecraft.client.Minecraft client,String prefix,Class<?> type){
        return ClientUiAccess.screen(client).children().stream().filter(type::isInstance).map(child->(AbstractWidget)child)
            .filter(child->child.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow(()->new AssertionError("Missing control "+prefix));
    }
    static void click(ClientGameTestContext context,String prefix,Class<?> type)throws Exception{
        var position=new AtomicReference<double[]>();
        context.runOnClient(client->{
            var screen=ClientUiAccess.screen(client);var widget=widget(client,prefix,type);
            if(widget.getY()<(Integer)read(screen,"viewTop")||widget.getBottom()>(Integer)read(screen,"viewBottom")){
                var scroll=CosmeticsScreen.class.getDeclaredField("scroll");scroll.setAccessible(true);
                var maximum=CosmeticsScreen.class.getDeclaredMethod("maxScroll");maximum.setAccessible(true);
                scroll.setDouble(screen,Math.clamp((Double)scroll.get(screen)+widget.getY()-(Integer)read(screen,"viewTop")-20,0,(Double)maximum.invoke(screen)));
                var apply=CosmeticsScreen.class.getDeclaredMethod("positionControls");apply.setAccessible(true);apply.invoke(screen);
            }
            double scale=client.getWindow().getGuiScale();position.set(new double[]{(widget.getX()+widget.getWidth()/2.0)*scale,(widget.getY()+widget.getHeight()/2.0)*scale});
        });
        context.getInput().setCursorPos(position.get()[0],position.get()[1]);context.getInput().pressMouse(ClientInput.MOUSE_LEFT);context.waitTicks(2);
    }
    private static void type(ClientGameTestContext context,String name,String value)throws Exception{
        click(context,name,PinkUi.Field.class);
        context.runOnClient(client->{var field=(PinkUi.Field)widget(client,name,PinkUi.Field.class);
            if(!field.isFocused())throw new AssertionError("Click did not focus "+name);field.setValue("");});
        context.getInput().typeChars(value);context.waitTicks(2);
    }
}
