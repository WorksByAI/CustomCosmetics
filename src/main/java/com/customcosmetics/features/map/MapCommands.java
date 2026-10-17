// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.*;
public final class MapCommands {
    private MapCommands(){}
    public static LiteralArgumentBuilder<FabricClientCommandSource> command(){
        var root=ClientCommands.literal("map").executes(c->{com.customcosmetics.client.CustomCosmeticsClient.requestGui(com.customcosmetics.gui.CosmeticsScreen.Page.MAP);return 1;});
        root.then(ClientCommands.literal("status").executes(c->run(MapEditor::reportStatus)));
        root.then(ClientCommands.literal("force").executes(c->run(()->{MapEditor.forceDungeon(!MapEditor.forceDungeon);MapEditor.reportStatus();}))
            .then(ClientCommands.literal("on").executes(c->run(()->{MapEditor.forceDungeon(true);MapEditor.reportStatus();})))
            .then(ClientCommands.literal("off").executes(c->run(()->{MapEditor.forceDungeon(false);MapEditor.reportStatus();}))));
        root.then(ClientCommands.literal("freecam").executes(c->run(Freecam::toggle))
            .then(ClientCommands.literal("on").executes(c->run(()->Freecam.setEnabled(true))))
            .then(ClientCommands.literal("off").executes(c->run(()->Freecam.setEnabled(false)))));
        root.then(ClientCommands.literal("edit").executes(c->run(MapEditorUi::toggleEdit)));
        root.then(ClientCommands.literal("import").executes(c->run(MapEditorUi::requestImport)));
        root.then(ClientCommands.literal("presets").executes(c->run(MapEditorUi::requestPresets)));
        root.then(ClientCommands.literal("blocks").executes(c->run(MapEditorUi::requestOpen)));
        root.then(ClientCommands.literal("blockgui").executes(c->run(MapEditorUi::requestOpen)));
        root.then(ClientCommands.literal("replace").executes(c->run(MapEditorUi::requestReplace))
            .then(ClientCommands.argument("from",new MapBlockArgument()).then(ClientCommands.argument("to",new MapBlockArgument())
                .executes(c->run(()->MaterialReplacement.start(StringArgumentType.getString(c,"from"),StringArgumentType.getString(c,"to"),MaterialReplacement.Scope.ROOM)))
                .then(ClientCommands.literal("selection").executes(c->run(()->MaterialReplacement.start(StringArgumentType.getString(c,"from"),StringArgumentType.getString(c,"to"),MaterialReplacement.Scope.SELECTION)))))));
        root.then(ClientCommands.literal("clearroom").executes(c->run(MapEditor::clearRoom)));
        root.then(ClientCommands.literal("guide").executes(c->run(()->MapEditor.notice("Map Editor: right-click places, left-click hides a real block or removes an edit, middle-click picks. Shift+right replaces a hidden block. /customcosmetics map blocks: searchable palette; right-click favorites. Room changes save automatically; /customcosmetics map clearroom resets this room."))));
        root.then(ClientCommands.literal("undo").executes(c->run(MapEditor::undo)));
        root.then(ClientCommands.literal("clear").executes(c->run(MapEditor::clear)));
        root.then(ClientCommands.literal("pos1").executes(c->run(()->MapEditor.mark(false))));
        root.then(ClientCommands.literal("pos2").executes(c->run(()->MapEditor.mark(true))));
        root.then(ClientCommands.literal("fill").executes(c->run(MapEditor::fill)));
        root.then(ClientCommands.literal("anchor").executes(c->run(MapEditor::setAnchor)));
        root.then(ClientCommands.literal("material").then(ClientCommands.argument("block",new MapBlockArgument()).executes(c->run(()->{String id=StringArgumentType.getString(c,"block");if(MapEditor.state(id)==null)throw new IllegalArgumentException("Unknown block");MapEditor.material=id.contains(":")?id:"minecraft:"+id;}))));
        root.then(ClientCommands.literal("save").then(ClientCommands.argument("name",StringArgumentType.greedyString()).executes(c->run(()->{MapEditor.presetName=StringArgumentType.getString(c,"name");MapEditor.savePreset();}))));
        root.then(ClientCommands.literal("load").then(ClientCommands.argument("name",StringArgumentType.greedyString()).executes(c->run(()->{MapEditor.presetName=StringArgumentType.getString(c,"name");MapEditor.loadPresetAuto();})))) ;
        return root;
    }
    public static int run(Runnable action){try{action.run();return 1;}catch(RuntimeException error){MapEditor.notice(error.getMessage()==null?"Map edit failed.":error.getMessage());return 0;}}
}
