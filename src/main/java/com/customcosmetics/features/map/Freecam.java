// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import com.customcosmetics.compatibility.ClientUiAccess;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.player.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/** A camera entity that is never added to the level or used as the network player. */
public final class Freecam {
    public static double speed=12;
    private static KeyMapping key;
    private static RemotePlayer camera;
    private static LocalPlayer body;
    private static ClientInput input;
    private static CameraType perspective;
    private Freecam(){}

    public static void init(){
        key=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.customcosmetics.freecam",InputConstants.UNKNOWN.getValue(),KeyMapping.Category.register(Identifier.fromNamespaceAndPath("customcosmetics","map_editor"))));
    }
    public static boolean active(){return camera!=null;}
    public static Entity camera(){return camera;}
    public static boolean controls(Entity entity){return active()&&entity==body;}
    public static void toggle(){setEnabled(!active());}
    public static void setEnabled(boolean enabled){
        var client=Minecraft.getInstance();
        if(!enabled){stop(client);return;}
        if(active())return;
        if(!MapEditor.allowed(client))throw new IllegalStateException("Freecam needs an allowed Map Editor world.");
        if(!client.player.isAlive()||client.player.isPassenger()||client.player.isSleeping())throw new IllegalStateException("Leave the vehicle or bed before enabling Freecam.");
        if(client.getCameraEntity()!=client.player)throw new IllegalStateException("Return to your player camera first.");
        body=client.player;input=body.input;perspective=client.options.getCameraType();
        camera=new RemotePlayer(client.level,body.getGameProfile()){
            // LivingEntity normally renders its independent head yaw. Our un-ticked camera uses mouse yaw.
            @Override public float getViewYRot(float delta){return getYRot(delta);}
        };
        camera.noPhysics=true;camera.setNoGravity(true);
        camera.snapTo(body.position(),body.getYRot(),body.getXRot());camera.setOldPosAndRot();
        body.input=new ClientInput();body.input.keyPresses=Input.EMPTY;
        body.setDeltaMovement(Vec3.ZERO);body.xxa=body.zza=0;
        if(client.gameMode!=null)client.gameMode.stopDestroyBlock();
        body.releaseUsingItem();
        client.options.setCameraType(CameraType.FIRST_PERSON);client.setCameraEntity(camera);
        MapEditor.enabled=true;MapEditor.refresh();
    }
    public static void stop(Minecraft client){
        if(!active())return;
        var oldCamera=camera;var oldBody=body;camera=null;body=null;
        // Never overwrite a new input/controller after respawn or another camera selected by the server.
        if(client.player==oldBody){oldBody.input=input;oldBody.setDeltaMovement(Vec3.ZERO);oldBody.xxa=oldBody.zza=0;}
        if(client.getCameraEntity()==oldCamera)client.setCameraEntity(client.player);
        client.options.setCameraType(perspective);input=null;perspective=null;
        // Held attack/use clicks must not escape when returning to normal play.
        client.options.keyAttack.setDown(false);client.options.keyUse.setDown(false);
        while(client.options.keyAttack.consumeClick()){}while(client.options.keyUse.consumeClick()){}
    }
    public static void tick(Minecraft client){
        while(key!=null&&key.consumeClick())if(ClientUiAccess.screen(client)==null)MapCommands.run(Freecam::toggle);
        if(!active())return;
        if(client.player!=body||client.level!=camera.level()||!MapEditor.enabled||!MapEditor.allowed(client)||!body.isAlive()||body.isPassenger()||body.isSleeping()||client.getCameraEntity()!=camera){stop(client);return;}
        camera.setOldPosAndRot();client.options.setCameraType(CameraType.FIRST_PERSON);
        if(ClientUiAccess.screen(client)!=null)return;
        var options=client.options;
        var step=step(camera.getYRot(),camera.getXRot(),axis(options.keyUp,options.keyDown),axis(options.keyLeft,options.keyRight),axis(options.keyJump,options.keyShift),options.keySprint.isDown(),speed);
        var destination=camera.position().add(step);
        // Exploring unseen chunks would require moving the real player. Stay in already loaded chunks.
        if(client.level.hasChunkAt(net.minecraft.core.BlockPos.containing(destination)))camera.setPos(destination);
    }
    private static int axis(KeyMapping positive,KeyMapping negative){return (positive.isDown()?1:0)-(negative.isDown()?1:0);}
    public static double validSpeed(double value){return Double.isFinite(value)?Math.clamp(value,1,40):12;}
    public static Vec3 step(float yaw,float pitch,int forward,int left,int up,boolean sprint,double blocksPerSecond){
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        var direction=new Vec3(-Math.sin(y)*Math.cos(p)*forward+Math.cos(y)*left,-Math.sin(p)*forward+up,Math.cos(y)*Math.cos(p)*forward+Math.sin(y)*left);
        double length=direction.length();
        return length<1e-8?Vec3.ZERO:direction.scale(validSpeed(blocksPerSecond)/20*(sprint?3:1)/Math.max(1,length));
    }
    public static boolean turn(Entity player,double x,double y){if(!controls(player))return false;camera.turn(x,y);return true;}
}
