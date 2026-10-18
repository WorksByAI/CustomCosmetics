package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.camera.Snaplook;
import com.customcosmetics.features.map.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.*;

public final class SnaplookGameTest implements FabricClientGameTest {
    private float yaw,pitch;
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;ConfigManager.snaplookEnabled=true;ConfigManager.snaplookPerspective="front";
            Snaplook.binding().setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(295));KeyMapping.resetMapping();});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("fill -5 63 -5 5 64 5 minecraft:stone");world.getServer().runCommand("tp @a .5 65 .5 60 15");context.waitTicks(20);
            context.runOnClient(client->{focus(client,true);yaw=client.player.getYRot();pitch=client.player.getXRot();});
            for(var baseline:CameraType.values()){
                context.runOnClient(client->client.options.setCameraType(baseline));hold(context);
                context.runOnClient(client->{check(Snaplook.active()&&client.options.getCameraType()==CameraType.THIRD_PERSON_FRONT,"Hold did not select rear view");check(client.player.getYRot()==yaw&&client.player.getXRot()==pitch,"Snaplook rotated the player");});
                release(context);context.runOnClient(client->check(!Snaplook.active()&&client.options.getCameraType()==baseline,"Release did not restore "+baseline));
            }
            context.runOnClient(client->{client.options.setCameraType(CameraType.FIRST_PERSON);ConfigManager.snaplookPerspective="back";});hold(context);
            context.runOnClient(client->check(client.options.getCameraType()==CameraType.THIRD_PERSON_BACK,"Configured back view ignored"));release(context);
            context.runOnClient(client->{client.options.setCameraType(CameraType.THIRD_PERSON_BACK);ConfigManager.snaplookPerspective="first";});hold(context);
            context.runOnClient(client->check(client.options.getCameraType()==CameraType.FIRST_PERSON,"Configured first-person view ignored"));release(context);
            context.runOnClient(client->{client.options.setCameraType(CameraType.FIRST_PERSON);ConfigManager.snaplookPerspective="front";});hold(context);context.takeScreenshot("snaplook-held-rear-view");
            context.runOnClient(client->ConfigManager.snaplookEnabled=false);context.waitTicks(2);
            context.runOnClient(client->{check(!Snaplook.active()&&client.options.getCameraType()==CameraType.FIRST_PERSON,"Disable did not restore");ConfigManager.snaplookEnabled=true;});context.waitTicks(2);
            context.runOnClient(client->check(!Snaplook.active(),"Re-enabling while held unexpectedly restarted Snaplook"));release(context);hold(context);
            context.runOnClient(client->focus(client,false));context.waitTicks(2);
            context.runOnClient(client->{check(!Snaplook.active()&&client.options.getCameraType()==CameraType.FIRST_PERSON,"Focus loss left rear view active");focus(client,true);});release(context);hold(context);
            context.runOnClient(client->ClientUiAccess.setScreen(client,new com.customcosmetics.gui.CosmeticsScreen(null,com.customcosmetics.gui.CosmeticsScreen.Page.SYNC)));context.waitTicks(2);
            context.runOnClient(client->{check(!Snaplook.active(),"Opening settings did not release Snaplook");ClientUiAccess.setScreen(client,null);});release(context);hold(context);
            context.runOnClient(client->{MapEditor.dungeonOnly=false;MapEditor.enabled=true;Freecam.setEnabled(true);check(!Snaplook.active()&&client.options.getCameraType()==CameraType.FIRST_PERSON,"Freecam did not take over cleanly");Freecam.stop(client);check(client.options.getCameraType()==CameraType.FIRST_PERSON,"Freecam saved the temporary Snaplook view");MapEditor.enabled=false;});release(context);
            context.runOnClient(client->client.options.setCameraType(CameraType.THIRD_PERSON_BACK));hold(context);
        }
        context.waitTicks(2);context.runOnClient(client->{check(!Snaplook.active()&&client.options.getCameraType()==CameraType.THIRD_PERSON_BACK,"Disconnect lost the original perspective");Snaplook.binding().setDown(false);});
        System.out.println("SNAPLOOK_NATIVE_KEY_HOLD_ALL_BASELINES_TARGETS_GUI_DISABLE_FOCUS_FREECAM_DISCONNECT_AND_PLAYER_ROTATION_OK");
    }
    private static void hold(ClientGameTestContext context){context.getInput().holdKey(options->Snaplook.binding());context.waitTicks(3);}
    private static void release(ClientGameTestContext context){context.getInput().releaseKey(options->Snaplook.binding());context.waitTicks(2);}
    private static void focus(Minecraft client,boolean active){try{var field=com.mojang.blaze3d.platform.Window.class.getDeclaredField("focused");field.setAccessible(true);field.setBoolean(client.getWindow(),active);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
