package com.customcosmetics.gametest;

import com.customcosmetics.features.map.*;
import com.customcosmetics.compatibility.*;
import com.customcosmetics.gui.*;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.item.*;
import java.util.*;

/** Real chat submission and world-render regression, never connected to a remote server. */
public final class MapSpecialGameTest implements FabricClientGameTest {
    public static boolean observe;
    public static final List<String> packets=new ArrayList<>();
    private static final BlockPos ORIGINAL=new BlockPos(-2,65,0),GHOST=new BlockPos(0,66,1);
    @Override public void runTest(ClientGameTestContext context){
        new MapEditorGameTest().runTest(context);
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("fill -12 63 -12 12 64 12 minecraft:stone");world.getServer().runCommand("fill -12 65 -12 12 80 12 minecraft:air");world.getServer().runCommand("fill 0 65 0 0 69 0 minecraft:stone");world.getServer().runCommand("setblock -2 65 0 minecraft:chest[facing=south]");world.getServer().runCommand("tp @a 0.5 65 5 180 0");context.waitTicks(25);
            world.getServer().runOnServer(server->((ChestBlockEntity)server.getLevel(Level.OVERWORLD).getBlockEntity(ORIGINAL)).setItem(0,new ItemStack(Items.DIAMOND,7)));context.waitTicks(5);
            // Container inventories are normally sent only while open; seed the same known client contents.
            context.runOnClient(client->((ChestBlockEntity)client.level.getBlockEntity(ORIGINAL)).setItem(0,new ItemStack(Items.DIAMOND,7)));
            command(context,"blockgui",MapBlockScreen.class);context.runOnClient(client->ClientUiAccess.screen(client).onClose());context.runOnClient(client->check(ClientUiAccess.screen(client)==null,"Back returned to stale command chat"));
            command(context,"blocks",MapBlockScreen.class);
            context.runOnClient(client->{var screen=ClientUiAccess.screen(client);var search=(net.minecraft.client.gui.components.EditBox)screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance).findFirst().orElseThrow();search.setValue("minecraft:chest");});context.waitTicks(2);
            context.runOnClient(client->{var screen=ClientUiAccess.screen(client);var cell=(net.minecraft.client.gui.components.AbstractWidget)screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.AbstractWidget w&&w.visible&&w.getMessage().getString().equals("Chest")).findFirst().orElseThrow();cell.onClick(new net.minecraft.client.input.MouseButtonEvent(cell.getX()+8,cell.getY()+8,new net.minecraft.client.input.MouseButtonInfo(ClientInput.MOUSE_LEFT,0)),false);check(MapEditor.material.equals("minecraft:chest"),"Palette did not select chest");check(ClientUiAccess.screen(client)==null,"Choosing chest did not return to the world");});
            command(context,"import",FmeImportScreen.class);context.runOnClient(client->ClientUiAccess.screen(client).onClose());
            context.runOnClient(client->{for(String id:List.of("chest","ender_chest","barrel","beacon","player_head","oak_sign","white_banner","water","lava"))check(MapEditor.selectable("minecraft:"+id),"Palette still rejects "+id);check(MapEditor.icon(Blocks.WATER.defaultBlockState()).is(Items.WATER_BUCKET),"Water has no palette icon");MapEditor.enabled=true;MapEditor.editing=true;MapEditor.dungeonOnly=false;MapEditor.tick(client);client.mouseHandler.grabMouse();packets.clear();observe=true;});
            context.getInput().pressMouse(ClientInput.MOUSE_RIGHT);context.waitTicks(5);
            context.runOnClient(client->{observe=false;check(MapEditor.DATA.snapshot().containsKey(MapEditor.position(GHOST)),"Physical right-click failed to place selected chest: "+MapEditor.DATA.snapshot());check(client.level.getBlockState(GHOST).isAir()&&client.level.getBlockEntity(GHOST)==null,"Legit chest entered the world");check(client.level.getBlockState(GHOST).getCollisionShape(client.level,GHOST).isEmpty(),"Legit chest added collision");check(packets.stream().noneMatch(p->p.contains("UseItem")||p.contains("PlayerActionPacket")||p.contains("SwingPacket")),"Chest editing sent interactions: "+packets);
                MapEditor.put(new BlockPos(2,65,0),"minecraft:ender_chest");MapEditor.put(new BlockPos(4,65,0),"minecraft:beacon");MapEditor.put(new BlockPos(6,65,0),"minecraft:water");MapEditor.put(new BlockPos(8,65,0),"minecraft:lava");MapEditor.put(new BlockPos(-4,65,0),"minecraft:player_head");MapEditor.put(ORIGINAL,"minecraft:gold_block");MapEditor.refresh();});context.waitTicks(20);
            context.runOnClient(client->{check(GhostBlocks.submittedSpecialBlocks>=3,"Special block renderers were not submitted: "+GhostBlocks.submittedSpecialBlocks);check(GhostBlocks.submittedFluidGhosts==2,"Fluid ghosts were not rendered");check(GhostBlocks.submittedPaintedEntities==1,"Real chest geometry was not retextured: count="+GhostBlocks.submittedPaintedEntities+", shape="+client.level.getBlockState(ORIGINAL).getRenderShape()+", visible="+MapEditor.visible());check(MapEditor.hideBlockEntity(client.level.getBlockEntity(ORIGINAL)),"Original container renderer was not hidden");contents(client,Items.DIAMOND,7);});capture(context,"map-special-chest-ender-chest-beacon-fluids");
            context.runOnClient(client->{MapEditor.put(ORIGINAL,"minecraft:air");MapEditor.refresh();contents(client,Items.DIAMOND,7);check(MapEditor.hideBlockEntity(client.level.getBlockEntity(ORIGINAL)),"Real chest hide did not suppress its renderer");});context.waitTicks(5);
            context.runOnClient(client->{check(client.level.getBlockState(GHOST).isAir()&&client.level.getBlockEntity(GHOST)==null,"Visual chest entered client world storage");check(client.level.getBlockState(GHOST).getCollisionShape(client.level,GHOST).isEmpty(),"Visual chest acquired collision");contents(client,Items.DIAMOND,7);});
            world.getServer().runOnServer(server->{check(server.getLevel(Level.OVERWORLD).getBlockState(GHOST).isAir(),"Chest was sent to server");((ChestBlockEntity)server.getLevel(Level.OVERWORLD).getBlockEntity(ORIGINAL)).setItem(0,new ItemStack(Items.EMERALD,3));server.getLevel(Level.OVERWORLD).sendBlockUpdated(ORIGINAL,Blocks.CHEST.defaultBlockState(),Blocks.CHEST.defaultBlockState(),3);});context.waitTicks(8);
            context.runOnClient(client->((ChestBlockEntity)client.level.getBlockEntity(ORIGINAL)).setItem(0,new ItemStack(Items.EMERALD,3)));
            context.runOnClient(client->{MapEditor.refresh();check(client.level.getBlockState(GHOST).isAir()&&client.level.getBlockEntity(GHOST)==null,"Visual refresh installed a fake chest");contents(client,Items.EMERALD,3);MapEditor.editing=false;MapEditor.enabled=false;MapEditor.clear();check(!MapEditor.hideBlockEntity(client.level.getBlockEntity(ORIGINAL)),"Clearing edits left the real chest hidden");});
        }
        glass(context);
        System.out.println("MAP_CHAT_BLOCKGUI_BLOCKS_IMPORT_CHEST_PALETTE_SPECIAL_RENDER_FLUIDS_REAL_CONTENTS_NO_PACKETS_OK");
    }
    private static void glass(ClientGameTestContext context){
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("fill -12 63 -12 12 64 12 minecraft:stone");world.getServer().runCommand("fill -12 65 -12 12 80 12 minecraft:air");world.getServer().runCommand("fill -1 65 -1 1 68 1 minecraft:stone");world.getServer().runCommand("setblock 0 66 0 minecraft:gold_block");world.getServer().runCommand("time set noon");world.getServer().runCommand("tp @a 0.5 65 5.5 180 0");context.waitTicks(30);
            context.runOnClient(client->{MapEditor.enabled=true;MapEditor.dungeonOnly=false;MapEditor.tick(client);MapEditor.editing=false;MapEditor.put(GHOST,"minecraft:glass");MapEditor.refresh();check(client.level.getBlockState(GHOST).is(Blocks.STONE),"Glass replaced the authoritative stone");check(client.level.getBlockState(GHOST).getCollisionShape(client.level,GHOST).bounds().getYsize()==1,"Glass changed real collision");check(MapEditor.revealsFace(GHOST),"Glass did not expose adjoining geometry");
                // A completely solid section containing only an edited glass tunnel must stay visually connected.
                for(int x=32;x<48;x++)MapEditor.put(new BlockPos(x,70,8),"minecraft:glass");MapEditor.refresh();var graph=new net.minecraft.client.renderer.chunk.VisGraph();for(int x=32;x<48;x++)for(int y=64;y<80;y++)for(int z=0;z<16;z++)graph.setOpaque(new BlockPos(x,y,z));check(graph.resolve().visibilityBetween(net.minecraft.core.Direction.WEST,net.minecraft.core.Direction.EAST),"Glass tunnel remained sealed in section visibility");
            });context.waitTicks(30);capture(context,"map-glass-neighbor-gold-face");goldPixels("map-glass-neighbor-gold-face");
            context.runOnClient(client->{MapEditor.put(GHOST,"minecraft:air");MapEditor.refresh();});context.waitTicks(25);capture(context,"map-hidden-neighbor-gold-face");goldPixels("map-hidden-neighbor-gold-face");context.runOnClient(client->{MapEditor.clear();MapEditor.enabled=false;check(!MapEditor.revealsFace(GHOST),"Cleared edit left a culling override");});
        }
        System.out.println("MAP_GLASS_HIDDEN_ADJACENT_FACES_PIXELS_SECTION_VISIBILITY_COLLISION_OK");
    }
    private static void goldPixels(String name){try{var image=javax.imageio.ImageIO.read(java.nio.file.Path.of("screenshots/"+name+".png").toFile());int gold=0;for(int y=image.getHeight()*2/5;y<image.getHeight()*3/5;y++)for(int x=image.getWidth()*2/5;x<image.getWidth()*3/5;x++){int c=image.getRGB(x,y),r=c>>16&255,g=c>>8&255,b=c&255;if(r>65&&g>50&&b<g*.7)gold++;}check(gold>100,"Adjoining gold face is missing behind "+name+": gold pixels="+gold);}catch(java.io.IOException error){throw new AssertionError(error);}}
    static void command(ClientGameTestContext context,String suffix,Class<?> expected){
        context.runOnClient(client->{try{var screen=net.minecraft.client.gui.screens.ChatScreen.class.getConstructor(String.class,boolean.class).newInstance("/customcosmetics map "+suffix,false);ClientUiAccess.setScreen(client,screen);}catch(Exception error){throw new AssertionError(error);}});context.getInput().pressKey(ClientInput.ENTER);context.waitTicks(5);context.runOnClient(client->{check(expected.isInstance(ClientUiAccess.screen(client)),suffix+" did not survive chat submission: "+ClientUiAccess.screen(client));System.out.println("Physical chat command opened "+suffix);});
    }
    private static void contents(net.minecraft.client.Minecraft client,Item item,int count){check(client.level.getBlockState(ORIGINAL).is(Blocks.CHEST),"Editing replaced a real container state");var chest=(ChestBlockEntity)client.level.getBlockEntity(ORIGINAL);check(chest!=null&&chest.getItem(0).is(item)&&chest.getItem(0).getCount()==count,"Editing lost authoritative chest contents");}
    static void capture(ClientGameTestContext context,String name){try{var method=MapEditorGameTest.class.getDeclaredMethod("capture",ClientGameTestContext.class,String.class);method.setAccessible(true);method.invoke(null,context,name);}catch(Exception error){throw new AssertionError(error);}}
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}
