package com.customcosmetics.gametest;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.map.*;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Physical mouse/keyboard regression, including the independent Freecam mod's suppressed sneak input. */
public final class MapAdventureGameTest implements FabricClientGameTest {
    public static boolean remoteWorld;
    private static final BlockPos COLUMN=new BlockPos(0,66,0);
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.offlineMode=true;MapEditor.forceDungeon=false;ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("fill -12 63 -12 12 64 12 minecraft:stone");
            world.getServer().runCommand("fill -12 65 -12 12 82 12 minecraft:air");
            world.getServer().runCommand("fill 0 65 0 0 80 0 minecraft:stone");
            world.getServer().runCommand("gamemode adventure @a");
            world.getServer().runCommand("tp @a 0.5 65 4 180 0");
            world.getServer().runOnServer(server->sidebar(server,false,true));context.waitTicks(25);
            context.runOnClient(client->{check(client.gameMode.getPlayerMode()==GameType.ADVENTURE&&!client.player.getAbilities().mayBuild,"Test player can build normally");remoteWorld=true;MapEditor.dungeonOnly=true;MapEditor.enabled=true;MapEditor.tick(client);MapEditor.editing=true;MapEditor.material="minecraft:gold_block";
                check(!com.customcosmetics.features.animations.PresenceWorld.isHypixel(client),"Test unexpectedly has Hypixel API/hostname detection");check(MapEditor.inDungeon(client)&&MapEditor.allowed(client),"Actual F7 sidebar failed without hostname/API detection");});
            click(context,"Adventure player");
            world.getServer().runOnServer(server->{var board=server.getScoreboard();var objective=board.getObjective("cc_map_main");board.resetSinglePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("cc_map_main_location"),objective);board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("#cc_map_main_location"),objective).set(4);var team=board.addPlayerTeam("cc_legacy_label");team.setPlayerPrefix(net.minecraft.network.chat.Component.literal("§c \ue067 The Cata"));team.setPlayerSuffix(net.minecraft.network.chat.Component.literal("combs §7(F7)"));board.addPlayerToTeam("#cc_map_main_location",team);});context.waitTicks(5);
            context.runOnClient(client->{MapEditor.clear();check(MapEditor.automaticDungeon(client),"Legacy team prefix/suffix label was rejected");check(MapEditor.floor().equals("F7"),"Legacy team label lost the floor");});click(context,"Legacy prefix/suffix sidebar");
            world.getServer().runOnServer(MapAdventureGameTest::capturedF7Sidebar);context.waitTicks(5);
            context.runOnClient(client->{MapEditor.clear();var board=client.level.getScoreboard();var team=board.getPlayersTeam("§u");var broken=net.minecraft.world.scores.PlayerTeam.formatNameForTeam(team,net.minecraft.network.chat.Component.literal("§u")).getString();check(!DungeonSidebar.matches("SKYBLOCK",java.util.List.of(broken)),"Captured F7 fixture no longer reproduces the beta5 failure");check(!MapEditor.forceDungeon&&MapEditor.automaticDungeon(client)&&MapEditor.floor().equals("F7"),"Captured live F7 sidebar was not recognized automatically: "+DungeonSidebarReader.read(client,true));});click(context,"Captured live F7 sidebar (Force OFF)");
            world.getServer().runOnServer(MapAdventureGameTest::capturedM7Sidebar);context.waitTicks(5);
            context.runOnClient(client->{MapEditor.clear();check(!MapEditor.forceDungeon&&MapEditor.automaticDungeon(client)&&MapEditor.floor().equals("M7"),"Legacy M7 sidebar was not recognized with Force OFF");});click(context,"Legacy M7 sidebar (Force OFF)");
            world.getServer().runCommand("tp @a 0.5 65 10 180 0");context.waitTicks(8);
            context.runOnClient(client->{MapEditor.clear();Freecam.setEnabled(true);Freecam.camera().snapTo(new Vec3(.5,71,4),180,0);Freecam.camera().setOldPosAndRot();});
            click(context,"Adventure built-in Freecam");
            context.runOnClient(client->Freecam.setEnabled(false));
            if(FabricLoader.getInstance().isModLoaded("freecam")){
                context.runOnClient(client->{MapEditor.clear();external("toggle");check(client.getCameraEntity()!=client.player,"Independent Freecam did not detach");var camera=client.getCameraEntity();camera.snapTo(new Vec3(.5,71,4),180,0);camera.setOldPosAndRot();});
                click(context,"Adventure independent Freecam");
                context.runOnClient(client->external("toggle"));
            }
            world.getServer().runOnServer(server->{sidebar(server,false,false);sidebar(server,true,true);});world.getServer().runCommand("tp @a 0.5 65 4 180 0");context.waitTicks(8);
            context.runOnClient(client->{MapEditor.clear();check(MapEditor.inDungeon(client)&&MapEditor.allowed(client),"Team-colored M7 sidebar not recognized");});click(context,"Team-colored M7 sidebar");
            world.getServer().runOnServer(server->sidebar(server,true,false));context.waitTicks(8);
            context.runOnClient(client->{MapEditor.clear();check(!MapEditor.inDungeon(client)&&!MapEditor.allowed(client),"Dungeon Hub incorrectly recognized as a dungeon");FreecamGameTest.packets.clear();FreecamGameTest.observe=true;});
            int right=mouse("MOUSE_BUTTON_RIGHT"),left=mouse("MOUSE_BUTTON_LEFT");context.getInput().pressMouse(right);context.getInput().pressMouse(left);context.waitTicks(3);
            context.runOnClient(client->{FreecamGameTest.observe=false;check(MapEditor.DATA.snapshot().isEmpty(),"Blocked area received map edits");noInteractions("Blocked Dungeon Only input");MapEditor.dungeonOnly=false;});click(context,"Dungeon Only disabled outside the dungeon");
            world.getServer().runOnServer(server->{var level=server.getLevel(Level.OVERWORLD);check(level.getBlockState(COLUMN).is(Blocks.STONE),"Real column changed on server");for(int y=65;y<=81;y++)for(int z=1;z<=4;z++)check(level.getBlockState(new BlockPos(0,y,z)).isAir(),"Editor placed a server block");check(server.getPlayerList().getPlayers().getFirst().gameMode.getGameModeForPlayer()==GameType.ADVENTURE,"Editor changed server game mode");});
            context.runOnClient(client->{remoteWorld=false;MapEditor.clear();MapEditor.editing=false;MapEditor.enabled=false;});
        }
        System.out.println("MAP_ADVENTURE_PHYSICAL_SHIFT_MOUSE_LOCAL_GHOSTS_CAMERA_INPUT_NO_INTERACTION_PACKETS_OK");
    }
    private static void click(ClientGameTestContext context,String label){
        context.runOnClient(client->{FreecamGameTest.packets.clear();FreecamGameTest.observe=true;});
        context.getInput().holdShift();
        context.runOnClient(client->{check(client.hasShiftDown(),label+": physical Shift not held");System.out.println(label+" before right-click: body sneak="+client.player.isShiftKeyDown());});
        int right=mouse("MOUSE_BUTTON_RIGHT");
        context.getInput().pressMouse(right);context.getInput().releaseShift();context.waitTicks(3);
        context.runOnClient(client->{FreecamGameTest.observe=false;var edits=MapEditor.DATA.snapshot();check(!edits.isEmpty(),label+": Shift+right created no edit");
            for(var edit:edits.values()){check(edit.originallyAir()&&edit.position().z()>0,label+": Shift+right painted the original block instead of placing adjacent: "+edits);var pos=MapEditor.blockPos(edit.position());check(client.level.getBlockState(pos).isAir(),label+": Legit edit wrote real state");check(client.level.getBlockState(pos).getCollisionShape(client.level,pos).isEmpty(),label+": Legit edit added collision");}
            noInteractions(label);
            System.out.println(label+" physical placement OK; edits="+edits.size());
        });
    }
    private static void noInteractions(String label){check(FreecamGameTest.packets.stream().noneMatch(p->p.contains("UseItem")||p.contains("PlayerActionPacket")||p.contains("InteractPacket")||p.contains("SwingPacket")),label+": sent interaction packets "+FreecamGameTest.packets);}
    private static int mouse(String name){try{return com.mojang.blaze3d.platform.InputConstants.class.getField(name).getInt(null);}catch(Exception error){throw new AssertionError(error);}}
    /** Exact owner/prefix/suffix fields captured read-only from the user's Hypixel F7 client. */
    static void capturedF7Sidebar(net.minecraft.server.MinecraftServer server){
        capturedSidebar(server,"F7");
    }
    static void capturedM7Sidebar(net.minecraft.server.MinecraftServer server){capturedSidebar(server,"M7");}
    private static void capturedSidebar(net.minecraft.server.MinecraftServer server,String floor){
        var board=server.getScoreboard();var objective=board.getObjective("captured_f7");if(objective==null)objective=board.addObjective("captured_f7",net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,net.minecraft.network.chat.Component.literal("SKYBLOCK"),net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,false,null);
        var rows=java.util.List.of(new String[]{"§u"," §7\ue067 §cThe Catac","§combs §7("+floor+")"},new String[]{"§g","§ewww.hypixel.ne","§et"},new String[]{"§q","Time Elapsed: §a","§a06s"},new String[]{"§p","Cleared: §c0% §8","§8(0)"});
        for(int i=0;i<rows.size();i++){var row=rows.get(i);var team=board.getPlayerTeam("cc_captured_"+i);if(team==null)team=board.addPlayerTeam("cc_captured_"+i);team.setPlayerPrefix(net.minecraft.network.chat.Component.literal(row[1]));team.setPlayerSuffix(net.minecraft.network.chat.Component.literal(row[2]));board.addPlayerToTeam(row[0],team);board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(row[0]),objective).set(i+1);}
        board.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR,objective);
    }
    private static void sidebar(net.minecraft.server.MinecraftServer server,boolean colored,boolean dungeon){
        var board=server.getScoreboard();String id=colored?"cc_map_team":"cc_map_main";var objective=board.getObjective(id);
        if(objective==null)objective=board.addObjective(id,net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,net.minecraft.network.chat.Component.literal("SKYBLOCK"),net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,false,null);
        var row=board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(id+"_location"),objective);row.set(4);row.display(net.minecraft.network.chat.Component.literal(dungeon?(colored?"§c⏣ The C§6a§et§aa§bc§9o§dm§cb§as (M7)":"⏣ The Catacombs (F7)"):"⏣ Dungeon Hub"));
        for(var pair:java.util.List.of(new String[]{"time","Time Elapsed: 28m 43s"},new String[]{"cleared","Cleared: 39% (71)"},new String[]{"brand","www.hypixel.net"})){var score=board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(id+pair[0]),objective);score.set(1);score.display(net.minecraft.network.chat.Component.literal(pair[1]));}
        board.setDisplayObjective(colored?net.minecraft.world.scores.DisplaySlot.TEAM_RED:net.minecraft.world.scores.DisplaySlot.SIDEBAR,objective);
        if(colored){var team=board.getPlayerTeam("cc_map_team");if(team==null)team=board.addPlayerTeam("cc_map_team");setRed(team);board.addPlayerToTeam(server.getPlayerList().getPlayers().getFirst().getScoreboardName(),team);}
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static void setRed(net.minecraft.world.scores.PlayerTeam team){try{for(var method:team.getClass().getMethods())if(method.getName().equals("setColor")){Object value=method.getParameterTypes()[0]==java.util.Optional.class?java.util.Optional.of(Enum.valueOf((Class)Class.forName("net.minecraft.world.scores.TeamColor"),"RED")):net.minecraft.ChatFormatting.RED;method.invoke(team,value);return;}throw new AssertionError("Team color setter missing");}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static void external(String method){try{Class.forName("net.xolt.freecam.Freecam").getMethod(method).invoke(null);}catch(Exception error){throw new AssertionError(error);}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
