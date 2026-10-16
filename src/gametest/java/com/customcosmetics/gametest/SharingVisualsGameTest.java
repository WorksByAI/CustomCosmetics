package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.*;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.party.*;
import com.customcosmetics.gui.CosmeticsScreen;
import com.customcosmetics.sync.PartyClient;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Original model geometry through a real wall, plus level/color/icon data from local TAB. */
final class SharingVisualsGameTest {
    private static final UUID PARTY=UUID.fromString("32345678-1234-4234-8234-123456789abc"),SHARED=UUID.fromString("42345678-1234-4234-8234-123456789abc"),OUTSIDE=UUID.fromString("52345678-1234-4234-8234-123456789abc");
    static void check(ClientGameTestContext context,Consumer<String> command)throws Exception{
        var previousRoster=new AtomicReference<PartyRoster>();
        context.runOnClient(client->{
            ConfigManager.partyNametagStyle="simple";ConfigManager.offlineMode=false;
            ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.PARTY));
        });
        PartyGameTest.clickSetting(context,"Nametag Style: Simple");
        context.runOnClient(client->{if(!ConfigManager.partyNametagStyle.equals("vanilla"))throw new AssertionError("Nametag style button did not change the local setting");ClientUiAccess.setScreen(client,null);});
        context.runOnClient(client->{try{
            var profile=new GameProfile(SHARED,"NativeMate");var info=new PlayerInfo(profile,true);
            info.setTabListDisplayName(tag("NativeMate"));
            var map=client.getConnection().getClass().getDeclaredField("playerInfoMap");map.setAccessible(true);var entries=(Map<UUID,PlayerInfo>)map.get(client.getConnection());entries.put(SHARED,info);
            var result=SharingNametag.label(client,SHARED,"NativeMate");assertTag(result,"[483] NativeMate ♣");
            info.setTabListDisplayName(Component.literal("[584] ").withStyle(ChatFormatting.RED).append(Component.literal("NativeMate ✓").withStyle(ChatFormatting.AQUA)));
            if(!SharingNametag.label(client,SHARED,"NativeMate").getString().equals("[584] NativeMate ✓"))throw new AssertionError("A changed TAB display name was not refreshed");
            info.setTabListDisplayName(tag("NativeMate"));SharingNametag.label(client,SHARED,"NativeMate");entries.remove(SHARED);
            assertTag(SharingNametag.label(client,SHARED,"NativeMate"),"[483] NativeMate ♣");
            if(!SharingNametag.label(client,OUTSIDE,"OriginalID").getString().equals("OriginalID"))throw new AssertionError("Unknown TAB profile did not fall back to the account ID");
            var syntheticId=UUID.fromString("62345678-1234-4234-8234-123456789abc");
            var synthetic=new PlayerInfo(new GameProfile(syntheticId,"!tab-row"),true);
            synthetic.setTabListDisplayName(Component.literal("[584] ").withStyle(ChatFormatting.RED).append(Component.literal("NativeMate ✓").withStyle(ChatFormatting.AQUA)));entries.put(syntheticId,synthetic);
            var roster=PartyFeatures.class.getDeclaredField("roster");roster.setAccessible(true);previousRoster.set((PartyRoster)roster.get(null));roster.set(null,new PartyRoster(Set.of(PARTY),Set.of(),"fixture"));
            PartyGameTest.sharedWorld=true;ConfigManager.positionSharingReceive=true;ConfigManager.partyShowNames=false;ConfigManager.partyEspEnabled=false;ConfigManager.partyTracersEnabled=false;ConfigManager.partyNametagHideDistance=0;
            PartyGameTest.remote=Map.of(SHARED,new PartyClient.Teammate(SHARED,"NativeMate",new PartyClient.Position(.5,-60,30,.6,1.8),Long.MAX_VALUE));
        }catch(Exception error){throw new AssertionError(error);}});
        context.waitTicks(13);
        context.runOnClient(client->{try{
            if(!SharingNametag.label(client,SHARED,"NativeMate").getString().equals("[584] NativeMate ✓"))throw new AssertionError("Synthetic TAB row did not refresh the original account nametag");
            var map=client.getConnection().getClass().getDeclaredField("playerInfoMap");map.setAccessible(true);((Map<?,?>)map.get(client.getConnection())).remove(UUID.fromString("62345678-1234-4234-8234-123456789abc"));
        }catch(Exception error){throw new AssertionError(error);}});
        context.takeScreenshot("sharing-vanilla-nametag-tab-level-colors-icon");
        command.accept("fill -4 -60 2 4 -55 2 stone");context.waitTicks(3);
        context.runOnClient(client->{
            var party=SharingModelFixture.player(client,PARTY,"PartyMate");party.setId(-24101);party.setPos(-1.2,-60,4.5);client.level.addEntity(party);
            var shared=SharingModelFixture.player(client,SHARED,"NativeMate");shared.setId(-24102);shared.setPos(1.2,-60,4.5);client.level.addEntity(shared);
            var outsider=new RemotePlayer(client.level,new GameProfile(OUTSIDE,"Unshared"));outsider.setId(-24103);outsider.setPos(3.5,-60,4.5);client.level.addEntity(outsider);
            PartyGameTest.remote=Map.of(SHARED,new PartyClient.Teammate(SHARED,"NativeMate",new PartyClient.Position(1.2,-60,4.5,.6,1.8),Long.MAX_VALUE));
            ConfigManager.partyNametagStyle="simple";ConfigManager.partyFarMarkers=false;ConfigManager.sharingChamsEnabled=false;
        });
        context.waitTicks(3);int disabled=pixels(context,0);context.takeScreenshot("sharing-chams-disabled-wall");
        context.runOnClient(client->{
            ConfigManager.sharingChamsEnabled=true;
            if(!SharingChams.applies(-24101)||!SharingChams.applies(-24102)||SharingChams.applies(-24103)||SharingChams.applies(client.player.getId()))throw new AssertionError("Chams scope included an unshared player or excluded a shared player");
            ConfigManager.partyMaxDisplayDistance=3;if(SharingChams.applies(-24101))throw new AssertionError("Chams ignored maximum distance");ConfigManager.partyMaxDisplayDistance=0;
            ConfigManager.offlineMode=true;if(SharingChams.applies(-24101))throw new AssertionError("Offline mode retained other players' chams");ConfigManager.offlineMode=false;
            client.level.getEntity(-24101).setInvisible(true);if(SharingChams.applies(-24101))throw new AssertionError("Invisible player was exposed");client.level.getEntity(-24101).setInvisible(false);
        });
        context.waitTicks(4);int visible=pixels(context,0);context.takeScreenshot("sharing-chams-real-models-through-wall");
        if(visible<disabled+400)throw new AssertionError("Player-model chams did not render through the wall: "+disabled+" -> "+visible);
        int cape=pixels(context,1),armor=pixels(context,2);
        if(cape<80||armor<80)throw new AssertionError("Original cape/armor textures missing through wall: "+cape+"/"+armor);
        context.takeScreenshot("sharing-chams-textured-skin-cape-armor-through-wall");
        context.runOnClient(client->{for(int id:new int[]{-24101,-24102}){var player=(RemotePlayer)client.level.getEntity(id);player.setYRot(180);player.yBodyRot=180;player.yBodyRotO=180;player.yHeadRot=180;player.yHeadRotO=180;}});
        context.waitTicks(8);int frontCape=pixels(context,1);context.takeScreenshot("sharing-chams-front-body-occludes-cape");
        if(frontCape>cape*.3)throw new AssertionError("Cape showed through the player's front: "+cape+" -> "+frontCape);
        command.accept("fill -4 -60 2 4 -55 2 air");context.waitTicks(5);
        int unobstructed=pixels(context,0);context.takeScreenshot("sharing-chams-original-appearance-without-wall");
        context.runOnClient(client->ConfigManager.sharingChamsEnabled=false);context.waitTicks(4);int original=pixels(context,0);
        if(original<400||Math.abs(original-unobstructed)>original*.25)throw new AssertionError("Chams changed the original visible skin: "+original+" -> "+unobstructed);
        context.runOnClient(client->{try{
            if(client.level.getEntity(-24101)==null||client.level.getEntity(-24102)==null||client.level.getEntity(-24103)==null)throw new AssertionError("Chams removed an entity");
            for(int id:new int[]{-24101,-24102,-24103})client.level.removeEntity(id,Entity.RemovalReason.DISCARDED);
            var roster=PartyFeatures.class.getDeclaredField("roster");roster.setAccessible(true);roster.set(null,previousRoster.get());
            ConfigManager.sharingChamsEnabled=false;
            ConfigManager.partyNametagStyle="simple";ConfigManager.positionSharingReceive=false;ConfigManager.offlineMode=true;ConfigManager.partyShowNames=true;ConfigManager.partyFarMarkers=true;ConfigManager.partyNametagHideDistance=8;
            PartyGameTest.sharedWorld=false;PartyGameTest.remote=Map.of();
        }catch(Exception error){throw new AssertionError(error);}});
        command.accept("fill -4 -60 2 4 -55 2 air");context.waitTicks(3);
        System.out.println("SHARING_VANILLA_TAB_CACHE_TEXTURED_CHAMS_SKIN_CAPE_ARMOR_SELF_DEPTH_GPU_OK disabled="+disabled+" visible="+visible+" cape="+cape+" armor="+armor+" frontCape="+frontCape);
    }
    private static Component tag(String name){return Component.literal("[483] ").withStyle(ChatFormatting.DARK_RED).append(Component.literal(name).withStyle(ChatFormatting.AQUA)).append(Component.literal(" ♣").withStyle(ChatFormatting.GOLD));}
    private static void assertTag(Component text,String expected){
        if(!text.getString().equals(expected))throw new AssertionError("Name, level or icon was lost: "+text.getString());
        var colors=new HashSet<Integer>();text.visit((style,value)->{if(style.getColor()!=null)colors.add(style.getColor().getValue());return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
        if(!colors.containsAll(Set.of(0xAA0000,0x55FFFF,0xFFAA00)))throw new AssertionError("Nametag component colors were flattened");
    }
    private static int pixels(ClientGameTestContext context,int kind){
        var count=new CompletableFuture<Integer>();context.runOnClient(client->Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(),image->{try(image){int n=0;for(int y=100;y<610;y++)for(int x=100;x<1180;x++){int c=image.getPixel(x,y),r=c>>>16&255,g=c>>>8&255,b=c&255;if(switch(kind){case 1->g>100&&g>r*1.35&&g>b*1.35;case 2->b>110&&g>110&&r<100;default->r>90&&b>90&&r>g*1.3&&b>g*1.2;})n++;}count.complete(n);}catch(Throwable error){count.completeExceptionally(error);}}));context.waitFor(client->count.isDone());return count.join();
    }
}
