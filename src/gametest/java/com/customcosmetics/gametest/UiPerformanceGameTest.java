// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.*;
import com.customcosmetics.gui.CosmeticsScreen;
import com.google.gson.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.InactivityFpsLimit;

/** Actual render-call intervals plus separately labelled GUI extraction CPU/allocation. Not Hypixel FPS. */
public final class UiPerformanceGameTest implements FabricClientGameTest {
    private static final com.sun.management.ThreadMXBean BEAN=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static final int SECONDS=Integer.getInteger("customcosmetics.uiSampleSeconds",6);
    private static volatile Sample sample;private static long entered;
    public static void begin(){var s=sample;if(s!=null&&!s.done){entered=System.nanoTime();s.before=BEAN.getCurrentThreadAllocatedBytes();}}
    public static void end(){var s=sample;if(s!=null&&!s.done&&s.cpuCount<s.cpu.length){s.cpu[s.cpuCount]=System.nanoTime()-entered;s.bytes[s.cpuCount++]=BEAN.getCurrentThreadAllocatedBytes()-s.before;}}
    public static void frame(){var s=sample;if(s==null||s.done)return;long now=System.nanoTime();if(s.started==0){s.started=s.previous=now;return;}if(s.frames<s.intervals.length)s.intervals[s.frames++]=now-s.previous;s.previous=now;if(net.minecraft.client.Minecraft.getInstance().isWindowActive())s.focused++;if(now-s.started>=SECONDS*1_000_000_000L||s.frames==s.intervals.length)s.done=true;}
    private static final class Sample {final long[] intervals=new long[65536],cpu=new long[65536],bytes=new long[65536];int frames,cpuCount,focused;long before,started,previous;volatile boolean done;}
    private static double percentile(long[] a,double p){return a.length==0?0:a[Math.min(a.length-1,(int)Math.ceil(a.length*p)-1)]/1e6;}
    private static long gcMillis(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean->Math.max(0,bean.getCollectionTime())).sum();}
    private static long gcCount(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean->Math.max(0,bean.getCollectionCount())).sum();}
    @Override public void runTest(ClientGameTestContext context){
        var rows=new JsonArray();jdk.jfr.Recording recording=null;
        try{
            BEAN.setThreadAllocatedMemoryEnabled(true);
            context.runOnClient(client->{ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;ConfigManager.updateCheckEnabled=false;ConfigManager.uiBackgroundBlur=false;ConfigManager.uiAnimationsEnabled=true;ConfigManager.uiLanguage=System.getProperty("customcosmetics.uiLanguage","en_us");FeatureModules.all(false);try{ConfigManager.class.getField("uiFont").set(null,System.getProperty("customcosmetics.uiFont","vanilla"));}catch(ReflectiveOperationException oldVersion){};});
            if(Boolean.getBoolean("customcosmetics.profileUi")){recording=new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"));recording.setMaxSize(64*1024*1024);recording.start();}
            try(var world=context.worldBuilder().setUseConsistentSettings(true).adjustSettings(s->s.setSeed("676767")).create()){
                world.getServer().runCommand("time set noon");context.getInput().resizeWindow(1600,1000);
                context.runOnClient(client->{client.options.guiScale().set(2);client.options.enableVsync().set(false);client.options.framerateLimit().set(260);client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);client.options.pauseOnLostFocus=false;});
                context.waitTicks(40);context.getInput().setCursorPos(1595,995);
                for(String phase:List.of("CLOSED","MAIN","ANIMATIONS","DAMAGE","THEME","CLOSED_AFTER")){
                    context.runOnClient(client->ClientUiAccess.setScreen(client,phase.startsWith("CLOSED")?null:new CosmeticsScreen(null,CosmeticsScreen.Page.valueOf(phase))));context.waitTicks(25);
                    long startMillis=System.currentTimeMillis(),gcMillis=gcMillis(),gcCount=gcCount();
                    var current=new Sample();context.runOnClient(client->sample=current);context.waitFor(client->current.done,800);context.runOnClient(client->sample=null);
                    if(current.frames<20)throw new AssertionError("Too few real rendered frames in "+phase);
                    if(phase.startsWith("CLOSED")&&current.cpuCount!=0)throw new AssertionError("Closed UI still extracted controls");
                    long[] intervals=Arrays.copyOf(current.intervals,current.frames),cpu=Arrays.copyOf(current.cpu,current.cpuCount);Arrays.sort(intervals);Arrays.sort(cpu);long total=0,slow=0,cpuTotal=0,allocated=0;for(long t:intervals)total+=t;for(long t:cpu)cpuTotal+=t;for(int i=0;i<current.cpuCount;i++)allocated+=current.bytes[i];int tail=Math.max(1,(int)Math.ceil(current.frames*.01));for(int i=current.frames-tail;i<current.frames;i++)slow+=intervals[i];
                    var row=new JsonObject();row.addProperty("page",phase);row.addProperty("sampleStartEpochMillis",startMillis);row.addProperty("sampleEndEpochMillis",System.currentTimeMillis());row.addProperty("gcMillis",gcMillis()-gcMillis);row.addProperty("gcCollections",gcCount()-gcCount);row.addProperty("actualFrames",current.frames);row.addProperty("focusedFrames",current.focused);row.addProperty("averageFps",current.frames*1e9/total);row.addProperty("onePercentLowFps",tail*1e9/slow);row.addProperty("meanFrameMs",total/1e6/current.frames);row.addProperty("p95FrameMs",percentile(intervals,.95));row.addProperty("p99FrameMs",percentile(intervals,.99));row.addProperty("maxFrameMs",percentile(intervals,1));row.addProperty("guiExtractions",current.cpuCount);row.addProperty("meanGuiCpuMs",current.cpuCount==0?0:cpuTotal/1e6/current.cpuCount);row.addProperty("p95GuiCpuMs",percentile(cpu,.95));row.addProperty("guiBytesPerFrame",current.cpuCount==0?0:allocated/(double)current.cpuCount);rows.add(row);
                }
                context.runOnClient(client->ClientUiAccess.setScreen(client,null));
            }
            var root=new JsonObject();root.addProperty("scope","isolated fixed world, actual frame intervals; not Hypixel gameplay");root.addProperty("sampleSeconds",SECONDS);root.addProperty("font",System.getProperty("customcosmetics.uiFont","vanilla"));root.addProperty("language",System.getProperty("customcosmetics.uiLanguage","en_us"));root.add("scenes",rows);root.addProperty("backend",com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().backendName());
            var output=FabricLoader.getInstance().getGameDir().resolve("ui-performance");Files.createDirectories(output);Files.writeString(output.resolve("frames.json"),new GsonBuilder().setPrettyPrinting().create().toJson(root));if(recording!=null){recording.stop();recording.dump(output.resolve("ui.jfr"));}
            System.out.println("UI_ACTUAL_FRAME_INTERVALS_EXTRACTION_AND_CLOSED_BASELINES_OK "+output);
        }catch(Exception error){throw new AssertionError(error);}finally{sample=null;if(recording!=null)recording.close();}
    }
}
