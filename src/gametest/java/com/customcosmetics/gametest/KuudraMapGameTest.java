package com.customcosmetics.gametest;

import com.customcosmetics.features.map.*;
import com.customcosmetics.config.ConfigManager;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/** Tests real client/server states with a synthetic Kuudra sidebar; never connects to Hypixel. */
public final class KuudraMapGameTest implements FabricClientGameTest {
    private static final MapEdits.Position REAL=new MapEdits.Position(-185,66,-185),GHOST=new MapEdits.Position(-185,66,-184);
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;MapEditor.forceDungeon=false;MapEditor.forceKuudra=false;});
        try(var world=context.worldBuilder().create()){
            prepare(world.getServer()::runCommand,context);world.getServer().runOnServer(s->sidebar(s,"Kuudra's Hollow"));context.waitTicks(8);
            context.runOnClient(client->{MapAdventureGameTest.remoteWorld=true;MapEditor.enabled=true;MapEditor.dungeonOnly=true;MapEditor.autoRooms=true;MapEditor.tick(client);DungeonRooms.scan();
                check(MapEditor.automaticKuudra(client)&&MapEditor.allowed(client),"Kuudra was not detected without Force");check(!MapEditor.automaticDungeon(client),"Kuudra was mistaken for Catacombs");
                check(MapEditor.currentRoom().key().equals(DungeonRooms.KUUDRA_KEY),"Legacy room grid intercepted Kuudra");
                MapEditor.editing=true;MapEditor.material="minecraft:gold_block";client.mouseHandler.grabMouse();FreecamGameTest.packets.clear();FreecamGameTest.observe=true;});
            context.getInput().pressMouse(mouse());context.waitTicks(3);
            context.runOnClient(client->{var ghost=MapEditor.DATA.snapshot().get(GHOST);check(ghost!=null&&ghost.originallyAir(),"Kuudra editor did not place a visual ghost");
                check(client.level.getBlockState(MapEditor.blockPos(GHOST)).isAir(),"Ghost changed client collision");check(MapEditor.ROOMS.edits(DungeonRooms.KUUDRA_KEY).containsKey(GHOST),"Kuudra edit was not auto-saved");
                MaterialReplacement.start("minecraft:stone","minecraft:dark_oak_planks",MaterialReplacement.Scope.ROOM);});
            context.waitFor(client->!MaterialReplacement.busy());
            context.runOnClient(client->{check(MapEditor.DATA.snapshot().get(REAL).material().equals("minecraft:dark_oak_planks"),"Kuudra bulk replacement failed");
                check(client.level.getBlockState(MapEditor.blockPos(REAL)).is(Blocks.STONE),"Bulk replacement changed world state");
                MapEditor.presetName="Native Kuudra";MapEditor.savePreset();MapEditor.clearRoom();MapEditor.loadPresetAuto();
                check(MapEditor.DATA.snapshot().containsKey(REAL)&&MapEditor.DATA.snapshot().containsKey(GHOST),"Kuudra preset did not restore");MapEditor.flushRooms();FreecamGameTest.observe=false;
                check(FreecamGameTest.packets.stream().noneMatch(p->p.contains("UseItem")||p.contains("PlayerActionPacket")||p.contains("InteractPacket")||p.contains("SwingPacket")),"Kuudra editor sent interaction packets");});
            world.getServer().runCommand("setblock -185 66 -185 minecraft:air");context.waitTicks(4);
            context.runOnClient(client->{check(client.level.getBlockState(MapEditor.blockPos(REAL)).isAir(),"Server removal was hidden by a fake block");check(!MapEditor.DATA.snapshot().get(REAL).originallyAir(),"Real-block edit became a ghost after server removal");});
            world.getServer().runCommand("setblock -185 66 -185 minecraft:stone");context.waitTicks(4);
            context.runOnClient(client->check(client.level.getBlockState(MapEditor.blockPos(REAL)).is(Blocks.STONE)&&MapEditor.visible().get(REAL).material().equals("minecraft:dark_oak_planks"),"Server-restored block lost its saved material"));
            world.getServer().runOnServer(s->sidebar(s,"The Catacombs (F7)"));context.waitTicks(4);
            context.runOnClient(client->{check(!MapEditor.inKuudra(client)&&MapEditor.DATA.snapshot().isEmpty(),"Kuudra edits leaked into Catacombs in the same ClientLevel");check(MapEditor.ROOMS.edits("floor:F7+M7").isEmpty(),"Kuudra edits polluted the F7/M7 layout");});
            world.getServer().runOnServer(s->sidebar(s,"The Catacombs (M7)"));context.waitTicks(4);
            var bossEdits=new java.util.concurrent.atomic.AtomicReference<java.util.Map<MapEdits.Position,MapEdits.Edit>>();
            context.runOnClient(client->{
                MapEditor.DATA.put(new MapEdits.Edit(REAL,"minecraft:gold_block",false));MapEditor.refresh();bossEdits.set(MapEditor.DATA.snapshot());
                check(MapEditor.automaticDungeon(client)&&MapEditor.floor().equals("M7"),"M7 fixture was not detected");
            });
            world.getServer().runOnServer(s->{
                var board=s.getScoreboard();var team=board.getPlayerTeam("kuudra_row");
                team.setPlayerPrefix(net.minecraft.network.chat.Component.literal("Power: 100%"));team.setPlayerSuffix(net.minecraft.network.chat.Component.empty());
                int score=2;for(String row:java.util.List.of("Apex: 100%","Soul: 100%","Ice: 100%","Flame: 100%"))
                    board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(row),board.getObjective("kuudra_test")).set(score++);
            });context.waitTicks(8);
            context.runOnClient(client->{
                check(!DungeonSidebar.matches(DungeonSidebarReader.read(client).title(),MapEditor.sidebarRows()),"Dragon fixture still contains a dungeon location");
                check(MapEditor.automaticDungeon(client)&&MapEditor.allowed(client)&&MapEditor.floor().equals("M7"),"Dragon sidebar disabled the confirmed M7 session");
                check(DungeonRooms.at(new MapEdits.Position(100,18,100)).key().equals("floor:F7+M7"),"P5 lost the shared F7/M7 layout");
                check(MapEditor.visible()==bossEdits.get()&&MapEditor.DATA.snapshot()==bossEdits.get(),"P5 dropped or recopied the active edits");
                check(client.level.getBlockState(MapEditor.blockPos(REAL)).is(Blocks.STONE),"P5 handling changed authoritative blocks");
            });
            world.getServer().runOnServer(s->s.getScoreboard().setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR,null));context.waitTicks(4);
            context.runOnClient(client->check(MapEditor.visible()==bossEdits.get()&&MapEditor.floor().equals("M7"),"Temporary sidebar removal lost M7 edits"));
            world.getServer().runOnServer(s->sidebar(s,"Dungeon Hub"));context.waitTicks(4);
            context.runOnClient(client->check(!MapEditor.automaticDungeon(client)&&!MapEditor.allowed(client)&&MapEditor.visible().isEmpty()&&MapEditor.floor().isEmpty(),"Dungeon session leaked into the Hub"));
            System.out.println("M7_P5_DRAGON_SIDEBAR_KEEPS_SHARED_LAYOUT_AND_SNAPSHOT_EXIT_CLEARS_CONTEXT_OK");
            world.getServer().runOnServer(s->sidebar(s,"Kuudra’s Hollow (T5)"));context.waitTicks(4);
            context.runOnClient(client->{check(MapEditor.visible().containsKey(REAL),"Kuudra template was not restored on arena re-entry");MapEditor.flushRooms();});
        }
        try(var world=context.worldBuilder().create()){
            prepare(world.getServer()::runCommand,context);world.getServer().runOnServer(s->sidebar(s,"Kuudra's Hollow (T1)"));context.waitTicks(8);
            context.runOnClient(client->{try{MapEditor.ROOMS.load(MapEditor.roomsPath());}catch(Exception error){throw new AssertionError(error);}MapEditor.enabled=true;MapEditor.tick(client);});context.waitTicks(4);
            context.runOnClient(client->{check(MapEditor.visible().containsKey(REAL)&&MapEditor.visible().containsKey(GHOST),"Kuudra disk saves did not reload in a fresh world");MapEditor.deletePreset();});
            world.getServer().runOnServer(s->s.getScoreboard().setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR,null));context.waitTicks(4);
            context.runOnClient(client->{MapAdventureGameTest.remoteWorld=false;MapEditor.forceDungeon(true);check(MapEditor.currentRoom().key().equals("floor:F7+M7"),"Existing forced singleplayer F7 behavior regressed");
                MapEditor.forceKuudra(true);check(!MapEditor.forceDungeon&&MapEditor.currentRoom().key().equals(DungeonRooms.KUUDRA_KEY),"Force Kuudra did not override the offline F7 default");
                check(MapEditor.visible().containsKey(REAL),"Force Kuudra did not restore its own layout");
                try{check(com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(MapEditor.settingsPath())).getAsJsonObject().get("forceKuudra").getAsBoolean(),"Force Kuudra did not persist");}catch(Exception error){throw new AssertionError(error);}
                MapEditor.forceKuudra(false);check(MapEditor.currentRoom()==null&&MapEditor.DATA.snapshot().isEmpty(),"Leaving forced Kuudra kept stale edits");MapEditor.enabled=false;MapEditor.editing=false;});
        }
        System.out.println("KUUDRA_AUTO_DETECT_EDIT_BATCH_PRESET_DISK_RELOAD_SCOPE_ISOLATION_SERVER_RESTORE_NO_PACKETS_OK");
    }
    private static void prepare(java.util.function.Consumer<String> command,ClientGameTestContext context){
        command.accept("gamemode creative @a");command.accept("forceload add -200 -200 -170 -170");command.accept("tp @a -184.5 65 -181 180 0");context.waitTicks(20);
        command.accept("fill -190 63 -190 -176 64 -176 minecraft:stone");command.accept("fill -185 65 -185 -185 72 -185 minecraft:stone");command.accept("tp @a -184.5 65 -181 180 0");command.accept("gamemode adventure @a");context.waitTicks(5);
    }
    private static void sidebar(net.minecraft.server.MinecraftServer server,String location){
        var board=server.getScoreboard();var objective=board.getObjective("kuudra_test");if(objective==null)objective=board.addObjective("kuudra_test",net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,net.minecraft.network.chat.Component.literal("SKYBLOCK"),net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,false,null);
        var team=board.getPlayerTeam("kuudra_row");if(team==null)team=board.addPlayerTeam("kuudra_row");int split=Math.min(12,location.length());team.setPlayerPrefix(net.minecraft.network.chat.Component.literal("§c\ue067 "+location.substring(0,split)));team.setPlayerSuffix(net.minecraft.network.chat.Component.literal(location.substring(split)));board.addPlayerToTeam("§u",team);board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("§u"),objective).set(1);board.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR,objective);
    }
    private static int mouse(){try{return com.mojang.blaze3d.platform.InputConstants.class.getField("MOUSE_BUTTON_RIGHT").getInt(null);}catch(Exception error){throw new AssertionError(error);}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
