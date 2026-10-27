package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.*;
import com.customcosmetics.features.map.*;
import com.customcosmetics.gui.*;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

public final class MapReplacementGameTest implements FabricClientGameTest {
    private static final BlockPos FIRST=new BlockPos(0,66,0),SECOND=new BlockPos(1,66,0),FAR=new BlockPos(20,66,0),STAIR=new BlockPos(-2,65,0),GHOST=new BlockPos(6,66,0);
    private Map<MapEdits.Position,MapEdits.Edit> before;
    @Override public void runTest(ClientGameTestContext context){
        new MapSpecialGameTest().runTest(context);
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("fill -12 63 -12 24 64 12 minecraft:stone");world.getServer().runCommand("fill -12 65 -12 24 80 12 minecraft:air");
            for(var p:List.of(FIRST,SECOND,FAR))world.getServer().runCommand("setblock "+p.getX()+" "+p.getY()+" "+p.getZ()+" minecraft:quartz_block");
            world.getServer().runCommand("setblock -2 65 0 minecraft:quartz_stairs[facing=east,half=top]");world.getServer().runCommand("setblock 3 66 0 minecraft:quartz_pillar[axis=x]");world.getServer().runCommand("setblock 4 66 0 minecraft:quartz_pillar[axis=z]");world.getServer().runCommand("time set noon");world.getServer().runCommand("tp @a 0.5 65 5.5 180 0");
            world.getServer().runOnServer(server->{var board=server.getScoreboard();var objective=board.addObjective("replace",net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,net.minecraft.network.chat.Component.literal("SKYBLOCK"),net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,false,null);board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("The Catacombs (F7)"),objective).set(1);board.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR,objective);});context.waitTicks(30);
            context.runOnClient(client->{MapEditor.enabled=true;MapEditor.dungeonOnly=true;MapEditor.tick(client);MapEditor.clear();MapEditor.put(SECOND,"minecraft:diamond_block");MapEditor.put(GHOST,"minecraft:quartz_block");MapEditor.refresh();MapEditor.material="minecraft:gold_block";before=MapEditor.DATA.snapshot();check(MapEditor.currentRoom()!=null,"No F7 boss frame");});
            MapSpecialGameTest.command(context,"replace",MapReplaceScreen.class);
            context.runOnClient(client->fields().getFirst().setValue("minecraft:stone"));click(context,"Choose Original Block");choose(context,"minecraft:quartz_block");
            context.runOnClient(client->{check(ClientUiAccess.screen(client) instanceof MapReplaceScreen,"Source picker did not return to replacement screen");check(fields().getFirst().getValue().equals("minecraft:quartz_block"),"Source picker failed");check(MapEditor.material.equals("minecraft:gold_block"),"Source picker changed editor placement material");});
            click(context,"Choose Replacement");choose(context,"minecraft:dark_oak_planks");MapSpecialGameTest.capture(context,"map-replace-materials-gui");
            context.runOnClient(client->{MapSpecialGameTest.packets.clear();MapSpecialGameTest.observe=true;});click(context,"Replace");finished(context);
            context.runOnClient(client->{MapSpecialGameTest.observe=false;for(var p:List.of(FIRST,SECOND,FAR)){var edit=MapEditor.DATA.snapshot().get(MapEditor.position(p));check(edit!=null&&edit.material().equals("minecraft:dark_oak_planks")&&!edit.originallyAir(),"Room replacement lost original matching/origin: "+p);check(client.level.getBlockState(p).is(Blocks.QUARTZ_BLOCK),"Room replacement changed world state");}
                check(!MapEditor.DATA.snapshot().containsKey(MapEditor.position(STAIR)),"Quartz stairs were incorrectly matched as quartz blocks");check(MapEditor.DATA.snapshot().get(MapEditor.position(GHOST)).equals(before.get(MapEditor.position(GHOST))),"Visual-only quartz ghost was incorrectly matched as a real quartz block");check(MapEditor.material.equals("minecraft:gold_block"),"Target picker changed editor placement material");check(MapSpecialGameTest.packets.stream().noneMatch(p->p.contains("UseItem")||p.contains("PlayerActionPacket")||p.contains("SwingPacket")),"Replacement sent interactions: "+MapSpecialGameTest.packets);
                var frame=MapEditor.currentRoom();check(MapEditor.ROOMS.edits(frame.key()).get(frame.relative(MapEditor.position(FIRST))).equals("minecraft:dark_oak_planks"),"Batch was not saved by room");check(Boolean.FALSE.equals(MapEditor.ROOMS.originallyAir(frame.key(),frame.relative(MapEditor.position(FIRST)))),"Saved batch lost the real-block origin");
            });
            click(context,"Undo Last Edit");context.runOnClient(client->{check(MapEditor.DATA.snapshot().equals(before),"One Undo failed to restore every previous edit");check(!MapEditor.ROOMS.edits(MapEditor.currentRoom().key()).containsKey(MapEditor.position(FIRST)),"Undo did not remove new room edits");ClientUiAccess.screen(client).onClose();});
            command(context,"pos1");world.getServer().runCommand("tp @a 1.5 65 5.5 180 0");context.waitTicks(4);command(context,"pos2");
            context.runOnClient(client->{check(MapEditor.selection()!=null&&MapEditor.selection().min().x()==0&&MapEditor.selection().max().x()==1,"Selection corners did not use actual block picks: "+MapEditor.selection());});
            MapSpecialGameTest.command(context,"replace",MapReplaceScreen.class);context.runOnClient(client->{fields().get(0).setValue("minecraft:quartz_block");fields().get(1).setValue("minecraft:dark_oak_planks");});click(context,"Scope: Current Room");click(context,"Replace");finished(context);
            context.runOnClient(client->{check(MapEditor.DATA.snapshot().get(MapEditor.position(FIRST)).material().equals("minecraft:dark_oak_planks"),"Selection missed inclusive first corner");check(MapEditor.DATA.snapshot().get(MapEditor.position(SECOND)).material().equals("minecraft:dark_oak_planks"),"Selection missed inclusive second corner");check(!MapEditor.DATA.snapshot().containsKey(MapEditor.position(FAR)),"Selection modified a block outside its bounds");ClientUiAccess.screen(client).onClose();});
            command(context,"replace minecraft:quartz_block minecraft:stone selection");finished(context);context.runOnClient(client->{check(MapEditor.DATA.snapshot().get(MapEditor.position(FIRST)).material().equals("minecraft:stone"),"Selection command did not parse namespaced materials");check(!MapEditor.DATA.snapshot().containsKey(MapEditor.position(FAR)),"Selection command escaped the selected region");MapEditor.undo();});
            world.getServer().runCommand("setblock 0 66 0 minecraft:air");context.waitTicks(5);context.runOnClient(client->{check(client.level.getBlockState(FIRST).isAir(),"Temporary server mining was hidden");check(!MapEditor.DATA.snapshot().get(MapEditor.position(FIRST)).ghost(true),"Mined replacement turned into a ghost");});world.getServer().runCommand("setblock 0 66 0 minecraft:quartz_block");context.waitTicks(5);
            context.runOnClient(client->{check(MapEditor.wrap(client.getModelManager().getBlockStateModelSet().get(client.level.getBlockState(FIRST)),FIRST) instanceof MaterialModel,"Restored block lost its batch material");});command(context,"replace minecraft:quartz_pillar[axis=x] minecraft:dark_oak_planks");finished(context);
            context.runOnClient(client->{for(int x:List.of(3,4))check(MapEditor.DATA.snapshot().get(new MapEdits.Position(x,66,0)).material().equals("minecraft:dark_oak_planks"),"Pillar orientation affected block-type matching");});
            command(context,"replace quartz_stairs dark_oak_planks");finished(context);
            context.runOnClient(client->{var state=client.level.getBlockState(STAIR);check(state.is(Blocks.QUARTZ_STAIRS)&&state.getValue(net.minecraft.world.level.block.StairBlock.HALF)==net.minecraft.world.level.block.state.properties.Half.TOP,"Replacing stair material changed its real shape/state");check(MapEditor.DATA.snapshot().get(MapEditor.position(STAIR)).material().equals("minecraft:dark_oak_planks"),"Stair material was not replaced");var snapshot=MapEditor.DATA.snapshot();MaterialReplacement.start("minecraft:quartz_block","minecraft:emerald_block",MaterialReplacement.Scope.ROOM);MapEditor.put(new BlockPos(10,66,0),"minecraft:gold_block");before=MapEditor.DATA.snapshot();check(before.size()==snapshot.size()+1,"Interference fixture failed");});context.waitTicks(5);
            context.runOnClient(client->{check(!MaterialReplacement.busy()&&MaterialReplacement.status().contains("edits changed"),"Concurrent edits did not cancel batch");check(MapEditor.DATA.snapshot().equals(before),"Cancelled batch partially changed editor data");MaterialReplacement.start("minecraft:quartz_block","minecraft:emerald_block",MaterialReplacement.Scope.ROOM);MaterialReplacement.cancel();check(MapEditor.DATA.snapshot().equals(before),"Explicit cancel changed data");MapEditor.clear();MapEditor.enabled=false;MapEditor.dungeonOnly=true;});
            world.getServer().runOnServer(server->check(server.getLevel(Level.OVERWORLD).getBlockState(FAR).is(Blocks.QUARTZ_BLOCK),"Server received a replacement block"));
            world.getServer().runCommand("fill 0 90 0 31 105 31 minecraft:quartz_block");context.waitTicks(15);
            context.runOnClient(client->{MapEditor.enabled=true;MapEditor.clear();MaterialReplacement.start("minecraft:quartz_block","minecraft:dark_oak_planks",MaterialReplacement.Scope.ROOM);});finished(context);
            context.runOnClient(client->{
                int count=MapEditor.DATA.snapshot().size();check(count>=16384,"Large replacement stopped at the former active edit cap: "+count);
                check(MapEditor.DATA.snapshot().get(new MapEdits.Position(31,105,31)).material().equals("minecraft:dark_oak_planks"),"Large replacement missed its last section");
                MapEditor.flushRooms();try{var saved=new RoomLayouts();saved.load(MapEditor.roomsPath());check(saved.edits("floor:F7").size()>=16384,"Large batch was not saved completely");}catch(java.io.IOException e){throw new AssertionError(e);}
                MapEditor.undo();check(MapEditor.DATA.snapshot().isEmpty(),"Large replacement did not undo atomically");MapEditor.enabled=false;
            });
            world.getServer().runOnServer(server->check(server.getLevel(Level.OVERWORLD).getBlockState(new BlockPos(31,105,31)).is(Blocks.QUARTZ_BLOCK),"Large replacement changed authoritative blocks"));
            System.out.println("MAP_REPLACEMENT_16384_PLUS_BLOCKS_SAVE_UNDO_SERVER_UNCHANGED_OK");
        }
        System.out.println("MAP_REPLACEMENT_CHAT_GUI_PICKERS_ROOM_SELECTION_ATOMIC_UNDO_PERSISTENCE_RESTORE_ORIENTATIONS_CANCEL_NO_PACKETS_OK");
    }
    private static List<EditBox> fields(){return ClientUiAccess.screen(net.minecraft.client.Minecraft.getInstance()).children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();}
    private static void choose(ClientGameTestContext context,String id){context.runOnClient(client->fields().getFirst().setValue(id));context.waitTicks(2);context.runOnClient(client->{var cell=ClientUiAccess.screen(client).children().stream().filter(c->c instanceof AbstractWidget w&&w.visible&&w.getClass().getSimpleName().equals("Cell")).map(AbstractWidget.class::cast).findFirst().orElseThrow();cell.onClick(event(cell),false);});context.waitTicks(2);}
    private static void click(ClientGameTestContext context,String label){context.runOnClient(client->{var button=ClientUiAccess.screen(client).children().stream().filter(c->c instanceof AbstractWidget w&&w.getMessage().getString().equals(label)).map(AbstractWidget.class::cast).findFirst().orElseThrow(()->new AssertionError("Missing button: "+label));check(button.active,"Inactive button: "+label);button.onClick(event(button),false);});context.waitTicks(1);}
    private static net.minecraft.client.input.MouseButtonEvent event(AbstractWidget widget){return new net.minecraft.client.input.MouseButtonEvent(widget.getX()+4,widget.getY()+4,new net.minecraft.client.input.MouseButtonInfo(ClientInput.MOUSE_LEFT,0));}
    private static void finished(ClientGameTestContext context){context.waitFor(client->!MaterialReplacement.busy());context.waitTicks(2);context.runOnClient(client->check(MaterialReplacement.status().startsWith("Replaced"),"Replacement failed: "+MaterialReplacement.status()));}
    private static void command(ClientGameTestContext context,String suffix){context.runOnClient(client->{try{ClientUiAccess.setScreen(client,net.minecraft.client.gui.screens.ChatScreen.class.getConstructor(String.class,boolean.class).newInstance("/customcosmetics map "+suffix,false));}catch(Exception e){throw new AssertionError(e);}});context.getInput().pressKey(ClientInput.ENTER);context.waitTicks(3);}
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}
