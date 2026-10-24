// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.*;
import com.customcosmetics.features.map.MapEditor;
import com.customcosmetics.features.map.MapEdits;
import com.customcosmetics.features.waypoint.*;
import com.customcosmetics.utils.NickRenderUtils;
import com.google.gson.*;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Identical isolated-world frame workload for released and optimized clients; never a Hypixel FPS claim. */
public final class PerformanceComparisonGameTest implements FabricClientGameTest {
    private static final int SECONDS=Integer.getInteger("customcosmetics.comparisonSeconds",10);
    private static final int REPEATS=3,WARMUP_TICKS=120;
    private static final List<GarbageCollectorMXBean> GC=ManagementFactory.getGarbageCollectorMXBeans();
    private static final com.sun.management.ThreadMXBean ALLOCATION=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static volatile Sample sample;
    public static boolean simulatedSkyblock;
    private final List<JsonObject> results=new ArrayList<>();
    private final List<Integer> entities=new ArrayList<>();
    private Path output;
    private static long gcCount(){long sum=0;for(var bean:GC)sum+=Math.max(0,bean.getCollectionCount());return sum;}
    private static long gcMillis(){long sum=0;for(var bean:GC)sum+=Math.max(0,bean.getCollectionTime());return sum;}
    private static long heap(){var runtime=Runtime.getRuntime();return runtime.totalMemory()-runtime.freeMemory();}
    private static final class Sample {
        final String phase;final int repeat;final long[] intervals=new long[250_000];
        final long thread=Thread.currentThread().threadId(),allocatedBefore=ALLOCATION.getThreadAllocatedBytes(thread),gcBefore=gcCount(),gcMillisBefore=gcMillis(),heapBefore=heap();
        long started,previous,allocatedAfter,gcAfter,gcMillisAfter,heapAfter,runTickCalls,noLevelTickCalls;int count;volatile boolean done;
        Sample(String phase,int repeat){this.phase=phase;this.repeat=repeat;}
        void frame(long now){
            if(started==0){started=previous=now;return;}
            if(count>=intervals.length)throw new AssertionError("Frame sample buffer overflow");
            intervals[count++]=now-previous;previous=now;
            if(now-started>=SECONDS*1_000_000_000L){allocatedAfter=ALLOCATION.getThreadAllocatedBytes(thread);gcAfter=gcCount();gcMillisAfter=gcMillis();heapAfter=heap();done=true;}
        }
        JsonObject result(){
            if(count<100)throw new AssertionError("Not enough rendered frames in "+phase+": "+count);
            var sorted=Arrays.copyOf(intervals,count);Arrays.sort(sorted);long sum=0,slow=0;for(long value:sorted)sum+=value;
            int tail=Math.max(1,(int)Math.ceil(count*.01));for(int i=count-tail;i<count;i++)slow+=sorted[i];
            var result=new JsonObject();result.addProperty("phase",phase);result.addProperty("repeat",repeat);result.addProperty("frames",count);
            result.addProperty("runTickCalls",runTickCalls);result.addProperty("noLevelTickCalls",noLevelTickCalls);result.addProperty("actualLevelRenderInvocations",count+1);
            result.addProperty("seconds",sum/1e9);result.addProperty("averageFps",count*1e9/sum);result.addProperty("onePercentLowFps",tail*1e9/slow);
            result.addProperty("meanFrameMs",sum/1e6/count);result.addProperty("p50FrameMs",percentile(sorted,.5));result.addProperty("p95FrameMs",percentile(sorted,.95));result.addProperty("p99FrameMs",percentile(sorted,.99));result.addProperty("maxFrameMs",sorted[count-1]/1e6);
            result.addProperty("renderThreadAllocatedBytes",allocatedAfter-allocatedBefore);result.addProperty("gcCount",gcAfter-gcBefore);result.addProperty("gcMillis",gcMillisAfter-gcMillisBefore);result.addProperty("heapBeforeBytes",heapBefore);result.addProperty("heapAfterBytes",heapAfter);
            return result;
        }
    }
    /** Counts ordinary loop iterations separately so skipped frames cannot masquerade as rendered FPS. */
    public static void tick(boolean renderLevel){var current=sample;if(current!=null&&!current.done){current.runTickCalls++;if(!renderLevel)current.noLevelTickCalls++;}}
    /** Successive actual world-render calls include normal CPU/GPU work, ticks and scheduling between frames. */
    public static void frame(){var current=sample;if(current!=null&&!current.done)current.frame(System.nanoTime());}
    private static double percentile(long[] values,double p){return values[Math.clamp((int)Math.ceil(values.length*p)-1,0,values.length-1)]/1e6;}
    @Override public void runTest(ClientGameTestContext context){
        try{
            output=FabricLoader.getInstance().getGameDir().resolve("performance-comparison").toAbsolutePath().normalize();
            var project=Path.of(System.getProperty("customcosmetics.testProjectDir")).toAbsolutePath().normalize();
            if(!output.startsWith(project.resolve("build")))throw new AssertionError("Benchmark output must stay in the isolated build directory");
            Files.createDirectories(output);ALLOCATION.setThreadAllocatedMemoryEnabled(true);
            context.runOnClient(client->{
                ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.updateCheckEnabled=false;
                ConfigManager.partyEspEnabled=false;ConfigManager.partyChatEnabled=false;ConfigManager.nickTweaksEnabled=false;ConfigManager.nickTweaksRankEnabled=false;
                ConfigManager.playerSizeEnabled=false;ConfigManager.smolpeopleMode=false;ConfigManager.damageSplashEnabled=false;
                MapEditor.enabled=false;MapEditor.autoRooms=false;MapEditor.editing=false;MapEditor.forceDungeon=false;MapEditor.forceKuudra=false;MapEditor.DATA.reset();
                Waypoints.replace(WaypointData.Snapshot.empty());
            });
            try(var world=context.worldBuilder().setUseConsistentSettings(true).adjustSettings(settings->settings.setSeed("676767")).create()){
                context.getInput().resizeWindow(1280,720);
                context.runOnClient(client->{
                    client.options.guiScale().set(2);client.options.renderDistance().set(8);client.options.simulationDistance().set(5);
                    client.options.enableVsync().set(false);client.options.framerateLimit().set(260);client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);client.options.pauseOnLostFocus=false;client.options.fov().set(70);
                });
                world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("difficulty peaceful");
                world.getServer().runCommand("time set noon");world.getServer().runCommand("weather clear 100000");
                world.getServer().runCommand("tp @a 0.5 -60 0.5 0 8");
                world.getServer().runCommand("fill -24 -60 18 24 -48 18 minecraft:stone");
                context.waitTicks(100);context.runOnClient(client->{ClientUiAccess.setScreen(client,null);client.mouseHandler.grabMouse();});
                context.runOnClient(client->FeatureModules.all(false));measure(context,"modules_off_idle");
                context.runOnClient(client->FeatureModules.all(true));measure(context,"modules_on_idle");
                context.runOnClient(this::crowd);measure(context,"96_labels_64_waypoints");
                context.runOnClient(client->{
                    for(int id:entities)client.level.removeEntity(id,Entity.RemovalReason.DISCARDED);entities.clear();
                    simulatedSkyblock=false;ConfigManager.nickTweaksEnabled=false;ConfigManager.damageSplashEnabled=false;NickRenderUtils.invalidateLocalTargetsCache();Waypoints.replace(WaypointData.Snapshot.empty());
                    var edits=new HashMap<MapEdits.Position,MapEdits.Edit>();
                    for(int x=-40;x<40;x++)for(int y=-64;y<0;y++)for(int z=-40;z<40;z++){var p=new MapEdits.Position(x,y,z);edits.put(p,new MapEdits.Edit(p,"minecraft:deepslate_bricks",false));}
                    MapEditor.DATA.replace(edits);MapEditor.dungeonOnly=false;MapEditor.enabled=true;MapEditor.refresh();
                });
                context.waitTicks(160);measure(context,"409600_map_edits_settled");
                context.runOnClient(client->{MapEditor.enabled=false;MapEditor.DATA.reset();MapEditor.refresh();FeatureModules.all(true);});
            }
            writeSummary();System.out.println("PERFORMANCE_COMPARISON_COMPLETE path="+output.resolve("summary.json"));
        }catch(Exception error){throw new AssertionError(error);}finally{sample=null;simulatedSkyblock=false;}
    }
    private void crowd(Minecraft client){
        simulatedSkyblock=true;ConfigManager.damageSplashEnabled=true;
        ConfigManager.nickTweaksEnabled=true;ConfigManager.nickTweaksCustomNickColorEnabled=true;ConfigManager.nickTweaksChromaEnabled=false;ConfigManager.nickTweaksNickChanger="Benchmark";
        ConfigManager.nickTweaksGradientStyle="shimmer";ConfigManager.nickTweaksGradientStartColor=0x24CFDF;ConfigManager.nickTweaksGradientEndColor=0xEA78C5;NickRenderUtils.invalidateLocalTargetsCache();
        for(int i=0;i<96;i++){
            var stand=new ArmorStand(client.level,(i%12-5.5)*1.3,-60+(i/12)*.65,5+(i/12)*1.2);int id=-68000-i;
            stand.setId(id);stand.setUUID(new UUID(676767,i+1));stand.setInvisible(true);stand.setNoGravity(true);stand.setCustomNameVisible(true);
            stand.setCustomName(Component.literal(i<64?client.getUser().getName()+" NPC "+i:"Teleport Pad "+i));client.level.addEntity(stand);entities.add(id);
        }
        try{
            var type=Class.forName("com.customcosmetics.features.waypoint.WaypointWorld");var field=type.getDeclaredField("SESSION");field.setAccessible(true);var session=field.get(null);
            var location=session.getClass().getDeclaredMethod("location",Object.class,String.class,String.class);location.setAccessible(true);location.invoke(session,client.getConnection(),"SKYBLOCK","hub");
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        var points=new ArrayList<WaypointData.Point>();for(int i=0;i<64;i++)points.add(new WaypointData.Point("Marker "+i,(i%8-4)*3,-60,6+(i/8)*3,0x22DDFF,true));
        ConfigManager.waypointMaxRendered=64;ConfigManager.waypointShowLabels=true;ConfigManager.waypointShowDistance=false;ConfigManager.waypointMaxDistance=0;
        Waypoints.replace(new WaypointData.Snapshot(true,List.of(new WaypointData.Group("00000000-0000-0000-0000-000000676767","Benchmark","hypixel:hub",true,false,true,points,"outlined_highlight"))));
    }
    private void measure(ClientGameTestContext context,String phase)throws Exception{
        context.waitTicks(WARMUP_TICKS);
        var actual=context.computeOnClient(client->{
            var window=client.getWindow();
            if(window.getWidth()!=1280||window.getHeight()!=720||client.options.renderDistance().get()!=8||client.options.simulationDistance().get()!=5||client.options.enableVsync().get()||client.options.framerateLimit().get()!=260||window.isIconified())
                throw new AssertionError("Benchmark graphics settings differ from the required fixture");
            int visible=0;try{var field=Waypoints.class.getDeclaredField("visibleCount");field.setAccessible(true);visible=field.getInt(null);}catch(ReflectiveOperationException error){throw new AssertionError(error);}
            if(phase.equals("96_labels_64_waypoints")){
                int found=0;for(int id:entities)if(client.level.getEntity(id)!=null)found++;
                boolean damageAllowed;try{var field=com.customcosmetics.features.damage.DamageSplash.class.getDeclaredField("allowed");field.setAccessible(true);damageAllowed=field.getBoolean(null);}catch(ReflectiveOperationException error){throw new AssertionError(error);}
                if(found!=96||visible!=64||!NickRenderUtils.hasActiveTargets()||!damageAllowed||!ConfigManager.damageSplashEnabled)throw new AssertionError("Crowded feature fixture is inactive: entities="+found+", waypoints="+visible);
            }
            if(phase.equals("409600_map_edits_settled")&&MapEditor.visible().size()!=409600)throw new AssertionError("Dense map fixture is not visible");
            var settings=new JsonObject();settings.addProperty("framebufferWidth",window.getWidth());settings.addProperty("framebufferHeight",window.getHeight());settings.addProperty("windowWidth",window.getScreenWidth());settings.addProperty("windowHeight",window.getScreenHeight());
            settings.addProperty("renderDistance",client.options.renderDistance().get());settings.addProperty("effectiveRenderDistance",client.options.getEffectiveRenderDistance());settings.addProperty("simulationDistance",client.options.simulationDistance().get());settings.addProperty("vsync",client.options.enableVsync().get());settings.addProperty("fpsLimitSetting",client.options.framerateLimit().get());settings.addProperty("unlimitedCutoff",net.minecraft.client.Options.UNLIMITED_FRAMERATE_CUTOFF);settings.addProperty("fov",client.options.fov().get());
            settings.addProperty("waypointsVisible",visible);settings.addProperty("mapEditsVisible",MapEditor.visible().size());settings.addProperty("disabledModules",ConfigManager.disabledModules);settings.addProperty("playerX",client.player.getX());settings.addProperty("playerY",client.player.getY());settings.addProperty("playerZ",client.player.getZ());settings.addProperty("yaw",client.player.getYRot());settings.addProperty("pitch",client.player.getXRot());return settings;
        });
        context.takeScreenshot("performance-"+phase);context.waitTicks(20);
        for(int repeat=1;repeat<=REPEATS;repeat++){
            int number=repeat;context.runOnClient(client->{if(ClientUiAccess.screen(client)!=null)throw new AssertionError("Benchmark screen is not closed");sample=new Sample(phase,number);});
            context.waitFor(client->sample!=null&&sample.done,(SECONDS+30)*20);
            var completed=sample;sample=null;var result=completed.result();result.add("actualSettings",actual);results.add(result);
            var raw=new StringBuilder(completed.count*10);raw.append("frame,interval_ns\n");for(int i=0;i<completed.count;i++)raw.append(i).append(',').append(completed.intervals[i]).append('\n');
            Files.writeString(output.resolve(phase+"-"+repeat+".csv"),raw);
            Files.writeString(output.resolve(phase+"-"+repeat+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
            System.out.println("PERFORMANCE_FRAME_SAMPLE "+result);context.waitTicks(20);
        }
    }
    private void writeSummary()throws Exception{
        var summary=new JsonObject();summary.addProperty("modVersion",FabricLoader.getInstance().getModContainer("customcosmetics").orElseThrow().getMetadata().getVersion().getFriendlyString());
        summary.addProperty("minecraft","26.2");summary.addProperty("resolution","1280x720");summary.addProperty("renderDistance",8);summary.addProperty("simulationDistance",5);summary.addProperty("vsync",false);summary.addProperty("fpsLimitSetting",260);summary.addProperty("fov",70);summary.addProperty("seed",676767);summary.addProperty("sampleSeconds",SECONDS);summary.addProperty("repeats",REPEATS);
        summary.addProperty("camera","Fixed position (0.5,-60,0.5), yaw 0, pitch 8; identical stationary trajectory");summary.addProperty("scope","Isolated deterministic flat world; simulated SkyBlock render context; no live Hypixel/network/player-count claim");
        summary.addProperty("frameProbe","Minecraft.renderFrame at actual GameRenderer.render invocation, only renderLevel=true; skipped runTick iterations are counted separately");
        summary.addProperty("onePercentLowDefinition","1000000000 divided by the arithmetic mean of the slowest ceil(frameCount * 0.01) frame intervals");summary.addProperty("java",System.getProperty("java.version"));summary.addProperty("os",System.getProperty("os.name"));summary.addProperty("logicalProcessors",Runtime.getRuntime().availableProcessors());summary.addProperty("maxHeapBytes",Runtime.getRuntime().maxMemory());summary.add("samples",new Gson().toJsonTree(results));
        Files.writeString(output.resolve("summary.json"),new GsonBuilder().setPrettyPrinting().create().toJson(summary));
    }
}
