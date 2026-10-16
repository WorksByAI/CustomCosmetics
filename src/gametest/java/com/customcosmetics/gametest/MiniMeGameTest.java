// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.accessories.*;
import com.customcosmetics.gui.*;
import com.customcosmetics.sync.PresenceSync;
import com.google.gson.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.PlayerModelType;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Native skin/UV, wide/slim, preview, remote sync, offline and deferred-pose checks. */
public final class MiniMeGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{client.options.guiScale().set(2);ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.partyEspEnabled=false;ConfigManager.partyChatEnabled=false;ConfigManager.sharingChamsEnabled=false;ConfigManager.smolpeopleMode=false;ConfigManager.playerSizeEnabled=false;ConfigManager.accessories=AccessoriesSettings.NONE;});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("time set noon");world.getServer().runCommand("weather clear");context.waitTicks(15);
            world.getServer().runCommand("tp @a 0.5 -60 -0.5 0 0");context.getInput().resizeWindow(1280,720);context.waitTicks(8);
            var settings=AccessoriesSettings.NONE.with("pet",AccessoriesSettings.NONE.part("pet").enabled(true).style(MiniMePet.ID));
            var a=UUID.fromString("52345678-1234-4234-8234-123456789abc");var b=UUID.fromString("62345678-1234-4234-8234-123456789abc");
            context.runOnClient(client->{
                var left=player(client,a,"WideMate",false,0xFFEE33CC);left.setId(-25101);left.setPos(-1.6,-60,5.5);client.level.addEntity(left);
                var right=player(client,b,"SlimMate",true,0xFF22DDFF);right.setId(-25102);right.setPos(1.2,-60,5.5);client.level.addEntity(right);
                left.setYRot(180);left.yBodyRot=left.yBodyRotO=left.yHeadRot=left.yHeadRotO=180;
                right.setYRot(180);right.yBodyRot=right.yBodyRotO=right.yHeadRot=right.yHeadRotO=180;
                ConfigManager.offlineMode=false;receive(Map.of(a,settings,b,settings));
                verifySnapshot(client);
            });context.waitTicks(8);int baseline=pixels(context),wide=pixels(context,1),slim=pixels(context,2);
            context.takeScreenshot("mini-me-two-owner-skins-wide-and-slim");
            context.runOnClient(client->receive(Map.of()));context.waitTicks(5);int without=pixels(context);
            if(baseline<without+160||wide<pixels(context,1)+60||slim<pixels(context,2)+60)throw new AssertionError("Mini-Me did not preserve both owners' skins: "+baseline+" / "+without);
            context.runOnClient(client->receive(Map.of(a,settings,b,settings)));context.waitTicks(4);
            context.runOnClient(client->ConfigManager.offlineMode=true);context.waitTicks(4);if(pixels(context)>without+150)throw new AssertionError("Remote pets remained visible offline");
            context.runOnClient(client->{ConfigManager.accessories=settings;ClientUiAccess.setScreen(client,new WardrobeScreen(null,"pet"));});context.waitTicks(5);
            context.runOnClient(client->{var screen=ClientUiAccess.screen(client);var labels=screen.children().stream().filter(PinkUi.Action.class::isInstance).map(PinkUi.Action.class::cast).map(w->w.getMessage().getString()).toList();if(!labels.contains("Mini-Me"))throw new AssertionError("Mini-Me is missing from the Pets selector");if(screen.children().stream().anyMatch(PinkUi.Field.class::isInstance))throw new AssertionError("Mini-Me should use its skin, not recolor controls");});
            context.takeScreenshot("mini-me-seated-right-shoulder-preview");
            context.runOnClient(client->{var button=ClientUiAccess.screen(client).children().stream().filter(PinkUi.Action.class::isInstance).map(PinkUi.Action.class::cast).filter(w->w.getMessage().getString().equals("Right Shoulder")).findFirst().orElseThrow();button.onClick(new net.minecraft.client.input.MouseButtonEvent(button.getX()+2,button.getY()+2,new net.minecraft.client.input.MouseButtonInfo(0,0)),false);if(!ConfigManager.accessories.part("pet").side().equals("left"))throw new AssertionError("Pet side button did not save left shoulder");});context.waitTicks(4);context.takeScreenshot("mini-me-seated-left-shoulder-preview");
            context.runOnClient(client->{ConfigManager.accessories=AccessoriesSettings.parse(settings.json());if(!ConfigManager.accessories.part("pet").style().equals(MiniMePet.ID))throw new AssertionError("Mini-Me save round trip failed");ClientUiAccess.setScreen(client,null);ConfigManager.offlineMode=true;});
            System.out.println("MINI_ME_NATIVE_OWNER_SKINS_WIDE_SLIM_PREVIEW_REMOTE_SYNC_OFFLINE_AND_SNAPSHOT_OK "+baseline+"/"+without);
        }catch(Exception error){throw new AssertionError(error);}
    }
    private static RemotePlayer player(net.minecraft.client.Minecraft client,UUID uuid,String name,boolean slim,int color){
        var id=net.minecraft.resources.Identifier.fromNamespaceAndPath("customcosmetics","mini-me-fixture-"+name.toLowerCase(Locale.ROOT));
        var image=new com.mojang.blaze3d.platform.NativeImage(64,64,false);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setPixel(x,y,0);
        fill(image,0,0,32,16,0xFF553526);fill(image,8,8,8,8,0xFFF2BD8D);
        fill(image,10,11,1,1,0xFF292337);fill(image,13,11,1,1,0xFF292337);fill(image,11,14,3,1,0xFF9B6553);
        fill(image,16,16,24,16,color);fill(image,40,16,16,16,0xFFF2BD8D);fill(image,44,20,4,4,color);
        fill(image,0,16,16,16,0xFF29283F);fill(image,16,48,16,16,0xFF29283F);
        fill(image,32,48,16,16,0xFFF2BD8D);fill(image,36,52,4,4,color);
        fill(image,20,39,8,2,0xFFEFEAF4); // Jacket stripe verifies the skin's outer layer.
        var texture=new net.minecraft.core.ClientAsset.Texture(){public net.minecraft.resources.Identifier id(){return id;}public net.minecraft.resources.Identifier texturePath(){return id;}};
        client.getTextureManager().register(id,new net.minecraft.client.renderer.texture.DynamicTexture(()->name,image));
        var skin=new net.minecraft.world.entity.player.PlayerSkin(texture,null,null,slim?PlayerModelType.SLIM:PlayerModelType.WIDE,true);
        return new RemotePlayer(client.level,new com.mojang.authlib.GameProfile(uuid,name)){
            @Override public net.minecraft.world.entity.player.PlayerSkin getSkin(){return skin;}
            @Override public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part){return true;}
        };
    }
    private static void fill(com.mojang.blaze3d.platform.NativeImage image,int x,int y,int width,int height,int color){for(int py=y;py<y+height;py++)for(int px=x;px<x+width;px++)image.setPixel(px,py,color);}
    private static void verifySnapshot(net.minecraft.client.Minecraft client){
        var owner=new AvatarRenderState();owner.id=client.player.getId();owner.skin=client.player.getSkin();owner.ageInTicks=42;owner.walkAnimationPos=4;owner.walkAnimationSpeed=.8f;owner.showHat=true;owner.showLeftSleeve=true;owner.isCrouching=true;owner.isUsingItem=true;owner.outlineColor=0xFFFFFF;
        var mini=MiniMePet.snapshot(owner);if(mini==owner||mini.skin!=owner.skin||mini.id==owner.id||mini.isCrouching||mini.isUsingItem||!mini.isPassenger||mini.outlineColor!=0||!mini.showHat||!mini.showLeftSleeve||mini.walkAnimationSpeed!=0)throw new AssertionError("Companion snapshot leaked owner state or lost its seated pose");
        mini.walkAnimationPos=8;if(owner.walkAnimationPos!=4||!owner.isCrouching)throw new AssertionError("Mini-Me changed the real owner snapshot");
    }
    private static void receive(Map<UUID,AccessoriesSettings> settings){try{
        var users=new JsonObject();settings.forEach((id,value)->{var user=new JsonObject();user.addProperty("name",id.toString().startsWith("5")?"WideMate":"SlimMate");var features=new JsonObject();features.add("accessories",value.json());user.add("features",features);users.add(id.toString(),user);});
        var root=new JsonObject();root.addProperty("authVersion",2);root.add("users",users);var apply=PresenceSync.class.getDeclaredMethod("applyPayload",String.class,boolean.class,boolean.class);apply.setAccessible(true);apply.invoke(null,root.toString(),false,true);
    }catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static int pixels(ClientGameTestContext context){return pixels(context,0);}
    private static int pixels(ClientGameTestContext context,int kind){
        var result=new CompletableFuture<Integer>();context.runOnClient(client->{try{
            Object target;try{target=client.gameRenderer.getClass().getMethod("mainRenderTarget").invoke(client.gameRenderer);}catch(NoSuchMethodException old){target=client.getClass().getMethod("getMainRenderTarget").invoke(client);}
            Screenshot.takeScreenshot((com.mojang.blaze3d.pipeline.RenderTarget)target,image->{try(image){int n=0;for(int y=100;y<590;y++)for(int x=140;x<1120;x++){int c=image.getPixel(x,y),r=c>>>16&255,g=c>>>8&255,b=c&255;if(b>80&&(kind!=2&&r>g*1.5&&r>90||kind!=1&&g>r*1.8&&g>90))n++;}result.complete(n);}catch(Throwable error){result.completeExceptionally(error);}});
        }catch(Exception error){result.completeExceptionally(error);}});context.waitFor(client->result.isDone());return result.join();
    }
}
