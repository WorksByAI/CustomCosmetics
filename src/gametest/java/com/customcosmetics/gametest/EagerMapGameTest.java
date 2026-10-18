package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.chat.CommandTooltips;
import com.customcosmetics.features.map.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.*;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Distant loaded rooms and unloaded fixed bosses must not require walking into them. */
public final class EagerMapGameTest implements FabricClientGameTest {
    public static Style hovered;
    private static final MapEdits.Position BOSS=new MapEdits.Position(1024,0,1024),GHOST=new MapEdits.Position(1024,65,1024),LEGACY=new MapEdits.Position(1104,66,1024),ROOM=new MapEdits.Position(-89,85,-89);
    private static final int COUNT=64*64*64;
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{client.options.guiScale().set(2);client.options.renderDistance().set(8);ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;MapEditor.enabled=false;MapEditor.forceDungeon=false;MapEditor.forceKuudra=false;});
        try(var world=context.worldBuilder().create()){
            world.getServer().runOnServer(s->s.getPlayerList().setViewDistance(8));world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("forceload add -208 -208 -1 -1");
            world.getServer().runCommand("tp @a -184.5 65 -184.5 0 0");world.getServer().runCommand("fill -89 60 -89 -89 90 -89 minecraft:amethyst_block");
            world.getServer().runOnServer(s->{
                var board=s.getScoreboard();var objective=board.addObjective("eager",net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,Component.literal("SKYBLOCK"),net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,false,null);
                var team=board.addPlayerTeam("eager_location");team.setPlayerPrefix(Component.literal("⏣ The Catacombs (M7)"));board.addPlayerToTeam("§u",team);
                board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("§u"),objective).set(1);board.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR,objective);
            });
            context.waitTicks(30);context.waitFor(client->MapEditor.loaded(MapEditor.blockPos(ROOM)));
            context.runOnClient(client->{
                String best=null;int rotation=0;for(int i=0;i<4;i++){String hash=RoomFingerprint.compute(new MapEdits.Position(-89,0,-89),i,p->net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(MapEditor.blockPos(p)).getBlock()).toString());if(best==null||hash.compareTo(best)<0){best=hash;rotation=i;}}
                var roomFrame=new RoomLayouts.Frame("fingerprint:"+best,"Distant room",new MapEdits.Position(-89,0,-89),rotation);
                var edits=new HashMap<MapEdits.Position,String>();for(int x=1024;x<1088;x++)for(int y=0;y<64;y++)for(int z=1024;z<1088;z++)edits.put(new MapEdits.Position(x,y,z),"minecraft:gold_block");
                edits.put(GHOST,"minecraft:diamond_block");edits.put(LEGACY,"minecraft:emerald_block");
                MapEditor.ROOMS.importLayouts(Map.of("floor:M7",new FmeImport.Layout(edits,null),roomFrame.key(),new FmeImport.Layout(Map.of(roomFrame.relative(ROOM),"minecraft:gold_block"),null)),true);
                var flags=new HashMap<MapEdits.Position,Boolean>();for(var p:edits.keySet())if(!p.equals(LEGACY))flags.put(p,p.equals(GHOST));MapEditor.ROOMS.rememberOrigins("floor:M7",flags);MapEditor.ROOMS.rememberOrigin(roomFrame.key(),roomFrame.relative(ROOM),false);
                check(!MapEditor.loaded(MapEditor.blockPos(BOSS)),"Boss fixture is already loaded");
                MapEditor.autoRooms=true;MapEditor.enabled=true;MapEditor.dungeonOnly=true;MapEditor.tick(client);
                check(DungeonRooms.frames().stream().anyMatch(f->f.key().equals("floor:F7+M7")),"Boss frame was not requested from dungeon entry: "+DungeonSidebarReader.read(client,true));
            });
            context.waitFor(client->MapEditor.visible().containsKey(BOSS)&&MapEditor.visible().containsKey(ROOM)&&MapEditor.visible().size()>=COUNT+2);
            context.runOnClient(client->{
                check(client.player.getX()< -180,"Test walked into a room to trigger recognition");
                check(!MapEditor.loaded(MapEditor.blockPos(BOSS)),"Preloading requested unloaded chunks");
                check(!MapEditor.visible().get(BOSS).originallyAir()&&MapEditor.visible().get(GHOST).originallyAir(),"Known real/ghost flags changed");
                check(!MapEditor.visible().containsKey(LEGACY),"Unknown unloaded block was guessed to be air");
                check(client.level.getBlockState(MapEditor.blockPos(ROOM)).is(net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK),"Room replacement changed real blocks");
                long worst=0;for(int i=0;i<30;i++){long began=System.nanoTime();MapEditor.tick(client);worst=Math.max(worst,System.nanoTime()-began);}check(worst<150_000_000,"Prepared map tick stalled: "+worst/1_000_000+"ms");
                System.out.println("EAGER_262144_BOSS_EDITS_UNLOADED_DISTANT_ROOM_BEFORE_VISIT_NO_CHUNK_REQUEST_MAX_TICK_MS="+worst/1_000_000);
            });
            world.getServer().runCommand("forceload add 1024 1024 1119 1039");world.getServer().runCommand("setblock 1104 66 1024 minecraft:stone");world.getServer().runCommand("tp @a 1030 70 1028 0 0");
            context.waitFor(client->MapEditor.loaded(MapEditor.blockPos(LEGACY))&&MapEditor.visible().containsKey(LEGACY));
            context.runOnClient(client->{check(!MapEditor.visible().get(LEGACY).originallyAir(),"Deferred legacy edit guessed unloaded air");check(MapEditor.visible().containsKey(BOSS),"Entering boss lost the preloaded edits");MapEditor.enabled=false;MapEditor.autoRooms=false;});
            checkTooltip(context);
        }
        System.out.println("EAGER_DUNGEON_BOSS_AND_LOADED_ROOM_PREPARATION_COMMAND_TOOLTIP_NATIVE_OK");
    }
    private static void checkTooltip(ClientGameTestContext context){
        context.runOnClient(client->{var item=Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/help")).withHoverEvent(new HoverEvent.ShowItem(new net.minecraft.world.item.ItemStackTemplate(net.minecraft.world.item.Items.STONE)));check(CommandTooltips.enrich(item)==item,"Item tooltip was replaced");});
        context.getInput().resizeWindow(1280,720);
        context.runOnClient(client->{ConfigManager.commandTooltipsEnabled=true;hovered=null;ClientUiAccess.setScreen(client,new net.minecraft.client.gui.screens.Screen(Component.literal("Command Tooltip Test")){
            @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g,int mx,int my,float dt){
                g.fill(0,0,width,height,0xFF202028);
                var text=Component.literal("[CLICK HERE]").withStyle(s->s.withColor(0xFFA6CF).withClickEvent(new ClickEvent.RunCommand("/party accept NativeMate")).withHoverEvent(new HoverEvent.ShowText(Component.literal("Original tooltip"))));
                g.textRenderer(net.minecraft.client.gui.GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR).accept(50,50,text);super.extractRenderState(g,mx,my,dt);
            }
        });});
        context.getInput().setCursorPos(110,106);context.waitTicks(5);
        context.runOnClient(client->{check(hovered!=null&&((HoverEvent.ShowText)hovered.getHoverEvent()).value().getString().equals("Original tooltip\nClick to execute command: /party accept NativeMate"),"Native tooltip did not append the real command");});
        context.takeScreenshot("command-tooltip-original-and-real-command");
        context.runOnClient(client->ConfigManager.commandTooltipsEnabled=false);context.waitTicks(3);
        context.runOnClient(client->{check(((HoverEvent.ShowText)hovered.getHoverEvent()).value().getString().equals("Original tooltip"),"Tooltip opt-out did not restore the original");ConfigManager.commandTooltipsEnabled=true;ClientUiAccess.setScreen(client,null);});
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
