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

/** CPU submission time/allocation of actual menus, not GPU frame time or gameplay FPS. */
public final class UiPerformanceGameTest implements FabricClientGameTest {
    private static final com.sun.management.ThreadMXBean BEAN=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static volatile Sample sample;
    private static long entered;
    public static void begin(){var s=sample;if(s!=null&&s.count<s.times.length){entered=System.nanoTime();s.before=BEAN.getCurrentThreadAllocatedBytes();}}
    public static void end(){var s=sample;if(s!=null&&s.count<s.times.length){long bytes=BEAN.getCurrentThreadAllocatedBytes()-s.before;long ns=System.nanoTime()-entered;s.times[s.count]=ns;s.bytes[s.count++]=bytes;}}
    private static final class Sample {final long[] times=new long[600],bytes=new long[600];int count;long before;}
    @Override public void runTest(ClientGameTestContext context){
        var rows=new JsonArray();
        try{
            BEAN.setThreadAllocatedMemoryEnabled(true);
            context.runOnClient(client->{ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;FeatureModules.all(false);});
            try(var world=context.worldBuilder().setUseConsistentSettings(true).adjustSettings(s->s.setSeed("676767")).create()){
                context.getInput().resizeWindow(1600,1000);
                context.runOnClient(client->{client.options.guiScale().set(2);client.options.enableVsync().set(false);client.options.framerateLimit().set(120);client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);client.options.pauseOnLostFocus=false;});
                context.waitTicks(30);context.getInput().setCursorPos(1595,995);
                for(var page:List.of(CosmeticsScreen.Page.MODULES,CosmeticsScreen.Page.SIZE,CosmeticsScreen.Page.ANIMATIONS)){
                    context.runOnClient(client->ClientUiAccess.setScreen(client,new CosmeticsScreen(null,page)));context.waitTicks(30);
                    for(int repeat=0;repeat<3;repeat++){
                        var current=new Sample();context.runOnClient(client->sample=current);context.waitFor(client->current.count==current.times.length);context.runOnClient(client->sample=null);
                        var sorted=current.times.clone();Arrays.sort(sorted);long total=0,allocated=0;for(long t:sorted)total+=t;for(long b:current.bytes)allocated+=b;
                        var row=new JsonObject();row.addProperty("page",page.name());row.addProperty("repeat",repeat);row.addProperty("frames",sorted.length);row.addProperty("meanCpuMs",total/1e6/sorted.length);row.addProperty("p95CpuMs",sorted[(int)(sorted.length*.95)]/1e6);row.addProperty("maxCpuMs",sorted[sorted.length-1]/1e6);row.addProperty("bytesPerFrame",allocated/(double)sorted.length);rows.add(row);
                    }
                    // Keep the test hook armed while closed: no menu calls are permitted.
                    context.runOnClient(client->{ClientUiAccess.setScreen(client,null);sample=new Sample();});context.waitTicks(10);
                    context.runOnClient(client->{if(sample.count!=0)throw new AssertionError("Closed menu still rendered");sample=null;});
                }
            }
            var output=Path.of(System.getProperty("customcosmetics.testProjectDir"),"build/ui-performance",FabricLoader.getInstance().getModContainer("customcosmetics").orElseThrow().getMetadata().getVersion().getFriendlyString()+".json");Files.createDirectories(output.getParent());Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(rows));
            System.out.println("UI_CPU_SUBMISSION_ALLOCATION_AND_CLOSED_MENU_OK "+output);
        }catch(Exception error){throw new AssertionError(error);}finally{sample=null;}
    }
}
