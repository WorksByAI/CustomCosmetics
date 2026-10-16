package com.customcosmetics.gametest;
import com.customcosmetics.features.skins.*;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.gui.*;
import com.customcosmetics.compatibility.ClientUiAccess;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerModelType;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
public final class SkinGameTest implements FabricClientGameTest {
    public static volatile Set<UUID> DIRECT=Set.of();private static final UUID PEER=UUID.fromString("62345678-1234-4234-8234-123456789abc");
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.partyEspEnabled=false;ConfigManager.partyChatEnabled=false;ConfigManager.sharingChamsEnabled=false;ConfigManager.skinP2pSharing=false;ConfigManager.skinP2pReceiving=true;ConfigManager.localSkinSlim=false;try{LocalSkins.clear();}catch(java.io.IOException error){throw new AssertionError(error);}});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("fill -8 63 -8 8 64 8 minecraft:stone");world.getServer().runCommand("fill -8 65 -8 8 74 8 minecraft:air");world.getServer().runCommand("time set noon");world.getServer().runCommand("tp @a 0 65 5 180 0");context.waitTicks(20);
            var imported=new AtomicReference<java.util.concurrent.CompletableFuture<Void>>();var original=new AtomicReference<net.minecraft.world.entity.player.PlayerSkin>();
            context.runOnClient(client->{original.set(client.player.getSkin());var path=Path.of("skin-fixture.png");Files.write(path,png(0xffff33bb));imported.set(LocalSkins.importFile(path));});context.waitFor(client->imported.get().isDone());imported.get().join();context.waitTicks(3);
            context.runOnClient(client->{check(client.player.getSkin().body().texturePath().getNamespace().equals("customcosmetics"),"Local skin did not replace the player skin");check(client.player.getSkin().cape()==original.get().cape(),"Local skin replaced the cape");LocalSkins.slim(true);check(client.player.getSkin().model()==PlayerModelType.SLIM,"Slim skin model selection did not apply");ConfigManager.offlineMode=false;var player=new RemotePlayer(client.level,new com.mojang.authlib.GameProfile(PEER,"SkinPeer"));player.setId(-29101);player.setPos(0,65,0);client.level.addEntity(player);DIRECT=Set.of(PEER);});context.waitTicks(2);
            context.runOnClient(client->LocalSkins.exchange().received(PEER,new SkinTransfer.Document(png(0xff11dddd),true)));context.waitFor(client->((RemotePlayer)client.level.getEntity(-29101)).getSkin().body().texturePath().getNamespace().equals("customcosmetics"));context.waitTicks(4);capture(context,"skins-direct-peer-texture");
            context.runOnClient(client->{var skin=((RemotePlayer)client.level.getEntity(-29101)).getSkin();check(skin.model()==PlayerModelType.SLIM,"Direct skin lost model metadata");DIRECT=Set.of();});context.waitTicks(2);
            context.runOnClient(client->{check(!((RemotePlayer)client.level.getEntity(-29101)).getSkin().body().texturePath().getNamespace().equals("customcosmetics"),"Remote skin stayed visible after P2P ended");check(client.player.getSkin().body().texturePath().getNamespace().equals("customcosmetics"),"Losing P2P removed the private local skin");ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SKINS));});context.waitTicks(4);capture(context,"skins-local-preview-settings");
            context.runOnClient(client->{ClientUiAccess.setScreen(client,new SkinImportScreen(null));});context.waitTicks(3);capture(context,"skins-import-screen");
            context.runOnClient(client->{LocalSkins.clear();ConfigManager.offlineMode=true;ClientUiAccess.setScreen(client,null);check(!client.player.getSkin().body().texturePath().getNamespace().equals("customcosmetics"),"Removing local skin did not restore original");});
            System.out.println("SKINS_LOCAL_IMPORT_SLIM_CAPE_PRESERVATION_DIRECT_RENDER_DISCONNECT_FALLBACK_GUI_OK");
        }catch(Exception error){throw new AssertionError(error);}finally{DIRECT=Set.of();}
    }
    private static byte[] png(int color){try{var image=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,color);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"PNG",bytes);return SkinImages.normalize(bytes.toByteArray());}catch(Exception error){throw new AssertionError(error);}}
    private static void check(boolean valid,String error){if(!valid)throw new AssertionError(error);}
    private static void capture(ClientGameTestContext context,String name){var done=new AtomicBoolean();context.runOnClient(client->{com.mojang.blaze3d.pipeline.RenderTarget target=null;for(Object owner:new Object[]{client.gameRenderer,client}){for(var method:owner.getClass().getMethods())if(method.getParameterCount()==0&&method.getReturnType()==com.mojang.blaze3d.pipeline.RenderTarget.class){try{target=(com.mojang.blaze3d.pipeline.RenderTarget)method.invoke(owner);break;}catch(Exception e){throw new AssertionError(e);}}if(target!=null)break;}net.minecraft.client.Screenshot.grab(client.gameDirectory,name+".png",target,1,message->done.set(true));});context.waitFor(client->done.get());}
}
