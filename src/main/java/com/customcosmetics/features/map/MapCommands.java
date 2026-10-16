// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.*;
public final class MapCommands {
    private MapCommands(){}
    public static LiteralArgumentBuilder<FabricClientCommandSource> command(){
        var root=ClientCommands.literal("map").executes(c->{com.customcosmetics.client.CustomCosmeticsClient.requestGui(com.customcosmetics.gui.CosmeticsScreen.Page.MAP);return 1;});
        root.then(ClientCommands.literal("edit").executes(c->run(()->{MapEditor.enabled=true;MapEditor.editing=!MapEditor.editing;MapEditor.refresh();MapEditor.notice("Map edit mode "+(MapEditor.editing?"ON. Right: paint; Shift+right: place; Left: remove edit; Middle: pick.":"OFF."));})));
        root.then(ClientCommands.literal("undo").executes(c->run(MapEditor::undo)));
        root.then(ClientCommands.literal("clear").executes(c->run(MapEditor::clear)));
        root.then(ClientCommands.literal("pos1").executes(c->run(()->MapEditor.mark(false))));
        root.then(ClientCommands.literal("pos2").executes(c->run(()->MapEditor.mark(true))));
        root.then(ClientCommands.literal("fill").executes(c->run(MapEditor::fill)));
        root.then(ClientCommands.literal("anchor").executes(c->run(MapEditor::setAnchor)));
        root.then(ClientCommands.literal("material").then(ClientCommands.argument("block",StringArgumentType.word()).executes(c->run(()->{String id=StringArgumentType.getString(c,"block");if(MapEditor.state(id)==null)throw new IllegalArgumentException("Unknown block");MapEditor.material=id;}))));
        root.then(ClientCommands.literal("save").then(ClientCommands.argument("name",StringArgumentType.greedyString()).executes(c->run(()->{MapEditor.presetName=StringArgumentType.getString(c,"name");MapEditor.savePreset();}))));
        root.then(ClientCommands.literal("load").then(ClientCommands.argument("name",StringArgumentType.greedyString()).executes(c->run(()->{MapEditor.presetName=StringArgumentType.getString(c,"name");MapEditor.loadPreset(0);})))) ;
        return root;
    }
    public static int run(Runnable action){try{action.run();return 1;}catch(RuntimeException error){MapEditor.notice(error.getMessage()==null?"Map edit failed.":error.getMessage());return 0;}}
}
