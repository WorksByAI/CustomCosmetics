package com.customcosmetics.gametest;
import com.customcosmetics.features.map.*;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.gui.CosmeticsScreen;
import com.customcosmetics.gametest.mixin.FreecamProbe;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;

public final class FreecamGameTest implements FabricClientGameTest {
    public static volatile boolean observe;
    public static final java.util.concurrent.ConcurrentLinkedQueue<String> packets=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private Vec3 bodyPosition,cameraPosition;private float yaw,pitch;private ClientInput input;
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("fill -12 63 -12 12 64 12 minecraft:stone");world.getServer().runCommand("fill -12 65 -12 12 74 12 minecraft:air");
            world.getServer().runCommand("setblock 0 66 0 minecraft:stone");world.getServer().runCommand("tp @a 0 65 8 180 0");context.waitTicks(25);
            context.runOnClient(client->{MapEditor.dungeonOnly=false;MapEditor.enabled=true;MapEditor.editing=false;MapEditor.tick(client);
                bodyPosition=client.player.position();yaw=client.player.getYRot();pitch=client.player.getXRot();input=client.player.input;
                Freecam.setEnabled(true);check(Freecam.active()&&client.getCameraEntity()==Freecam.camera(),"Detached camera not selected");
                check(client.player.input!=input,"Body still consumes real keyboard input");
                packets.clear();observe=true;
                client.options.keyUp.setDown(true);client.options.keyJump.setDown(true);client.options.keySprint.setDown(true);
                client.player.turn(200,-100);
            });context.waitTicks(8);
            context.runOnClient(client->{check(client.player.position().distanceTo(bodyPosition)<1e-6,"Camera flight moved the real body");
                check(client.player.getYRot()==yaw&&client.player.getXRot()==pitch,"Camera look rotated the real body");
                check(client.player.input.keyPresses.equals(Input.EMPTY),"Camera movement reached player input");
                check(Freecam.camera().position().distanceTo(bodyPosition)>2,"Camera did not fly");
                check(Freecam.camera().getViewYRot(1)==Freecam.camera().getYRot(),"Camera view yaw differs from movement/editor yaw");
                client.options.keyUp.setDown(false);client.options.keyJump.setDown(false);client.options.keySprint.setDown(false);
                Freecam.camera().snapTo(new Vec3(.5,65,4),180,0);Freecam.camera().setOldPosAndRot();
                check(MapEditor.target()!=null&&MapEditor.target().getBlockPos().equals(new BlockPos(0,66,0)),"Editor ray did not use detached camera");
                var actions=(FreecamProbe.Actions)client;actions.attack();actions.hold(true);actions.use();actions.pick();
                MapEditor.editing=true;MapEditor.material="minecraft:gold_block";actions.use();
                check(MapEditor.DATA.snapshot().get(new MapEdits.Position(0,66,1)).material().equals("minecraft:gold_block"),"Detached camera failed to place");
                client.options.keyShift.setDown(true);actions.use();client.options.keyShift.setDown(false);
                check(MapEditor.DATA.snapshot().size()==2,"Camera adjacent placement did not use detached camera");
                cameraPosition=Freecam.camera().position();
            });context.waitTicks(25);
            context.runOnClient(client->{
                net.minecraft.client.Camera rendered=null;for(var method:client.gameRenderer.getClass().getMethods())if(method.getParameterCount()==0&&method.getReturnType()==net.minecraft.client.Camera.class)try{rendered=(net.minecraft.client.Camera)method.invoke(client.gameRenderer);}catch(Exception error){throw new AssertionError(error);}
                check(rendered!=null&&rendered.entity()==Freecam.camera()&&rendered.position().distanceTo(Freecam.camera().getEyePosition())<.1,"Rendered view did not follow detached camera");
                check(packets.stream().noneMatch(p->p.contains("MovePlayerPacket")||p.contains("PlayerActionPacket")||p.contains("UseItem")||p.contains("InteractPacket")||p.contains("SwingPacket")),"Freecam sent movement/interaction: "+packets);
                check(client.player.position().distanceTo(bodyPosition)<1e-6,"Body drifted during Freecam");
            });capture(context,"freecam-camera-map-edit");
            world.getServer().runOnServer(server->{var player=server.getPlayerList().getPlayers().getFirst();check(player.position().distanceTo(bodyPosition)<.01,"Server player moved");check(server.getLevel(Level.OVERWORLD).getBlockState(new BlockPos(0,66,0)).is(Blocks.STONE),"Paint reached server");});
            world.getServer().runCommand("tp @a 2 65 8 45 0");context.waitTicks(10);
            context.runOnClient(client->{check(client.player.getX()==2.5,"Server teleport ignored: "+client.player.position());check(Freecam.camera().position().distanceTo(cameraPosition)<1e-6,"Server teleport moved detached camera");
                check(packets.contains("ServerboundAcceptTeleportationPacket"),"Server teleport acknowledgement blocked");
                observe=false;Freecam.setEnabled(false);check(client.getCameraEntity()==client.player&&client.player.input==input,"Freecam did not restore camera/input");
                check(client.player.getX()==2.5&&client.player.getYRot()==45,"Freecam restored stale position or rotation over server correction");
                ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.MAP));
            });context.waitTicks(3);capture(context,"freecam-map-editor-settings");
            context.runOnClient(client->{ClientUiAccess.setScreen(client,null);packets.clear();observe=true;client.player.turn(20,0);client.options.keyUp.setDown(true);});context.waitTicks(5);
            context.runOnClient(client->{check(packets.stream().anyMatch(p->p.contains("MovePlayerPacket")),"Normal player movement/packet observer did not resume");observe=false;client.options.keyUp.setDown(false);Freecam.setEnabled(true);});
        }
        context.waitTicks(5);context.runOnClient(client->check(!Freecam.active(),"Freecam survived disconnect"));
        System.out.println("FREECAM_BODY_LOOK_INPUT_PACKETS_EDITOR_CAMERA_RAY_TELEPORT_RESTORE_DISCONNECT_OK");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void capture(ClientGameTestContext context,String name){
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")){var path=context.takeScreenshot(name);if(name.equals("freecam-camera-map-edit"))verifyEditedBlock(path);return;}
        var done=new java.util.concurrent.atomic.AtomicBoolean();context.runOnClient(client->{com.mojang.blaze3d.pipeline.RenderTarget target=null;for(Object owner:new Object[]{client.gameRenderer,client}){for(var method:owner.getClass().getMethods())if(method.getParameterCount()==0&&method.getReturnType()==com.mojang.blaze3d.pipeline.RenderTarget.class){try{target=(com.mojang.blaze3d.pipeline.RenderTarget)method.invoke(owner);break;}catch(Exception e){throw new AssertionError(e);}}if(target!=null)break;}if(target==null)throw new AssertionError("No render target");net.minecraft.client.Screenshot.grab(client.gameDirectory,name+".png",target,1,message->done.set(true));});context.waitFor(client->done.get());
    }
    private static void verifyEditedBlock(java.nio.file.Path path){try{var image=javax.imageio.ImageIO.read(path.toFile());int gold=0;for(int y=image.getHeight()/5;y<image.getHeight()*7/10;y++)for(int x=image.getWidth()/5;x<image.getWidth()*4/5;x++){int rgb=image.getRGB(x,y),r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;if(r>100&&g>80&&b<g*.7)gold++;}check(gold>100,"Detached camera did not display the edited block (gold pixels="+gold+")");}catch(java.io.IOException error){throw new AssertionError(error);}}
}
