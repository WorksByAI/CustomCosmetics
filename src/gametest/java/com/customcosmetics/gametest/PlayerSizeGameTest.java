// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.playersize.*;
import com.customcosmetics.features.smolpeople.SmolPeopleNametag;
import com.customcosmetics.gui.*;
import com.customcosmetics.sync.PresenceSync;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.player.RemotePlayer;

public final class PlayerSizeGameTest implements FabricClientGameTest {
    public static final Map<Integer,float[]> observed=new ConcurrentHashMap<>();
    private static final UUID REMOTE=UUID.fromString("12345678-1234-4234-8234-123456789abc");
    private static final int REMOTE_ID=-12150;
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.offlineMode=false;ConfigManager.smolpeopleMode=false;ConfigManager.playerSizeEnabled=false;ConfigManager.capeEnabled=false;ConfigManager.nickTweaksEnabled=false;});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("time set noon");world.getServer().runCommand("weather clear");
            context.waitTicks(10);context.getInput().resizeWindow(1600,1000);
            var self=new AtomicReference<Integer>();var physical=new AtomicReference<float[]>();var normal=new AtomicReference<float[]>();
            context.runOnClient(client->{client.options.guiScale().set(2);self.set(client.player.getId());physical.set(new float[]{client.player.getBbWidth(),client.player.getBbHeight()});ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SIZE));});
            context.waitTicks(4);normal.set(observed.get(self.get()));if(normal.get()==null)throw new AssertionError("Player Size preview did not reach the real model renderer");
            NicknamePresetGameTest.click(context,"Enabled",PinkUi.Toggle.class);
            type(context,"Size X value","0.5");type(context,"Size Y value","0.75");type(context,"Size Z value","1.25");context.waitTicks(3);
            ratio(observed.get(self.get()),normal.get(),.5,.75,1.25);
            context.runOnClient(client->{
                if(!ConfigManager.playerSizeEnabled||ConfigManager.playerSizeX!=.5||ConfigManager.playerSizeY!=.75||ConfigManager.playerSizeZ!=1.25)throw new AssertionError("Real GUI controls did not update each axis");
                if(client.player.getBbWidth()!=physical.get()[0]||client.player.getBbHeight()!=physical.get()[1])throw new AssertionError("Visual player scaling altered the physical entity");
                double offset=SmolPeopleNametag.getVerticalOffset(self.get());if(Math.abs(offset-physical.get()[1]*(-.25))>1e-5)throw new AssertionError("Name tag did not follow scaled model height");
            });context.takeScreenshot("player-size-local-xyz");
            context.runOnClient(client->ConfigManager.smolpeopleMode=true);context.waitTicks(3);ratio(observed.get(self.get()),normal.get(),.25,.375,.625);
            context.takeScreenshot("player-size-composed-with-smolpeople");
            NicknamePresetGameTest.click(context,"Reset Player Size",PinkUi.Action.class);context.waitTicks(2);
            context.runOnClient(client->{if(ConfigManager.playerSizeX!=1||ConfigManager.playerSizeY!=1||ConfigManager.playerSizeZ!=1)throw new AssertionError("Reset did not restore neutral XYZ values");ConfigManager.smolpeopleMode=false;ConfigManager.playerSizeEnabled=false;
                var remote=new RemotePlayer(client.level,new com.mojang.authlib.GameProfile(REMOTE,"Alice"));remote.setId(REMOTE_ID);remote.setPos(client.player.getX(),client.player.getY(),client.player.getZ()+4);client.level.addEntity(remote);client.player.setYRot(0);client.player.setXRot(0);ClientUiAccess.setScreen(client,null);
            });context.waitTicks(8);var remoteNormal=observed.get(REMOTE_ID);if(remoteNormal==null)throw new AssertionError("Remote test player did not reach the real renderer");
            context.runOnClient(client->receive(new PlayerSizeSettings(true,.6,1.4,1.2)));context.waitTicks(4);ratio(observed.get(REMOTE_ID),remoteNormal,.6,1.4,1.2);
            context.takeScreenshot("player-size-synced-remote");
            context.runOnClient(client->{ConfigManager.offlineMode=true;});context.waitTicks(3);ratio(observed.get(REMOTE_ID),remoteNormal,1,1,1);
            context.runOnClient(client->{ConfigManager.offlineMode=false;receive(PlayerSizeSettings.NORMAL);});context.waitTicks(3);ratio(observed.get(REMOTE_ID),remoteNormal,1,1,1);
            context.runOnClient(client->{client.level.removeEntity(REMOTE_ID,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);ConfigManager.playerSizeEnabled=false;ClientUiAccess.setScreen(client,null);});
            System.out.println("PLAYER_SIZE_GUI_REAL_MODEL_XYZ_REMOTE_SYNC_OFFLINE_AND_SMOL_COMPOSITION_OK");
        }catch(Exception failure){throw new AssertionError(failure);}
    }
    private static void type(ClientGameTestContext context,String name,String value)throws Exception{
        NicknamePresetGameTest.click(context,name,PinkUi.Field.class);
        context.runOnClient(client->{var field=ClientUiAccess.screen(client).children().stream().filter(PinkUi.Field.class::isInstance).map(PinkUi.Field.class::cast).filter(widget->widget.getMessage().getString().equals(name)).findFirst().orElseThrow();if(!field.isFocused())throw new AssertionError("Size field did not receive focus");field.setValue("");});
        context.getInput().typeChars(value);context.waitTicks(2);
    }
    private static void ratio(float[] measured,float[] baseline,double x,double y,double z){
        if(measured==null||baseline==null)throw new AssertionError("Missing native player scale measurement");
        double[] expected={x,y,z};for(int i=0;i<3;i++)if(Math.abs(measured[i]/baseline[i]-expected[i])>.002)throw new AssertionError("Native axis "+i+" scale differs: "+Arrays.toString(measured)+" baseline="+Arrays.toString(baseline)+" expected="+Arrays.toString(expected));
    }
    private static void receive(PlayerSizeSettings settings){try{
        var user=new JsonObject();user.addProperty("name","Alice");var features=new JsonObject();features.add("playerSize",settings.json());user.add("features",features);
        var users=new JsonObject();users.add(REMOTE.toString(),user);var root=new JsonObject();root.addProperty("authVersion",2);root.add("users",users);
        var apply=PresenceSync.class.getDeclaredMethod("applyPayload",String.class,boolean.class,boolean.class);apply.setAccessible(true);apply.invoke(null,root.toString(),false,true);
    }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
}
