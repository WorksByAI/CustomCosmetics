// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.skins;
import com.customcosmetics.config.*;
import com.customcosmetics.sync.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.*;
import java.util.*;
import java.nio.file.Path;
import java.io.IOException;
import java.util.concurrent.*;
/** Local texture override and direct-peer-only received skins. Remote images are never saved or relayed. */
public final class LocalSkins {
    private record Ready(String wireHash,String textureHash,boolean slim){}
    private static LocalSkinStore store;private static volatile SkinTransfer.Document local;
    private static final LinkedHashMap<String,Identifier> textures=new LinkedHashMap<>(16,.75f,true);
    private static final Map<UUID,Ready> remote=new HashMap<>();private static final Map<UUID,String> pending=new HashMap<>();
    private static Set<UUID> direct=Set.of();private static int decoding;
    private LocalSkins(){}
    private static final SkinTransfer.Source SOURCE=new SkinTransfer.Source(){
        public SkinTransfer.Document local(){return ConfigManager.localSkinEnabled&&ConfigManager.skinP2pSharing?local:null;}
        public boolean receiving(){return ConfigManager.skinP2pReceiving;}
        public void received(UUID id,SkinTransfer.Document skin){Minecraft.getInstance().execute(()->receive(id,skin));}
    };
    public static SkinTransfer.Source exchange(){return store==null?SkinTransfer.Source.NONE:SOURCE;}
    public static void init(){store=new LocalSkinStore(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("skins"));try{var png=store.load();if(png!=null)local=new SkinTransfer.Document(png,ConfigManager.localSkinSlim);}catch(IOException error){notice("Could not load local skin: "+error.getMessage());}}
    public static void tick(){direct=RealtimeSync.directPeers();remote.keySet().retainAll(direct);pending.keySet().retainAll(direct);if(!ConfigManager.skinP2pReceiving){remote.clear();pending.clear();}
        var document=local;if(document!=null&&ConfigManager.localSkinEnabled&&!textures.containsKey(document.hash()))try{texture(document.bytes());}catch(IOException error){notice("Could not load skin texture: "+error.getMessage());}
    }
    public static boolean hasLocal(){return local!=null;}
    public static int directCount(){return direct.size();}
    public static CompletableFuture<Void> importFile(Path path){return CompletableFuture.supplyAsync(()->{try{return store.save(SkinImages.read(path));}catch(IOException error){throw new CompletionException(error);}}).thenAccept(png->Minecraft.getInstance().execute(()->{local=new SkinTransfer.Document(png,ConfigManager.localSkinSlim);ConfigManager.localSkinEnabled=true;ConfigManager.requestSave();}));}
    public static void clear()throws IOException{store.clear();local=null;}
    public static void slim(boolean slim){ConfigManager.localSkinSlim=slim;var old=local;if(old!=null)local=new SkinTransfer.Document(old.bytes(),slim);ConfigManager.requestSave();}
    private static void receive(UUID id,SkinTransfer.Document document){
        if(document==null){remote.remove(id);pending.remove(id);return;}
        if(!ConfigManager.skinP2pReceiving||!direct.contains(id)||!RemoteAccess.online())return;
        var ready=remote.get(id);if(ready!=null&&ready.wireHash.equals(document.hash())&&textures.containsKey(ready.textureHash)){remote.put(id,new Ready(ready.wireHash,ready.textureHash,document.slim()));return;}
        if(document.hash().equals(pending.get(id))||decoding>=2)return;
        decoding++;pending.put(id,document.hash());
        CompletableFuture.supplyAsync(()->{try{return SkinImages.normalize(document.bytes());}catch(IOException error){throw new CompletionException(error);}}).whenComplete((png,error)->Minecraft.getInstance().execute(()->{
            decoding--;if(!document.hash().equals(pending.get(id)))return;pending.remove(id);
            if(error!=null||!ConfigManager.skinP2pReceiving||!direct.contains(id)||!RemoteAccess.online())return;
            try{String hash=SkinTransfer.hash(png);texture(png);remote.put(id,new Ready(document.hash(),hash,document.slim()));}catch(IOException ignored){}
        }));
    }
    private static Identifier texture(byte[] png)throws IOException{
        String hash=SkinTransfer.hash(png);var existing=textures.get(hash);if(existing!=null)return existing;
        var id=Identifier.fromNamespaceAndPath("customcosmetics","skin/"+hash);var pixels=NativeImage.read(png);Minecraft.getInstance().getTextureManager().register(id,new DynamicTexture(()->"CustomCosmetics skin",pixels));textures.put(hash,id);
        while(textures.size()>20){var first=textures.entrySet().iterator();var entry=first.next();first.remove();Minecraft.getInstance().getTextureManager().release(entry.getValue());}return id;
    }
    public static PlayerSkin skin(UUID id,PlayerSkin original){
        var client=Minecraft.getInstance();boolean self=id.equals(client.getUser().getProfileId());String hash;boolean slim;
        if(self){var document=local;if(!ConfigManager.localSkinEnabled||document==null)return original;hash=document.hash();slim=document.slim();}
        else{var ready=remote.get(id);if(!ConfigManager.skinP2pReceiving||!RemoteAccess.online()||!direct.contains(id)||ready==null)return original;hash=ready.textureHash;slim=ready.slim;}
        var texture=textures.get(hash);if(texture==null)return original;
        ClientAsset.Texture asset=new ClientAsset.Texture(){public Identifier id(){return texture;}public Identifier texturePath(){return texture;}};
        return new PlayerSkin(asset,original.cape(),original.elytra(),slim?PlayerModelType.SLIM:PlayerModelType.WIDE,false);
    }
    public static void close(){for(var texture:textures.values())Minecraft.getInstance().getTextureManager().release(texture);textures.clear();remote.clear();pending.clear();direct=Set.of();}
    private static void notice(String text){com.customcosmetics.features.cape.CapeCosmetics.notice(text);}
}
