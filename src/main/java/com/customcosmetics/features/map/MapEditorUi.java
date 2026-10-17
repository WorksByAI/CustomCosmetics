// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.gui.MapBlockScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.*;
import net.minecraft.resources.Identifier;

public final class MapEditorUi {
    private static KeyMapping palette,edit;
    private static String pendingScreen;
    private MapEditorUi(){}
    public static void init(){
        var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("customcosmetics","map_editor_tools"));
        palette=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.customcosmetics.map_blocks",InputConstants.UNKNOWN.getValue(),category));
        edit=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.customcosmetics.map_edit",InputConstants.UNKNOWN.getValue(),category));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Identifier.fromNamespaceAndPath("customcosmetics","map_editor"),(g,delta)->{
            var client=Minecraft.getInstance();if(!MapEditor.enabled||!MapEditor.editing||!MapEditor.showHud||client.player==null||ClientUiAccess.screen(client)!=null)return;
            int x=10,y=32;String[] lines={"Map Editor · Visual Only","Room: "+MapEditor.roomLabel(),"Last edited: "+MapEditor.lastRoom,"Right: place · Left: hide/delete · Middle: pick"};
            g.fill(x-5,y-5,x+Math.min(330,client.getWindow().getGuiScaledWidth()-15),y+70,0xB020242C);
            for(int i=0;i<lines.length;i++)g.text(client.font,lines[i],x,y+i*12,i==0?0xFFFFA8CA:0xFFE1E3EA,false);
            var state=MapEditor.state(MapEditor.material);if(state!=null)g.item(MapEditor.icon(state),x,y+49);g.text(client.font,MapEditor.material,x+23,y+54,0xFF7BE6A2,false);
        });
    }
    public static void open(){var client=Minecraft.getInstance();ClientUiAccess.setScreen(client,new MapBlockScreen(ClientUiAccess.screen(client)));}
    public static void requestOpen(){pendingScreen="blocks";}
    public static void requestImport(){pendingScreen="import";}
    public static void requestPresets(){pendingScreen="presets";}
    public static void requestReplace(){pendingScreen="replace";}
    public static void toggleEdit(){MapEditor.enabled=true;MapEditor.editing=!MapEditor.editing;MapEditor.refresh();MapEditor.notice("Map edit mode "+(MapEditor.editing?"ON. Right: place; Left: hide/delete; Middle: pick. /customcosmetics map blocks opens the palette.":"OFF."));}
    public static void tick(Minecraft client){if(pendingScreen!=null&&!(ClientUiAccess.screen(client) instanceof net.minecraft.client.gui.screens.ChatScreen)){String pending=pendingScreen;pendingScreen=null;if(pending.equals("blocks"))open();else if(pending.equals("presets"))ClientUiAccess.setScreen(client,new com.customcosmetics.gui.MapPresetScreen(ClientUiAccess.screen(client)));else if(pending.equals("replace"))ClientUiAccess.setScreen(client,new com.customcosmetics.gui.MapReplaceScreen(ClientUiAccess.screen(client)));else ClientUiAccess.setScreen(client,new com.customcosmetics.gui.FmeImportScreen(ClientUiAccess.screen(client)));}while(palette!=null&&palette.consumeClick())if(ClientUiAccess.screen(client)==null)open();while(edit!=null&&edit.consumeClick())if(ClientUiAccess.screen(client)==null)toggleEdit();}
}
