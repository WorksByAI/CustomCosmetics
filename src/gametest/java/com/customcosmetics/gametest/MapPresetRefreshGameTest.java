// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.features.map.*;
import com.customcosmetics.gui.MapPresetScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Copy files from an external peer while the preset screen remains open, then click Refresh. */
public final class MapPresetRefreshGameTest implements FabricClientGameTest {
    private Path original,shared;private byte[] template;
    private Map<MapEdits.Position,MapEdits.Edit> active;
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{com.customcosmetics.config.ConfigManager.offlineMode=true;com.customcosmetics.config.ConfigManager.syncEnabled=false;});
        try(var world=context.worldBuilder().create()){
            context.waitTicks(5);
            if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu")){
                for(String name:List.of("ModMenuOptionsScreen","ModsScreen")){
                    context.runOnClient(client->{try{ClientUiAccess.setScreen(client,(net.minecraft.client.gui.screens.Screen)Class.forName("com.terraformersmc.modmenu.gui."+name).getConstructor(net.minecraft.client.gui.screens.Screen.class).newInstance(new Object[]{null}));}catch(ReflectiveOperationException error){throw new AssertionError(error);}});
                    context.waitTicks(2);
                }
                context.runOnClient(client->ClientUiAccess.setScreen(client,null));
                System.out.println("MOD_MENU_OPTIONS_AND_MODS_SCREEN_RENDER_OK");
            }
            context.runOnClient(client->{try{
                MapEditor.forceKuudra(true);MapEditor.autoRooms=false;MapEditor.enabled=true;
                MapEditor.put(new net.minecraft.core.BlockPos(0,70,0),"minecraft:gold_block");MapEditor.refresh();
                MapEditor.presetName="Original";MapEditor.savePreset();active=MapEditor.DATA.snapshot();
                var folder=MapEditor.settingsPath().getParent().resolve("presets");
                try(var files=Files.list(folder)){original=files.filter(p->p.toString().endsWith(".ccmap")).findFirst().orElseThrow();}
                template=Files.readAllBytes(original);shared=folder.resolve("Christmas.CCMAP");writeShared();
                check(!MapEditor.presets().contains("Shared from friend"),"File appeared without a refresh");
                ClientUiAccess.setScreen(client,new MapPresetScreen(null));clickRefresh();
            }catch(IOException error){throw new AssertionError(error);}});
            context.waitFor(client->!MapEditor.busy());
            context.runOnClient(client->{check(MapEditor.presets().contains("Shared from friend"),"Refresh did not load the renamed external file");check(MapEditor.DATA.snapshot()==active,"Refresh changed active map edits");});
            context.waitTicks(2);context.takeScreenshot("preset-refresh-imported");
            context.runOnClient(client->{try{Files.delete(original);clickRefresh();}catch(IOException error){throw new AssertionError(error);}});
            context.waitFor(client->!MapEditor.busy());
            context.runOnClient(client->{check(!MapEditor.presets().contains("Original"),"Deleted file remains in the list");try{Files.writeString(shared,"broken");clickRefresh();}catch(IOException error){throw new AssertionError(error);}});
            context.waitFor(client->!MapEditor.busy());
            context.runOnClient(client->{check(MapEditor.presets().equals(List.of("Shared from friend")),"Failed refresh erased the previous library");try{
                var field=MapPresetScreen.class.getDeclaredField("status");field.setAccessible(true);check(field.get(ClientUiAccess.screen(client)).toString().contains("Christmas.CCMAP"),"Read error omitted the filename");
                Files.delete(shared);clickRefresh();
            }catch(ReflectiveOperationException|IOException error){throw new AssertionError(error);}});
            context.waitFor(client->!MapEditor.busy());
            context.runOnClient(client->{check(MapEditor.presets().isEmpty(),"Empty directory did not clear the list");check(MapEditor.DATA.snapshot()==active,"Reload changed the active map");ClientUiAccess.setScreen(client,null);MapEditor.enabled=false;MapEditor.forceKuudra(false);});
        }
        System.out.println("PRESET_REFRESH_EXTERNAL_ADD_DELETE_BAD_FILE_KEEPS_CACHE_ACTIVE_MAP_UNCHANGED_OK");
    }
    private void writeShared()throws IOException{
        try(var in=new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(template)));var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(shared)))){
            out.writeInt(in.readInt());out.writeInt(in.readInt());in.readUTF();out.writeUTF("Shared from friend");in.transferTo(out);
        }
    }
    private static void clickRefresh(){
        var screen=ClientUiAccess.screen(net.minecraft.client.Minecraft.getInstance());
        var button=(net.minecraft.client.gui.components.AbstractWidget)screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.AbstractWidget w&&w.visible&&w.active&&w.getMessage().getString().equals("Refresh")).findFirst().orElseThrow();
        button.onClick(new net.minecraft.client.input.MouseButtonEvent(button.getX()+2,button.getY()+2,new net.minecraft.client.input.MouseButtonInfo(0,0)),false);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
