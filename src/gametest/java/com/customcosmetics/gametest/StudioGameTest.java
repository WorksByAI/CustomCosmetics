// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.*;
import com.customcosmetics.config.*;
import com.customcosmetics.gui.*;
import com.customcosmetics.sync.*;
import com.customcosmetics.features.blocking.BlockingAdjustment;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.nio.file.*;

public final class StudioGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client->{ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("gamemode creative @a");context.waitTicks(10);
            context.getInput().resizeWindow(1600,1000);
            context.runOnClient(client->{
                client.options.guiScale().set(2);ConfigManager.animationsEnabled=false;BlockingAdjustment.reset();
                ClientUiAccess.setScreen(client,new BlockingAdjustmentScreen(null));
            });context.waitTicks(4);
            var base=pixels(context,"blocking-adjustment-default");
            if(base.count<100)throw new AssertionError("Actual sword model is missing from preview: "+base);
            type(context,"Position X value","0.2");var shifted=pixels(context,"blocking-adjustment-position");
            if(Math.abs(shifted.x-base.x)<3)throw new AssertionError("Position X did not move the native item preview: "+base+" "+shifted);
            type(context,"Rotation Z value","30");var rotated=pixels(context,"blocking-adjustment-rotation");
            if(Math.abs(rotated.y-shifted.y)<2&&Math.abs(rotated.count-shifted.count)<40)throw new AssertionError("Rotation Z did not change the actual model preview");
            click(context,"Play BlockHit",PinkUi.Action.class);context.waitTicks(5);context.takeScreenshot("blocking-adjustment-swing");
            click(context,"Pause Preview",PinkUi.Action.class);click(context,"Done",PinkUi.Action.class);
            var closed=new AtomicInteger();
            context.runOnClient(client->{
                RemoteAccess.channel(()->(AutoCloseable)closed::incrementAndGet);
                ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SMOL));
            });click(context,"Online",PinkUi.Action.class);
            context.runOnClient(client->{
                if(!ConfigManager.offlineMode||closed.get()!=1||PresenceSync.syncOnce()||!PresenceSync.remoteNicknames().isEmpty())
                    throw new AssertionError("Offline button did not cancel connections and suppress remote synchronization");
            });context.takeScreenshot("offline-mode-sidebar");
            click(context,"Offline",PinkUi.Action.class);
            context.runOnClient(client->{if(ConfigManager.offlineMode)throw new AssertionError("Online button did not restore the selected mode");ClientUiAccess.setScreen(client,null);});
            checkNetworkButtons(context);
            System.out.println("BLOCKING_ADJUSTMENT_NATIVE_PREVIEW_AND_OFFLINE_BUTTON_OK");
        }catch(Exception error){throw new AssertionError(error);}
    }
    private record Pixels(int count,double x,double y){}
    private static void checkNetworkButtons(ClientGameTestContext context)throws Exception {
        var reads=new AtomicInteger();var writes=new AtomicInteger();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            exchange.getRequestBody().readAllBytes();boolean read=exchange.getRequestURI().getPath().equals("/users.json");
            if(read)reads.incrementAndGet();else writes.incrementAndGet();
            byte[] body=(read?"{\"authVersion\":2,\"users\":{}}":"{\"error\":\"test_identity_unavailable\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("content-type","application/json");exchange.sendResponseHeaders(read?200:503,body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        var colors=com.customcosmetics.features.nickname.NicknameColors.capture();
        try {
            context.runOnClient(client->{
                ConfigManager.syncServerIp="127.0.0.1";ConfigManager.syncServerPort=server.getAddress().getPort();ConfigManager.syncServerHttps=false;
                ConfigManager.syncProxySource="none";PresenceSync.onServerAddressChanged();PresenceSync.tick();
                ConfigManager.syncEnabled=true;ConfigManager.syncRefreshIntervalSeconds=30;
                var cadence=PresenceSync.class.getDeclaredField("CADENCE");cadence.setAccessible(true);
                ((com.customcosmetics.sync.SyncCadence)cadence.get(null)).noteRead(System.currentTimeMillis());
                com.customcosmetics.features.nickname.NicknamePresets.store().put("Traffic Test",colors);
                ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.PRESETS));
            });context.waitTicks(2);
            for(int i=0;i<5;i++)click(context,"Apply: Traffic Test",PinkUi.Action.class);
            context.getInput().pressKey(ClientInput.ESCAPE);context.waitTicks(5);
            if(reads.get()!=0||writes.get()!=0)throw new AssertionError("Apply or closing GUI performed immediate I/O: read="+reads+" write="+writes);
            context.runOnClient(client->{
                var cadence=PresenceSync.class.getDeclaredField("CADENCE");cadence.setAccessible(true);
                ((com.customcosmetics.sync.SyncCadence)cadence.get(null)).noteRead(System.currentTimeMillis()-30_100);
            });context.waitFor(client->reads.get()==1&&writes.get()==1);context.waitTicks(4);
            context.runOnClient(client->ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SYNC)));
            click(context,"Sync Now",PinkUi.Action.class);context.waitFor(client->reads.get()==2&&writes.get()==2);
            click(context,"Sync Now",PinkUi.Action.class);context.waitTicks(4);
            if(reads.get()!=2||writes.get()!=2)throw new AssertionError("Manual sync cooldown was bypassed");
            System.out.println("PRESET_BUTTONS_LOCAL_ONLY_PERIODIC_IO_AND_MANUAL_COOLDOWN_OK");
        }finally{
            context.runOnClient(client->{ConfigManager.syncEnabled=false;ConfigManager.syncServerIp="cc.toryhi.com";ConfigManager.syncServerPort=443;ConfigManager.syncServerHttps=true;
                PresenceSync.onServerAddressChanged();com.customcosmetics.features.nickname.NicknamePresets.store().remove("Traffic Test");ClientUiAccess.setScreen(client,null);});
            server.stop(0);
        }
    }
    private Pixels pixels(ClientGameTestContext context,String label)throws ReflectiveOperationException{
        var result=new CompletableFuture<Pixels>();context.waitTicks(3);
        context.runOnClient(client->{
            var screen=ClientUiAccess.screen(client);int x=(Integer)read(screen,"x"),y=(Integer)read(screen,"y"),w=(Integer)read(screen,"w"),top=(Integer)read(screen,"top");
            double scale=client.getWindow().getGuiScale();
            java.lang.reflect.Method method;
            try{method=client.gameRenderer.getClass().getMethod("mainRenderTarget");}
            catch(NoSuchMethodException legacy){method=client.getClass().getMethod("getMainRenderTarget");}
            var target=method.invoke(method.getDeclaringClass().isInstance(client.gameRenderer)?client.gameRenderer:client);
            Screenshot.takeScreenshot((com.mojang.blaze3d.pipeline.RenderTarget)target,image->{try(image){
                int count=0;double totalX=0,totalY=0;
                for(int sy=(int)((y+62)*scale);sy<(int)((top-15)*scale);sy++)for(int sx=(int)((x+18)*scale);sx<(int)((x+w-18)*scale);sx++){
                    int c=image.getPixel(sx,sy),r=(c>>16)&255,g=(c>>8)&255,b=c&255;
                    if(r>105&&g>105&&b>105&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<35){count++;totalX+=sx;totalY+=sy;}
                }
                var path=Path.of(System.getProperty("customcosmetics.testProjectDir"),"build/audit/studio-game",label+".png");Files.createDirectories(path.getParent());image.writeToFile(path);
                result.complete(new Pixels(count,totalX/Math.max(1,count),totalY/Math.max(1,count)));
            }catch(Throwable error){result.completeExceptionally(error);}});
        });context.waitFor(client->result.isDone());return result.join();
    }
    private static Object read(Object object,String field){try{var f=object.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(object);}catch(Exception error){throw new AssertionError(error);}}
    private static AbstractWidget widget(net.minecraft.client.Minecraft client,String prefix,Class<?> type){
        return ClientUiAccess.screen(client).children().stream().filter(type::isInstance).map(child->(AbstractWidget)child)
            .filter(child->child.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow(()->new AssertionError("Missing "+prefix));
    }
    private static void click(ClientGameTestContext context,String prefix,Class<?> type)throws Exception{
        var point=new AtomicReference<double[]>();context.runOnClient(client->{
            var screen=ClientUiAccess.screen(client);var widget=widget(client,prefix,type);
            if(screen instanceof BlockingAdjustmentScreen&&(widget.getY()<(Integer)read(screen,"top")||widget.getBottom()>(Integer)read(screen,"bottom"))&&type==PinkUi.Field.class){
                var scroll=screen.getClass().getDeclaredField("scroll");scroll.setAccessible(true);var maximum=screen.getClass().getDeclaredMethod("maxScroll");maximum.setAccessible(true);
                scroll.setInt(screen,Math.clamp((Integer)scroll.get(screen)+widget.getY()-(Integer)read(screen,"top")-4,0,(Integer)maximum.invoke(screen)));
                var apply=screen.getClass().getDeclaredMethod("position");apply.setAccessible(true);apply.invoke(screen);
            }
            if(screen instanceof CosmeticsScreen&&(widget.getY()<(Integer)read(screen,"viewTop")||widget.getBottom()>(Integer)read(screen,"viewBottom"))){
                var scroll=screen.getClass().getDeclaredField("scroll");scroll.setAccessible(true);var maximum=screen.getClass().getDeclaredMethod("maxScroll");maximum.setAccessible(true);
                scroll.setDouble(screen,Math.clamp((Double)scroll.get(screen)+widget.getY()-(Integer)read(screen,"viewTop")-20,0,(Double)maximum.invoke(screen)));
                var apply=screen.getClass().getDeclaredMethod("positionControls");apply.setAccessible(true);apply.invoke(screen);
            }
            double scale=client.getWindow().getGuiScale();point.set(new double[]{(widget.getX()+widget.getWidth()/2.0)*scale,(widget.getY()+widget.getHeight()/2.0)*scale});
        });context.getInput().setCursorPos(point.get()[0],point.get()[1]);context.getInput().pressMouse(ClientInput.MOUSE_LEFT);context.waitTicks(2);
    }
    private static void type(ClientGameTestContext context,String name,String value)throws Exception{
        click(context,name,PinkUi.Field.class);context.runOnClient(client->{var field=(PinkUi.Field)widget(client,name,PinkUi.Field.class);if(!field.isFocused())throw new AssertionError("Not focused "+name);field.setValue("");});
        context.getInput().typeChars(value);context.waitTicks(2);
    }
}
