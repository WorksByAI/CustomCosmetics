package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.*;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.damage.*;
import com.customcosmetics.features.party.*;
import com.customcosmetics.gui.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class DamageSplashGameTest implements FabricClientGameTest {
    public static boolean fixtureWorld;
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.partyEspEnabled=false;ConfigManager.partyChatEnabled=false;});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("time set noon");world.getServer().runCommand("weather clear");
            context.waitTicks(15);world.getServer().runCommand("tp @a 0.5 -60 -0.5 0 0");context.waitTicks(8);
            context.getInput().resizeWindow(1280,720);
            var stand=new AtomicReference<ArmorStand>();
            context.runOnClient(client->{
                client.options.guiScale().set(2);fixtureWorld=false;
                if(DamageSplash.isSkyblock(client))throw new AssertionError("Singleplayer was mistaken for SkyBlock");
                fixtureWorld=true;ConfigManager.damageSplashEnabled=false;DamageSplash.tick(client);
                var entity=new ArmorStand(client.level,.5,-60,4.5);entity.setId(-18700);entity.setInvisible(true);entity.setCustomNameVisible(true);entity.setCustomName(Component.literal("✧1,234,567✧"));client.level.addEntity(entity);stand.set(entity);
                if(DamageSplash.capture(entity)||size("LABELS")!=0)throw new AssertionError("Disabled damage splash captured a label");
                ConfigManager.damageSplashEnabled=true;ConfigManager.damageSplashDuration=3;ConfigManager.damageSplashStartColor=0xFF00FF;ConfigManager.damageSplashEndColor=0xFF00FF;DamageSplash.tick(client);
                var values=entity.getEntityData().getNonDefaultValues();entity.setCustomName(null);
                client.getConnection().handleSetEntityData(new ClientboundSetEntityDataPacket(entity.getId(),values));
                if(!entity.getCustomName().getString().equals("✧1,234,567✧")||size("LABELS")!=1)throw new AssertionError("Metadata hook did not capture exactly one label");
                EntityRenderer renderer=client.getEntityRenderDispatcher().getRenderer(entity);var state=renderer.createRenderState();
                renderer.extractRenderState(entity,state,0);renderer.extractRenderState(entity,state,0);
                if(!((DamageRenderState)state).customcosmetics$damageName()||size("LABELS")!=1)throw new AssertionError("Renderer did not suppress the original or duplicated its label");
                if(entity.isRemoved()||client.level.getEntity(entity.getId())!=entity)throw new AssertionError("Visual feature removed the real entity");
            });
            context.waitTicks(4);if(pinkPixels(context)<10)throw new AssertionError("World damage text did not reach the GPU");context.takeScreenshot("damage-splash-world-gradient");
            context.runOnClient(client->{
                var entity=stand.get();ConfigManager.damageSplashEnabled=false;DamageSplash.tick(client);
                EntityRenderer renderer=client.getEntityRenderDispatcher().getRenderer(entity);var state=renderer.createRenderState();renderer.extractRenderState(entity,state,0);
                if(((DamageRenderState)state).customcosmetics$damageName()||size("LABELS")!=0)throw new AssertionError("Disabling did not restore vanilla labels");
                ConfigManager.damageSplashEnabled=true;DamageSplash.tick(client);
                for(String name:List.of("1,000❤","ToryHi","Lv100 123","123/200")){entity.setCustomName(Component.literal(name));if(DamageSplash.capture(entity))throw new AssertionError("Not a damage label: "+name);}
                // Reuse one stand as the server does while the fishing timer counts down.
                int labelsBeforeTimer=size("LABELS"),seenBeforeTimer=size("SEEN");
                for(String name:List.of("3.0","2.1","1.4","0.7","0.0","!!!")){
                    entity.setCustomName(Component.literal(name).withStyle(style->style.withColor(0xFFFF55)));
                    var timerValues=entity.getEntityData().getNonDefaultValues();entity.setCustomName(null);
                    client.getConnection().handleSetEntityData(new ClientboundSetEntityDataPacket(entity.getId(),timerValues));
                    renderer.extractRenderState(entity,state,0);
                    if(((DamageRenderState)state).customcosmetics$damageName()||size("LABELS")!=labelsBeforeTimer||size("SEEN")!=seenBeforeTimer)
                        throw new AssertionError("Fishing timer was hidden or animated as damage: "+name);
                    if(!entity.getCustomName().getString().equals(name))throw new AssertionError("Fishing timer text was changed");
                }
                for(String name:List.of("36","✧1,234,567✧","1.2M⚔")){
                    entity.setCustomName(Component.literal(name));renderer.extractRenderState(entity,state,0);
                    if(!((DamageRenderState)state).customcosmetics$damageName())throw new AssertionError("Combat label stopped working: "+name);
                }
                System.out.println("FISHING_COUNTDOWN_METADATA_AND_RENDER_PRESERVED_COMBAT_STILL_CAPTURED_OK");
                for(int i=0;i<2200;i++){var hit=new ArmorStand(client.level,0,-60,4);hit.setId(-19000-i);hit.setInvisible(true);hit.setCustomName(Component.literal("✧123✧"));DamageSplash.capture(hit);}
                if(size("LABELS")>256||size("SEEN")>2048)throw new AssertionError("Damage queues grew without bounds");
                ConfigManager.damageSplashEnabled=false;DamageSplash.tick(client);
                entity.setCustomName(Component.literal("✧123✧"));fixtureWorld=false;ConfigManager.damageSplashEnabled=true;DamageSplash.tick(client);
                if(DamageSplash.capture(entity))throw new AssertionError("Non-SkyBlock world was modified");
                ConfigManager.damageSplashEnabled=false;
            });
            checkSearch(context);checkLocationTracers(context);SharingVisualsGameTest.check(context,world.getServer()::runCommand);
            context.runOnClient(client->{ConfigManager.damageSplashDuration=1.2;ConfigManager.damageSplashStartColor=0xFFFF55;ConfigManager.damageSplashEndColor=0xFF5555;});
            for(String style:DamageAnimation.STYLES){
                context.runOnClient(client->{ConfigManager.damageSplashStyle=style;ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.DAMAGE));});
                context.waitTicks(2);context.takeScreenshot("damage-preview-"+style);
            }
            context.getInput().resizeWindow(854,480);context.waitTicks(3);context.takeScreenshot("damage-preview-narrow-search");
            checkSearch(context);
            context.runOnClient(client->{
                var screen=new CosmeticsScreen(null,CosmeticsScreen.Page.DAMAGE);ClientUiAccess.setScreen(client,screen);
                try{var max=CosmeticsScreen.class.getDeclaredMethod("maxScroll");max.setAccessible(true);var scroll=CosmeticsScreen.class.getDeclaredField("scroll");scroll.setAccessible(true);scroll.setDouble(screen,(Double)max.invoke(screen));}catch(Exception error){throw new AssertionError(error);}
            });context.waitTicks(3);context.takeScreenshot("damage-footer-thanks-to-baity");
            context.runOnClient(client->{ClientUiAccess.setScreen(client,null);ConfigManager.damageSplashEnabled=false;fixtureWorld=false;});
            System.out.println("DAMAGE_METADATA_DEDUP_RESTORE_BOUNDS_GPU_AND_SEARCH_OK");
        }catch(Exception error){throw new AssertionError(error);}finally{fixtureWorld=false;}
        context.runOnClient(client->DamageSplash.tick(client));
        new PartyGameTest().runTest(context);
    }
    private static void checkSearch(ClientGameTestContext context){
        context.runOnClient(client->ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SIZE)));
        typeSearch(context,"location tracer");
        context.runOnClient(client->{
            var screen=ClientUiAccess.screen(client);
            if(screen.children().stream().noneMatch(child->child instanceof PinkUi.Action action&&action.getMessage().getString().equals("Location Tracers")))throw new AssertionError("Global search did not index a remote page");
        });
        click(context,"Location Tracers");
        context.runOnClient(client->{
            var screen=ClientUiAccess.screen(client);var toggle=screen.children().stream().filter(child->child instanceof PinkUi.Toggle item&&item.getMessage().getString().startsWith("Location Tracers")).map(PinkUi.Toggle.class::cast).findFirst().orElseThrow();
            if(!toggle.visible||toggle.getY()<((Number)field(screen,"viewTop")).intValue())throw new AssertionError("Search did not scroll to its setting");
        });
        typeSearch(context,"伤害 大小");context.takeScreenshot("settings-search-chinese-damage-size");click(context,"Size");
        context.runOnClient(client->{if(!field(ClientUiAccess.screen(client),"page").toString().equals("DAMAGE"))throw new AssertionError("Search went to the wrong size setting");});
        typeSearch(context,"no_matching_setting_123");
        context.runOnClient(client->{if(ClientUiAccess.screen(client).children().stream().anyMatch(child->child instanceof PinkUi.Action action&&action.getMessage().getString().equals("Location Tracers")))throw new AssertionError("Old search results remained");});
        typeSearch(context,"鎏金大小");
        context.runOnClient(client->{if(ClientUiAccess.screen(client).children().stream().noneMatch(child->child instanceof PinkUi.Action action&&action.getMessage().getString().equals("Shimmer Size")))throw new AssertionError("Conditional shimmer settings were not indexed");});
        typeSearch(context,"预设");click(context,"Save Current Colors");
        context.runOnClient(client->{if(!field(ClientUiAccess.screen(client),"page").toString().equals("PRESETS"))throw new AssertionError("Preset controls were not indexed");});
        typeSearch(context,"jigsaw");click(context,"Blocking Style");
        context.runOnClient(client->{if(!field(ClientUiAccess.screen(client),"page").toString().equals("ANIMATIONS"))throw new AssertionError("Blocking dropdown was not indexed");});
    }
    private static void typeSearch(ClientGameTestContext context,String value){
        var point=new AtomicReference<double[]>();
        context.runOnClient(client->{var screen=ClientUiAccess.screen(client);var search=(PinkUi.Field)screen.children().stream().filter(child->child instanceof PinkUi.Field item&&item.getMessage().getString().equals("Search Settings")).findFirst().orElseThrow();search.setValue("");double scale=client.getWindow().getGuiScale();point.set(new double[]{(search.getX()+20)*scale,(search.getY()+10)*scale});});
        context.getInput().setCursorPos(point.get()[0],point.get()[1]);context.getInput().pressMouse(ClientInput.MOUSE_LEFT);context.getInput().typeChars(value);context.waitTicks(3);
    }
    private static void click(ClientGameTestContext context,String title){
        var point=new AtomicReference<double[]>();context.runOnClient(client->{var button=ClientUiAccess.screen(client).children().stream().filter(child->child instanceof PinkUi.Action action&&action.getMessage().getString().equals(title)).map(PinkUi.Action.class::cast).findFirst().orElseThrow();double scale=client.getWindow().getGuiScale();point.set(new double[]{(button.getX()+button.getWidth()/2.0)*scale,(button.getY()+button.getHeight()/2.0)*scale});});
        context.getInput().setCursorPos(point.get()[0],point.get()[1]);context.getInput().pressMouse(ClientInput.MOUSE_LEFT);context.waitTicks(3);
    }
    private static void checkLocationTracers(ClientGameTestContext context){
        context.runOnClient(client->{
            var camera=new net.minecraft.client.renderer.state.level.CameraRenderState();camera.pos=client.player.position();camera.orientation=new org.joml.Quaternionf();camera.depthFar=256;
            var calls=new AtomicInteger();
            var queue=(net.minecraft.client.renderer.SubmitNodeCollector)java.lang.reflect.Proxy.newProxyInstance(DamageSplashGameTest.class.getClassLoader(),new Class<?>[]{net.minecraft.client.renderer.SubmitNodeCollector.class},(proxy,method,args)->{if(method.getName().equals("submitCustomGeometry"))calls.incrementAndGet();return null;});
            var target=new PartyHighlight.Target(UUID.randomUUID(),"SharedPlayer",new AABB(2,-60,4,2.6,-58.2,4.6),false,false);
            ConfigManager.partyTracersEnabled=true;ConfigManager.locationTracersEnabled=false;PartyHighlight.submit(target,camera,queue,client.font);if(calls.get()!=0)throw new AssertionError("Party toggle enabled location tracers");
            ConfigManager.partyTracersEnabled=false;ConfigManager.locationTracersEnabled=true;PartyHighlight.submit(target,camera,queue,client.font);if(calls.get()!=1)throw new AssertionError("Location tracer was not submitted");
            ConfigManager.locationTracersEnabled=false;
        });
    }
    private static int size(String name){return name.equals("SEEN")?((Map<?,?>)field(null,name)).size():((Collection<?>)field(null,name)).size();}
    private static Object field(Object owner,String name){try{var f=(owner==null?DamageSplash.class:CosmeticsScreen.class).getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new AssertionError(error);}}
    private static int pinkPixels(ClientGameTestContext context){
        var count=new CompletableFuture<Integer>();context.runOnClient(client->Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(),image->{try(image){int n=0;for(int y=100;y<620;y++)for(int x=260;x<1020;x++){int rgb=image.getPixel(x,y);if((rgb>>>16&255)>170&&(rgb&255)>170&&(rgb>>>8&255)<90)n++;}count.complete(n);}catch(Throwable error){count.completeExceptionally(error);}}));context.waitFor(client->count.isDone());return count.join();
    }
}
