package com.customcosmetics.gametest;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.party.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Screenshot;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Small standalone render test for the differing 26.1/26.2/26.3 graphics APIs. */
public final class ChamsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.partyEspEnabled=false;ConfigManager.partyChatEnabled=false;ConfigManager.sharingChamsEnabled=false;});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("time set noon");world.getServer().runCommand("weather clear");context.waitTicks(15);
            world.getServer().runCommand("tp @a 0.5 -60 -0.5 0 0");context.getInput().resizeWindow(1280,720);context.waitTicks(8);
            var a=UUID.fromString("32345678-1234-4234-8234-123456789abc");var b=UUID.fromString("42345678-1234-4234-8234-123456789abc");
            context.runOnClient(client->{try{
                var roster=PartyFeatures.class.getDeclaredField("roster");roster.setAccessible(true);roster.set(null,new PartyRoster(Set.of(a,b),Set.of(),"fixture"));
                var left=SharingModelFixture.player(client,a,"PartyMate");left.setId(-24101);left.setPos(-1.2,-60,4.5);client.level.addEntity(left);
                var right=SharingModelFixture.player(client,b,"NativeMate");right.setId(-24102);right.setPos(1.2,-60,4.5);client.level.addEntity(right);
                ConfigManager.offlineMode=false;ConfigManager.partyMaxDisplayDistance=0;
            }catch(Exception error){throw new AssertionError(error);}});
            world.getServer().runCommand("fill -4 -60 2 4 -55 2 stone");context.waitTicks(5);int disabled=pixels(context,0);
            context.runOnClient(client->{ConfigManager.sharingChamsEnabled=true;if(!SharingChams.applies(-24101))throw new AssertionError("Shared player was excluded");});context.waitTicks(5);
            int visible=pixels(context,0),cape=pixels(context,1),armor=pixels(context,2);context.takeScreenshot("textured-chams-skin-cape-armor-through-wall");
            if(visible<disabled+400||cape<80||armor<80)throw new AssertionError("Textured chams missing: "+disabled+"/"+visible+"/"+cape+"/"+armor);
            context.runOnClient(client->{for(int id:new int[]{-24101,-24102}){var player=(net.minecraft.client.player.RemotePlayer)client.level.getEntity(id);player.setYRot(180);player.yBodyRot=180;player.yBodyRotO=180;player.yHeadRot=180;player.yHeadRotO=180;}});
            context.waitTicks(8);int front=pixels(context,1);context.takeScreenshot("textured-chams-front-occludes-cape");
            if(front>cape*.3)throw new AssertionError("Cape showed through the front of the body");
            context.runOnClient(client->ConfigManager.sharingChamsEnabled=false);context.waitTicks(4);if(pixels(context,0)>disabled+100)throw new AssertionError("Disabled chams remained visible");
            context.runOnClient(client->{ConfigManager.offlineMode=true;});
            System.out.println("TEXTURED_CHAMS_SKIN_CAPE_ARMOR_WALL_AND_SELF_OCCLUSION_OK "+visible+"/"+cape+"/"+armor+"/"+front);
        }catch(Exception error){throw new AssertionError(error);}
    }
    private static int pixels(ClientGameTestContext context,int kind){
        var result=new CompletableFuture<Integer>();context.runOnClient(client->{try{
            Object target;try{target=client.gameRenderer.getClass().getMethod("mainRenderTarget").invoke(client.gameRenderer);}catch(NoSuchMethodException older){target=client.getClass().getMethod("getMainRenderTarget").invoke(client);}
            Screenshot.takeScreenshot((com.mojang.blaze3d.pipeline.RenderTarget)target,image->{try(image){int n=0;for(int y=100;y<610;y++)for(int x=100;x<1180;x++){int color=image.getPixel(x,y),r=color>>>16&255,g=color>>>8&255,b=color&255;if(switch(kind){case 1->g>100&&g>r*1.35&&g>b*1.35;case 2->b>110&&g>110&&r<100;default->r>90&&b>90&&r>g*1.3&&b>g*1.2;})n++;}result.complete(n);}catch(Throwable error){result.completeExceptionally(error);}});
        }catch(Exception error){result.completeExceptionally(error);}});context.waitFor(client->result.isDone());return result.join();
    }
}
