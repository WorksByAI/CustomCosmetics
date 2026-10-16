package com.customcosmetics.gametest;
import com.customcosmetics.features.map.*;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.gui.CosmeticsScreen;
import com.customcosmetics.compatibility.ClientUiAccess;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.util.RandomSource;
import java.util.*;

public final class MapEditorGameTest implements FabricClientGameTest {
    private static final BlockPos SLAB=new BlockPos(-1,65,0),GHOST=new BlockPos(1,65,0);
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("fill -12 63 -12 12 64 12 minecraft:stone");world.getServer().runCommand("fill -12 65 -12 12 74 12 minecraft:air");world.getServer().runCommand("setblock -1 65 0 minecraft:stone_slab[type=bottom]");world.getServer().runCommand("time set noon");world.getServer().runCommand("tp @a 0 65 5 180 15");context.waitTicks(25);
            context.runOnClient(client->{MapEditor.enabled=true;MapEditor.dungeonOnly=false;MapEditor.mode=MapEdits.Mode.LEGIT;MapEditor.tick(client);MapEditor.put(SLAB,"minecraft:gold_block");MapEditor.put(GHOST,"minecraft:emerald_block");MapEditor.refresh();
                var real=client.level.getBlockState(SLAB);check(real.is(Blocks.STONE_SLAB),"Legit changed real slab state");check(real.getCollisionShape(client.level,SLAB).bounds().getYsize()==.5,"Legit changed slab collision");check(client.level.getBlockState(GHOST).isAir(),"Legit wrote a ghost into the world");
                var model=client.getModelManager().getBlockStateModelSet().get(real);var original=new ArrayList<BlockStateModelPart>();var changed=new ArrayList<BlockStateModelPart>();model.collectParts(RandomSource.create(1),original);MapEditor.wrap(model,SLAB).collectParts(RandomSource.create(1),changed);
                check(original.size()==changed.size(),"Retexture changed model parts");for(int part=0;part<original.size();part++)for(var direction:Direction.values()){var a=original.get(part).getQuads(direction);var b=changed.get(part).getQuads(direction);check(a.size()==b.size(),"Retexture changed quad count");for(int i=0;i<a.size();i++){for(int vertex=0;vertex<4;vertex++)check(a.get(i).position(vertex).equals(b.get(i).position(vertex)),"Retexture changed slab vertices");check(!a.get(i).materialInfo().sprite().equals(b.get(i).materialInfo().sprite()),"Retexture kept the old texture");}}
            });context.waitTicks(20);context.runOnClient(client->check(GhostBlocks.submittedGhosts==1,"Visual air block was not submitted"));capture(context,"map-legit-gold-slab-visual-emerald");verifyGoldPixels();
            world.getServer().runOnServer(server->{check(server.getLevel(Level.OVERWORLD).getBlockState(SLAB).is(Blocks.STONE_SLAB),"Server slab was modified");check(server.getLevel(Level.OVERWORLD).getBlockState(GHOST).isAir(),"Server received a fake block");});
            world.getServer().runCommand("setblock -1 65 0 minecraft:air");context.waitTicks(10);context.runOnClient(client->{check(client.level.getBlockState(SLAB).isAir(),"Mined server block did not become air");check(GhostBlocks.submittedGhosts==1,"Mined real block reappeared as a visual ghost");MapEditor.mode=MapEdits.Mode.CHEATING;MapEditor.refresh();});context.waitTicks(5);
            context.runOnClient(client->{check(client.level.getBlockState(GHOST).is(Blocks.EMERALD_BLOCK),"Cheating mode did not create client collision");check(!client.level.getBlockState(GHOST).getCollisionShape(client.level,GHOST).isEmpty(),"Cheating ghost has no collision");});
            world.getServer().runOnServer(server->check(server.getLevel(Level.OVERWORLD).getBlockState(GHOST).isAir(),"Cheating edit reached the server"));
            world.getServer().runCommand("setblock 1 65 0 minecraft:diamond_block");context.waitTicks(5);context.runOnClient(client->{MapEditor.mode=MapEdits.Mode.LEGIT;MapEditor.refresh();check(client.level.getBlockState(GHOST).is(Blocks.DIAMOND_BLOCK),"Disabling cheating restored stale air over a newer server update");MapEditor.presetName="Native test";MapEditor.savePreset();MapEditor.clear();MapEditor.loadPreset(1);check(MapEditor.DATA.snapshot().size()==2,"Preset save/load lost edits");ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.MAP));});context.waitTicks(5);capture(context,"map-editor-settings");
            context.runOnClient(client->{MapEditor.enabled=false;MapEditor.editing=false;MapEditor.clear();MapEditor.deletePreset();ClientUiAccess.setScreen(client,null);});
            System.out.println("MAP_LEGIT_MATERIAL_GEOMETRY_COLLISION_SERVER_UPDATES_GHOSTS_CHEATING_RESTORE_PRESETS_GUI_OK");
        }
    }
    private static void check(boolean valid,String error){if(!valid)throw new AssertionError(error);}
    private static void verifyGoldPixels(){try{var image=javax.imageio.ImageIO.read(java.nio.file.Path.of("screenshots/map-legit-gold-slab-visual-emerald.png").toFile());int gold=0;for(int y=image.getHeight()/5;y<image.getHeight()*7/10;y++)for(int x=image.getWidth()/5;x<image.getWidth()/2;x++){int rgb=image.getRGB(x,y),r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;if(r>100&&g>80&&b<g*.7)gold++;}check(gold>40,"Real terrain did not display the replacement gold texture (gold pixels="+gold+")");}catch(java.io.IOException error){throw new AssertionError(error);}}
    private static void capture(ClientGameTestContext context,String name){
        var done=new java.util.concurrent.atomic.AtomicBoolean();context.runOnClient(client->{com.mojang.blaze3d.pipeline.RenderTarget target=null;for(Object owner:new Object[]{client.gameRenderer,client}){for(var method:owner.getClass().getMethods())if(method.getParameterCount()==0&&method.getReturnType()==com.mojang.blaze3d.pipeline.RenderTarget.class){try{target=(com.mojang.blaze3d.pipeline.RenderTarget)method.invoke(owner);break;}catch(Exception e){throw new AssertionError(e);}}if(target!=null)break;}if(target==null)throw new AssertionError("No render target");net.minecraft.client.Screenshot.grab(client.gameDirectory,name+".png",target,1,message->done.set(true));});context.waitFor(client->done.get());
    }
}
