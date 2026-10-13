// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;
import com.customcosmetics.compatibility.*;
import com.customcosmetics.config.*;
import com.customcosmetics.gui.*;
import com.customcosmetics.features.cape.*;
import com.customcosmetics.sync.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.Screenshot;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.nio.file.*;

public final class CapeGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;});
        try(var world=context.worldBuilder().create()){
            context.waitTicks(10);context.getInput().resizeWindow(1600,1000);
            var source=new BufferedImage(100,200,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<200;y++)for(int x=0;x<100;x++)source.setRGB(x,y,0xFF00FF6A);
            var file=Path.of(System.getProperty("customcosmetics.testProjectDir"),"build/audit/cape-input/input.png");Files.createDirectories(file.getParent());javax.imageio.ImageIO.write(source,"png",file.toFile());
            context.runOnClient(client->{
                client.options.guiScale().set(2);ConfigManager.capeEnabled=true;CosmeticsMode.setOffline(true);
                ClientUiAccess.setScreen(client,new CapeFileScreen(new CapeScreen(null),false,file.getParent()));
            });click(context,"input.png");context.waitTicks(3);context.takeScreenshot("cape-crop");
            click(context,"Save Local Cape");context.waitTicks(4);
            context.runOnClient(client->{
                if(CapeCosmetics.localPng()==null||client.player.getSkin().cape()==null)throw new AssertionError("Local cape did not reach the actual player skin");
                var original=client.player.getSkin();var sentinel=new net.minecraft.core.ClientAsset.Texture(){public net.minecraft.resources.Identifier id(){return net.minecraft.resources.Identifier.fromNamespaceAndPath("othermod","cape");}public net.minecraft.resources.Identifier texturePath(){return id();}};
                var external=new net.minecraft.world.entity.player.PlayerSkin(original.body(),sentinel,sentinel,original.model(),original.secure());
                var self=CapeCosmetics.skin(client.player.getUUID(),external);
                if(self.body()!=external.body()||self.model()!=external.model()||self.secure()!=external.secure())throw new AssertionError("Cape override changed another provider's skin or model");
                var other=CapeCosmetics.skin(java.util.UUID.randomUUID(),external);if(other!=external)throw new AssertionError("Offline mode replaced another provider's cape for a remote player");
                ConfigManager.capeEnabled=false;if(CapeCosmetics.skin(client.player.getUUID(),external)!=external)throw new AssertionError("Disabling custom capes did not restore the original provider");ConfigManager.capeEnabled=true;
            });capturePreview(context);
            var highResolution=new BufferedImage(2048,1024,BufferedImage.TYPE_INT_ARGB);
            var graphics=highResolution.createGraphics();try{graphics.setColor(new java.awt.Color(0x00FF6A));graphics.fillRect(0,0,2048,1024);}finally{graphics.dispose();}
            var atlasFile=file.getParent().resolve("high-resolution-2048x1024.png");javax.imageio.ImageIO.write(highResolution,"png",atlasFile.toFile());
            context.runOnClient(client->ClientUiAccess.setScreen(client,new CapeFileScreen(new CapeScreen(null),true,atlasFile.getParent())));
            click(context,"high-resolution-2048x1024.png");context.waitTicks(4);
            context.runOnClient(client->{
                var local=CapeCosmetics.localPng();if(local==null)throw new AssertionError("High-resolution cape was rejected by the real file picker");
                var saved=CapeImages.decode(local);if(saved.getWidth()!=2048||saved.getHeight()!=1024||client.player.getSkin().cape()==null)throw new AssertionError("High-resolution cape was resized or failed to reach the real player skin");
            });capturePreview(context);context.takeScreenshot("cape-high-resolution-2048x1024");
            testTraffic(context);
            System.out.println("CAPE_NATIVE_PREVIEW_CROP_OFFLINE_ISOLATION_AND_EXPLICIT_REVIEW_OK");
        }catch(Exception failure){throw new AssertionError(failure);}
    }
    private void capturePreview(ClientGameTestContext context)throws Exception{
        var result=new CompletableFuture<Integer>();context.runOnClient(client->{
            var screen=ClientUiAccess.screen(client);int x=(int)read(screen,"x"),y=(int)read(screen,"y"),w=(int)read(screen,"w"),h=(int)read(screen,"h");double scale=client.getWindow().getGuiScale();
            java.lang.reflect.Method method;try{method=client.gameRenderer.getClass().getMethod("mainRenderTarget");}catch(NoSuchMethodException legacy){method=client.getClass().getMethod("getMainRenderTarget");}
            var target=method.invoke(method.getDeclaringClass().isInstance(client.gameRenderer)?client.gameRenderer:client);
            Screenshot.takeScreenshot((com.mojang.blaze3d.pipeline.RenderTarget)target,image->{try(image){
                int count=0;for(int py=(int)((y+70)*scale);py<(int)((y+h-145)*scale);py++)for(int px=(int)((x+w/3)*scale);px<(int)((x+w-20)*scale);px++){
                    int c=image.getPixel(px,py);if(((c>>8)&255)>140&&((c>>16)&255)<65&&(c&255)>40&&(c&255)<150)count++;
                }
                var path=Path.of(System.getProperty("customcosmetics.testProjectDir"),"build/audit/cape-game/preview.png");Files.createDirectories(path.getParent());image.writeToFile(path);result.complete(count);
            }catch(Throwable failure){result.completeExceptionally(failure);}});
        });context.waitFor(client->result.isDone());if(result.join()<100)throw new AssertionError("Cape is not visible on the native back-view player model: "+result.join());
    }
    private void testTraffic(ClientGameTestContext context)throws Exception{
        var reads=new AtomicInteger();var writes=new AtomicInteger();var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{exchange.getRequestBody().readAllBytes();String path=exchange.getRequestURI().getPath();boolean submit=path.equals("/capes/submit");if(submit)writes.incrementAndGet();else reads.incrementAndGet();
            String response=submit?"{\"status\":\"pending\",\"nextSubmissionAt\":"+(System.currentTimeMillis()+86400000)+"}":"{\"users\":{},\"authVersion\":2}";byte[] bytes=response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(submit?202:200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try{
            context.runOnClient(client->{
                ConfigManager.syncServerIp="127.0.0.1";ConfigManager.syncServerPort=server.getAddress().getPort();ConfigManager.syncServerHttps=false;ConfigManager.syncProxySource="none";
                CosmeticsMode.setOffline(false);String base="http://127.0.0.1:"+server.getAddress().getPort();
                var field=PresenceSync.class.getDeclaredField("authSession");field.setAccessible(true);var account=client.getUser();
                field.set(null,new PresenceAuthClient.Session("a".repeat(64),account.getProfileId(),account.getName(),System.currentTimeMillis()+3600000,base));
                var old=client.player.getSkin();if(old.cape()!=null&&old.cape().texturePath().equals(CapeCosmetics.preview()))throw new AssertionError("Unreviewed local cape leaked into online rendering");
                ClientUiAccess.setScreen(client,new CapeScreen(null));
            });context.waitTicks(5);
            if(reads.get()!=0||writes.get()!=0)throw new AssertionError("Switching online or opening the cape page performed automatic I/O");
            click(context,"Submit for Review");context.waitFor(client->writes.get()==1&&CapeSync.cooldownSeconds()>0);context.waitTicks(4);
            click(context,"Submit for Review");context.waitTicks(4);if(writes.get()!=1)throw new AssertionError("Daily cape submission cooldown was bypassed");
            context.runOnClient(client->{CosmeticsMode.setOffline(true);ClientUiAccess.setScreen(client,null);});
            if(reads.get()!=0)throw new AssertionError("Explicit submission fetched unrelated appearance data");
        }finally{context.runOnClient(client->{ConfigManager.syncServerIp="cc.toryhi.com";ConfigManager.syncServerPort=443;ConfigManager.syncServerHttps=true;ConfigManager.syncEnabled=false;});server.stop(0);}
    }
    private static Object read(Object object,String name){try{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(Exception failure){throw new AssertionError(failure);}}
    private static void click(ClientGameTestContext context,String name)throws Exception{
        var point=new AtomicReference<double[]>();context.runOnClient(client->{var widget=ClientUiAccess.screen(client).children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.getMessage().getString().equals(name)).findFirst().orElseThrow();double scale=client.getWindow().getGuiScale();point.set(new double[]{(widget.getX()+widget.getWidth()/2.0)*scale,(widget.getY()+widget.getHeight()/2.0)*scale});});
        context.getInput().setCursorPos(point.get()[0],point.get()[1]);context.getInput().pressMouse(ClientInput.MOUSE_LEFT);context.waitTicks(2);
    }
}
