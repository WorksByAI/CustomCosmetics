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
    public static final java.util.Queue<NativeFileDialogs.Result> fileSelections=new ConcurrentLinkedQueue<>();
    public static volatile boolean deferFileSelection;public static volatile java.util.function.Consumer<NativeFileDialogs.Result> delayedFileSelection;
    private static final AtomicInteger neutralPreviews=new AtomicInteger();
    public static void observePreview(net.minecraft.client.renderer.entity.state.AvatarRenderState state){
        if(!state.rightHandItemState.isEmpty()||!state.leftHandItemState.isEmpty()||!state.rightHandItemStack.isEmpty()||!state.leftHandItemStack.isEmpty()
                ||!state.headItem.isEmpty()||!state.heldOnHead.isEmpty()||!state.chestEquipment.isEmpty()||!state.headEquipment.isEmpty())
            throw new AssertionError("Held items or armor entered the actual cape preview render state");
        float swing;try{java.lang.reflect.Field field;try{field=state.getClass().getField("attackTime");}catch(NoSuchFieldException modern){field=state.getClass().getField("swingAnimation");}swing=field.getFloat(state);}catch(ReflectiveOperationException invalid){throw new AssertionError(invalid);}
        if(state.isCrouching||state.isFallFlying||state.isUsingItem||swing!=0||state.pose!=net.minecraft.world.entity.Pose.STANDING)
            throw new AssertionError("Cape preview did not use a neutral standing pose");
        neutralPreviews.incrementAndGet();
    }
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;});
        try(var world=context.worldBuilder().create()){
            try{
            world.getServer().runCommand("time set noon");world.getServer().runCommand("weather clear");
            context.waitTicks(10);context.getInput().resizeWindow(1600,1000);
            if(Files.exists(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("cape-local.png"))){
                context.runOnClient(client->{
                    ConfigManager.capeEnabled=true;CosmeticsMode.setOffline(true);CapeCosmetics.tick();
                    if(!CapeCosmetics.hasLocal()||CapeCosmetics.preview()==null||client.player.getSkin().cape()==null
                            ||!client.player.getSkin().cape().texturePath().equals(CapeCosmetics.preview()))
                        throw new AssertionError("An existing cape file failed to load during actual client startup");
                    ClientUiAccess.setScreen(client,new CapeScreen(null));
                });context.waitTicks(4);context.takeScreenshot("cape-existing-file-at-client-startup");
                System.out.println("CAPE_EXISTING_FILE_NATIVE_STARTUP_TEXTURE_AND_PREVIEW_OK");
            }
            var source=new BufferedImage(320,512,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<512;y++)for(int x=0;x<320;x++)source.setRGB(x,y,0xFF00FF6A);source.setRGB(137,200,0xFFCC0066);
            var file=Path.of(System.getProperty("customcosmetics.testProjectDir"),"build/audit/cape-input/input.png");Files.createDirectories(file.getParent());javax.imageio.ImageIO.write(source,"png",file.toFile());
            context.runOnClient(client->{
                client.options.guiScale().set(2);ConfigManager.capeEnabled=true;CosmeticsMode.setOffline(true);
                ConfigManager.smolpeopleMode=true;
                client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_AXE));
                client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
                client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN));
                client.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
                fileSelections.add(NativeFileDialogs.Result.selected(file.toString()));ClientUiAccess.setScreen(client,new CapeScreen(null));
            });click(context,"Choose Image & Crop");context.waitFor(client->ClientUiAccess.screen(client) instanceof CapeCropScreen);context.waitTicks(3);context.takeScreenshot("cape-crop");
            click(context,"Save Local Cape");context.waitTicks(4);
            context.runOnClient(client->{
                if(CapeCosmetics.localPng()==null||client.player.getSkin().cape()==null)throw new AssertionError("Local cape did not reach the actual player skin");
                var cropped=CapeImages.decode(CapeCosmetics.localPng());if(cropped.getWidth()!=2048||cropped.getHeight()!=1024||cropped.getRGB(32+137,32+200)!=0xFFCC0066)throw new AssertionError("The real crop GUI discarded original image details");
                System.out.println("CAPE_REAL_CROP_GUI_PRESERVES_SINGLE_PIXEL_DETAIL_OK");
                var original=client.player.getSkin();var sentinel=new net.minecraft.core.ClientAsset.Texture(){public net.minecraft.resources.Identifier id(){return net.minecraft.resources.Identifier.fromNamespaceAndPath("othermod","cape");}public net.minecraft.resources.Identifier texturePath(){return id();}};
                var external=new net.minecraft.world.entity.player.PlayerSkin(original.body(),sentinel,sentinel,original.model(),original.secure());
                var self=CapeCosmetics.skin(client.player.getUUID(),external);
                if(self.body()!=external.body()||self.model()!=external.model()||self.secure()!=external.secure())throw new AssertionError("Cape override changed another provider's skin or model");
                var other=CapeCosmetics.skin(java.util.UUID.randomUUID(),external);if(other!=external)throw new AssertionError("Offline mode replaced another provider's cape for a remote player");
                ConfigManager.capeEnabled=false;if(CapeCosmetics.skin(client.player.getUUID(),external)!=external)throw new AssertionError("Disabling custom capes did not restore the original provider");ConfigManager.capeEnabled=true;
            });capturePreview(context);
            context.runOnClient(client->{
                if(neutralPreviews.get()==0)throw new AssertionError("Cape preview did not reach its real neutral render state");
                if(!client.player.getMainHandItem().is(net.minecraft.world.item.Items.GOLDEN_AXE)||!client.player.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD)
                        ||!client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE))
                    throw new AssertionError("Preview cleared the actual player's equipment");
            });context.takeScreenshot("cape-smol-no-held-items-or-armor");
            var highResolution=new BufferedImage(2048,1024,BufferedImage.TYPE_INT_ARGB);
            var graphics=highResolution.createGraphics();try{graphics.setColor(new java.awt.Color(0x00FF6A));graphics.fillRect(0,0,2048,1024);}finally{graphics.dispose();}
            var atlasFile=file.getParent().resolve("high-resolution-2048x1024.png");javax.imageio.ImageIO.write(highResolution,"png",atlasFile.toFile());
            context.runOnClient(client->{fileSelections.add(NativeFileDialogs.Result.selected(atlasFile.toString()));ClientUiAccess.setScreen(client,new CapeScreen(null));});
            var atlasHash=CapeImages.hash(CapeImages.atlasPng(highResolution));click(context,"Choose Cape + Elytra");context.waitFor(client->CapeImages.hash(CapeCosmetics.localPng()).equals(atlasHash));context.waitTicks(4);
            context.runOnClient(client->{
                var local=CapeCosmetics.localPng();if(local==null)throw new AssertionError("High-resolution cape was rejected by the real file picker");
                var saved=CapeImages.decode(local);if(saved.getWidth()!=2048||saved.getHeight()!=1024||client.player.getSkin().cape()==null)throw new AssertionError("High-resolution cape was resized or failed to reach the real player skin");
            });capturePreview(context);context.takeScreenshot("cape-high-resolution-2048x1024");
            checkPlayerBlocks(context);
            restartPreview(context);
            testFileDialog(context,atlasFile);
            testTraffic(context);
            System.out.println("CAPE_NATIVE_PREVIEW_CROP_OFFLINE_ISOLATION_AND_EXPLICIT_REVIEW_OK");
            }catch(Exception|Error failure){
                System.err.println("CAPE_FIXTURE_FAILED_BEFORE_WORLD_CLOSE");failure.printStackTrace();throw failure;
            }finally{
                // Flush while Fabric's test client/server tick coordination is still connected.
                context.runOnClient(client->ClientUiAccess.setScreen(client,null));context.waitTicks(5);
                world.getServer().runCommand("save-all flush");
            }
        }catch(Exception failure){throw new AssertionError(failure);}
    }
    private void checkPlayerBlocks(ClientGameTestContext context){
        context.runOnClient(client->{
            var saved=ConfigManager.capeBlockedPlayers;var nicknames=ConfigManager.nickTweaksBlockedPlayers;
            var other=java.util.UUID.fromString("12345678-1234-4234-8234-123456789abc");
            try{
                CosmeticsMode.setOffline(false);String hash=CapeImages.hash(CapeCosmetics.localPng());CapeCosmetics.approved(java.util.Map.of(other,hash,client.player.getUUID(),hash));
                var original=client.player.getSkin();var vanilla=new net.minecraft.world.entity.player.PlayerSkin(original.body(),null,null,original.model(),original.secure());
                if(CapeCosmetics.skin(other,vanilla)==vanilla)throw new AssertionError("Approved remote cape fixture did not reach the real skin provider");
                PlayerBlocks.set(PlayerBlocks.Kind.CAPE,java.util.List.of(new PlayerBlocklist.Entry(other,"Alice"),new PlayerBlocklist.Entry(client.player.getUUID(),client.getUser().getName())));
                if(CapeCosmetics.skin(other,vanilla)!=vanilla)throw new AssertionError("Cape block did not preserve the original cape provider");
                if(CapeCosmetics.skin(client.player.getUUID(),vanilla)==vanilla)throw new AssertionError("A remote-player block changed the local cape");
                PlayerBlocks.set(PlayerBlocks.Kind.CAPE,java.util.List.of());PlayerBlocks.set(PlayerBlocks.Kind.NICK,java.util.List.of(new PlayerBlocklist.Entry(other,"Alice")));
                if(CapeCosmetics.skin(other,vanilla)==vanilla)throw new AssertionError("A nickname block accidentally hid the cape");
                ClientUiAccess.setScreen(client,new CapeScreen(null));
                var button=(PinkUi.Action)ClientUiAccess.screen(client).children().stream().filter(child->child instanceof PinkUi.Action action&&action.getMessage().getString().equals("Blocked Capes")).findFirst().orElseThrow();button.onClick(null,false);
                if(!(ClientUiAccess.screen(client) instanceof CosmeticsScreen))throw new AssertionError("Cape blocklist button did not open settings");
            }finally{ConfigManager.capeBlockedPlayers=saved;ConfigManager.nickTweaksBlockedPlayers=nicknames;CapeCosmetics.clearRemote();CosmeticsMode.setOffline(true);}
        });context.waitTicks(3);context.takeScreenshot("cape-player-blocklist");
        context.runOnClient(client->ClientUiAccess.setScreen(client,new CapeScreen(null)));
        System.out.println("CAPE_LOCAL_PLAYER_BLOCK_PRESERVES_VANILLA_PROVIDER_SELF_AND_OTHER_FEATURES_OK");
    }
    private void restartPreview(ClientGameTestContext context)throws Exception{
        var saved=new AtomicReference<byte[]>();var approved=new AtomicReference<net.minecraft.resources.Identifier>();
        context.runOnClient(client->{saved.set(CapeCosmetics.localPng());CapeCosmetics.close();CapeCosmetics.init();CapeCosmetics.tick();
            if(!CapeCosmetics.hasLocal()||CapeCosmetics.preview()==null||!client.player.getSkin().cape().texturePath().equals(CapeCosmetics.preview()))throw new AssertionError("Persisted local cape was not restored after cold texture/store initialization");});
        context.waitTicks(3);capturePreview(context);context.takeScreenshot("cape-restored-local-after-restart");
        context.runOnClient(client->{CapeCosmetics.clearLocal();CapeCosmetics.close();CapeCosmetics.init();CosmeticsMode.setOffline(false);
            var hash=CapeImages.hash(saved.get());approved.set(CapeCosmetics.texture(saved.get()));CapeCosmetics.approved(java.util.Map.of(client.player.getUUID(),hash));
            if(CapeCosmetics.hasLocal()||CapeCosmetics.localPng()!=null||!approved.get().equals(CapeCosmetics.preview()))throw new AssertionError("Approved cape did not become the preview when the local draft was absent");
            if(!client.player.getSkin().cape().texturePath().equals(approved.get()))throw new AssertionError("Preview does not match the approved cape worn in the world");
            ClientUiAccess.setScreen(client,new CapeScreen(null));
        });context.waitTicks(4);capturePreview(context);context.takeScreenshot("cape-approved-after-restart-without-local-file");
        context.runOnClient(client->{CosmeticsMode.setOffline(true);if(CapeCosmetics.preview()!=null)throw new AssertionError("Offline preview used an approved remote cape");CapeCosmetics.saveLocal(saved.get());ClientUiAccess.setScreen(client,new CapeScreen(null));});
        context.waitTicks(3);System.out.println("CAPE_COLD_RESTART_LOCAL_AND_APPROVED_PREVIEW_FALLBACK_WITH_OFFLINE_ISOLATION_OK");
    }
    private static void testFileDialog(ClientGameTestContext context,Path file)throws Exception{
        var before=CapeImages.hash(CapeCosmetics.localPng());fileSelections.add(NativeFileDialogs.Result.cancelled());click(context,"Choose Image & Crop");context.waitTicks(3);
        context.runOnClient(client->{if(!(ClientUiAccess.screen(client) instanceof CapeScreen)||NativeFileDialogs.busy()||!before.equals(CapeImages.hash(CapeCosmetics.localPng())))throw new AssertionError("Cancelling the system picker changed the local cape");});
        deferFileSelection=true;click(context,"Choose Cape + Elytra");context.waitTicks(2);
        context.runOnClient(client->{if(!NativeFileDialogs.busy()||delayedFileSelection==null)throw new AssertionError("A second dialog is not guarded");ClientUiAccess.setScreen(client,null);delayedFileSelection.accept(NativeFileDialogs.Result.selected(file.toString()));delayedFileSelection=null;});context.waitTicks(3);
        context.runOnClient(client->{if(ClientUiAccess.screen(client)!=null||NativeFileDialogs.busy()||!before.equals(CapeImages.hash(CapeCosmetics.localPng())))throw new AssertionError("A late OS selection reopened a closed screen");ClientUiAccess.setScreen(client,new CapeScreen(null));});
        fileSelections.add(NativeFileDialogs.Result.unavailableResult());click(context,"Choose Image & Crop");context.waitTicks(3);context.runOnClient(client->{if(!(ClientUiAccess.screen(client) instanceof CapeFileScreen))throw new AssertionError("Unavailable native picker did not offer the in-game fallback");ClientUiAccess.setScreen(client,new CapeScreen(null));});
        System.out.println("CAPE_SYSTEM_PICKER_BINDING_IMPORT_CANCEL_LATE_RESULT_AND_FALLBACK_OK");
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
        var executor=Executors.newCachedThreadPool(task->{var thread=new Thread(task,"Cape delayed fixture");thread.setDaemon(true);return thread;});server.setExecutor(executor);
        server.createContext("/",exchange->{exchange.getRequestBody().readAllBytes();String path=exchange.getRequestURI().getPath();boolean submit=path.equals("/capes/submit");if(submit){writes.incrementAndGet();try{Thread.sleep(16_000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.io.IOException(interrupted);}}else reads.incrementAndGet();
            String response=submit?"{\"status\":\"pending\",\"nextSubmissionAt\":"+(System.currentTimeMillis()+86400000)+"}":"{\"status\":\"none\",\"nextSubmissionAt\":0}";byte[] bytes=response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(submit?202:200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try{
            context.runOnClient(client->{
                ConfigManager.syncServerIp="127.0.0.1";ConfigManager.syncServerPort=server.getAddress().getPort();ConfigManager.syncServerHttps=false;ConfigManager.syncProxySource="none";
                CosmeticsMode.setOffline(false);String base="http://127.0.0.1:"+server.getAddress().getPort();
                var account=client.getUser();
                var session=new PresenceAuthClient.Session("a".repeat(64),account.getProfileId(),account.getName(),System.currentTimeMillis()+3600000,base);
                // Seed the shared cache after the online transition resets it, under its production monitor.
                var sessionsField=PresenceSync.class.getDeclaredField("SESSIONS");sessionsField.setAccessible(true);var sessions=sessionsField.get(null);
                var sessionField=sessions.getClass().getDeclaredField("session");sessionField.setAccessible(true);
                synchronized(sessions){sessionField.set(sessions,session);}
                var cached=PresenceSync.authenticatedSession();
                if(!cached.isDone()||cached.join()!=session)throw new AssertionError("Cape traffic fixture did not reuse the shared authenticated session");
                var old=client.player.getSkin();if(old.cape()!=null&&old.cape().texturePath().equals(CapeCosmetics.preview()))throw new AssertionError("Unreviewed local cape leaked into online rendering");
                ClientUiAccess.setScreen(client,new CapeScreen(null));
            });context.waitTicks(5);
            if(reads.get()!=0||writes.get()!=0)throw new AssertionError("Switching online or opening the cape page performed automatic I/O");
            click(context,"Submit for Review");
            context.waitFor(client->writes.get()==1);
            context.runOnClient(client->{var snapshot=new com.google.gson.JsonObject();snapshot.add("capes",new com.google.gson.JsonObject());CapeSync.acceptSnapshot(snapshot,"http://127.0.0.1:"+server.getAddress().getPort());});
            context.waitFor(client->reads.get()==1);context.waitTicks(4);
            if(!CapeSync.status().equals("Uploading cape..."))throw new AssertionError("A periodic status read replaced the in-progress upload with Not submitted");
            long deadline=System.nanoTime()+35_000_000_000L;while(CapeSync.cooldownSeconds()==0&&System.nanoTime()<deadline)context.waitTicks(2);
            if(writes.get()!=1||CapeSync.cooldownSeconds()==0||!CapeSync.status().equals("Pending review"))throw new AssertionError("A delayed upload acknowledgement was mistaken for submission failure");
            context.waitTicks(4);System.out.println("CAPE_UPLOAD_ACK_AFTER_16_SECONDS_WITHOUT_REPOST_OK");
            click(context,"Submit for Review");context.waitTicks(4);if(writes.get()!=1)throw new AssertionError("Daily cape submission cooldown was bypassed");
            context.runOnClient(client->{CosmeticsMode.setOffline(true);ClientUiAccess.setScreen(client,null);});
            if(reads.get()!=1)throw new AssertionError("Submission or ordinary buttons fetched data outside the explicit test sync");
        }finally{context.runOnClient(client->{CosmeticsMode.setOffline(true);ConfigManager.syncServerIp="cc.toryhi.com";ConfigManager.syncServerPort=443;ConfigManager.syncServerHttps=true;ConfigManager.syncEnabled=false;});server.stop(0);executor.shutdownNow();}
    }
    private static Object read(Object object,String name){try{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(Exception failure){throw new AssertionError(failure);}}
    private static void click(ClientGameTestContext context,String name)throws Exception{
        var point=new AtomicReference<double[]>();context.runOnClient(client->{var widget=ClientUiAccess.screen(client).children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.getMessage().getString().equals(name)).findFirst().orElseThrow();double scale=client.getWindow().getGuiScale();point.set(new double[]{(widget.getX()+widget.getWidth()/2.0)*scale,(widget.getY()+widget.getHeight()/2.0)*scale});});
        context.getInput().setCursorPos(point.get()[0],point.get()[1]);context.getInput().pressMouse(ClientInput.MOUSE_LEFT);context.waitTicks(2);
    }
}
