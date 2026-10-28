// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.skins;
import com.customcosmetics.config.*;
import com.customcosmetics.sync.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
    private static final SkinRules.Blocks blocked=new SkinRules.Blocks();
    private static final Identifier STEVE=Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png"),ALEX=Identifier.withDefaultNamespace("textures/entity/player/slim/alex.png");
    private static LocalSkinStore otherStore;private static SkinTransfer.Document other;private static long otherRevision;
    private static boolean otherLoading,otherAttempted;private static String otherNotice="";
    private record Prepared(SkinTransfer.Document document,NativeImage pixels){}
    private static final PlayerSkinCache skinCache=new PlayerSkinCache();
    private LocalSkins(){}
    private static final SkinTransfer.Source SOURCE=new SkinTransfer.Source(){
        public SkinTransfer.Document local(){return (ConfigManager.localSkinEnabled && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))&&(ConfigManager.skinP2pSharing && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))?local:null;}
        public boolean receiving(){return (ConfigManager.skinP2pReceiving && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))&&"original".equals(ConfigManager.otherPlayerSkinMode);}
        public boolean receiving(UUID id,String account){return receiving()&&!blocked.contains(ConfigManager.skinBlockedPlayers,id,account);}
        public void received(UUID id,SkinTransfer.Document skin){Minecraft.getInstance().execute(()->receive(id,skin));}
    };
    public static SkinTransfer.Source exchange(){return store==null||!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS)?SkinTransfer.Source.NONE:SOURCE;}
    public static void init(){otherStore=new LocalSkinStore(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("skins/other-players"));store=new LocalSkinStore(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("skins"));try{var png=store.load();if(png!=null)local=new SkinTransfer.Document(png,ConfigManager.localSkinSlim);}catch(IOException error){notice("Could not load local skin: "+error.getMessage());}}
    public static void tick(){if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))return;direct=RealtimeSync.directPeers();remote.keySet().retainAll(direct);pending.keySet().retainAll(direct);if(!(ConfigManager.skinP2pReceiving && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))){remote.clear();pending.clear();}
        if(!"original".equals(ConfigManager.otherPlayerSkinMode)){remote.clear();pending.clear();}
        else if(!ConfigManager.skinBlockedPlayers.isEmpty()){remote.keySet().removeIf(LocalSkins::blocked);pending.keySet().removeIf(LocalSkins::blocked);}
        if("custom".equals(ConfigManager.otherPlayerSkinMode)){
            if(other!=null&&!textures.containsKey(other.hash())){other=null;otherAttempted=false;}
            if(other==null&&!otherLoading&&!otherAttempted)loadOther(null,false);
        }
        var document=local;if(document!=null&&(ConfigManager.localSkinEnabled && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))&&!textures.containsKey(document.hash()))try{texture(document.bytes());}catch(IOException error){notice("Could not load skin texture: "+error.getMessage());}
    }
    public static boolean hasLocal(){return local!=null;}
    public static int directCount(){return direct.size();}
    public static CompletableFuture<Void> importFile(Path path){return CompletableFuture.supplyAsync(()->{try{return store.save(SkinImages.read(path));}catch(IOException error){throw new CompletionException(error);}}).thenAccept(png->Minecraft.getInstance().execute(()->{local=new SkinTransfer.Document(png,ConfigManager.localSkinSlim);ConfigManager.localSkinEnabled=true;ConfigManager.requestSave();}));}
    public static void clear()throws IOException{store.clear();local=null;}
    public static void slim(boolean slim){ConfigManager.localSkinSlim=slim;var old=local;if(old!=null)local=new SkinTransfer.Document(old.bytes(),slim);ConfigManager.requestSave();}
    private static void receive(UUID id,SkinTransfer.Document document){
        if(document==null){remote.remove(id);pending.remove(id);return;}
        if(!(ConfigManager.skinP2pReceiving && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))||!direct.contains(id)||!RemoteAccess.online()||blocked(id)||!"original".equals(ConfigManager.otherPlayerSkinMode))return;
        var ready=remote.get(id);if(ready!=null&&ready.wireHash.equals(document.hash())&&textures.containsKey(ready.textureHash)){remote.put(id,new Ready(ready.wireHash,ready.textureHash,document.slim()));return;}
        if(document.hash().equals(pending.get(id))||decoding>=2)return;
        decoding++;pending.put(id,document.hash());
        CompletableFuture.supplyAsync(()->{try{return SkinImages.normalize(document.bytes());}catch(IOException error){throw new CompletionException(error);}}).whenComplete((png,error)->Minecraft.getInstance().execute(()->{
            decoding--;if(!document.hash().equals(pending.get(id)))return;pending.remove(id);
            if(error!=null||!(ConfigManager.skinP2pReceiving && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))||!direct.contains(id)||!RemoteAccess.online()||blocked(id)||!"original".equals(ConfigManager.otherPlayerSkinMode))return;
            try{String hash=SkinTransfer.hash(png);texture(png);remote.put(id,new Ready(document.hash(),hash,document.slim()));}catch(IOException ignored){}
        }));
    }
    public static boolean blocked(UUID id,String account){return blocked.contains(ConfigManager.skinBlockedPlayers,id,account);}
    private static boolean blocked(UUID id){
        if(ConfigManager.skinBlockedPlayers.isEmpty())return false;
        if(blocked(id,null))return true;
        var client=Minecraft.getInstance();var info=client.getConnection()==null?null:client.getConnection().getPlayerInfo(id);
        var player=info==null&&client.level!=null?client.level.getPlayerByUUID(id):null;
        return blocked(id,info!=null?info.getProfile().name():player!=null?player.getGameProfile().name():PresenceSync.cachedPlayerName(id));
    }
    public static void otherMode(String mode){
        ConfigManager.otherPlayerSkinMode=SkinRules.mode(mode);otherRevision++;otherLoading=false;otherAttempted=false;otherNotice="";ConfigManager.requestSave();
    }
    public static String otherNotice(){return otherNotice;}
    public static CompletableFuture<Void> importOther(Path file){return loadOther(file,true);}
    private static CompletableFuture<Void> loadOther(Path file,boolean importing){
        long revision=++otherRevision;otherLoading=true;otherAttempted=true;otherNotice="";
        return CompletableFuture.supplyAsync(()->{
            try{byte[] png=importing?otherStore.save(SkinImages.read(file)):otherStore.load();
                return png==null?null:new Prepared(new SkinTransfer.Document(png,false),NativeImage.read(png));
            }catch(IOException error){throw new CompletionException(error);}
        }).handle((prepared,error)->{
            var applied=new CompletableFuture<Void>();Minecraft.getInstance().execute(()->{
                if(revision!=otherRevision){if(prepared!=null)prepared.pixels.close();applied.complete(null);return;}
                otherLoading=false;
                if(error!=null){otherNotice="Could not load global skin.";applied.completeExceptionally(error);return;}
                if(prepared==null){otherNotice="Choose a custom skin first.";applied.complete(null);return;}
                if(importing){ConfigManager.otherPlayerSkinMode="custom";ConfigManager.requestSave();}
                if(!FeatureModules.enabled(FeatureModules.Feature.SKINS)){otherAttempted=false;prepared.pixels.close();applied.complete(null);return;}
                try{registerTexture(prepared.document.hash(),prepared.pixels);other=prepared.document;if(importing)ConfigManager.otherPlayerSkinMode="custom";ConfigManager.requestSave();applied.complete(null);}
                catch(RuntimeException failure){prepared.pixels.close();applied.completeExceptionally(failure);}
            });return applied;
        }).thenCompose(result->result);
    }
    private static Identifier registerTexture(String hash,NativeImage pixels){
        var existing=textures.get(hash);if(existing!=null){pixels.close();return existing;}
        var id=Identifier.fromNamespaceAndPath("customcosmetics","skin/"+hash);Minecraft.getInstance().getTextureManager().register(id,new DynamicTexture(()->"CustomCosmetics skin",pixels));textures.put(hash,id);
        while(textures.size()>20){var first=textures.entrySet().iterator();var entry=first.next();first.remove();Minecraft.getInstance().getTextureManager().release(entry.getValue());}return id;
    }
    private static Identifier texture(byte[] png)throws IOException{
        String hash=SkinTransfer.hash(png);var existing=textures.get(hash);if(existing!=null)return existing;
        return registerTexture(hash,NativeImage.read(png));
    }
    public static PlayerSkin skin(UUID id,PlayerSkin original){return skin(id,null,original);}
    public static PlayerSkin skin(UUID id,String account,PlayerSkin original){
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))return original;
        var client=Minecraft.getInstance();boolean self=id.equals(client.getUser().getProfileId());String hash;boolean slim;
        if(!self){
            if(blocked(id,account)||account==null&&blocked(id))return original;
            switch(ConfigManager.otherPlayerSkinMode){
                case "steve":return skinCache.body(id,original,STEVE,PlayerModelType.WIDE);
                case "alex":return skinCache.body(id,original,ALEX,PlayerModelType.SLIM);
                case "custom":var document=other;var texture=document==null?null:textures.get(document.hash());return texture==null?original:skinCache.body(id,original,texture,ConfigManager.otherPlayerSkinSlim?PlayerModelType.SLIM:PlayerModelType.WIDE);
                default:break;
            }
        }
        if(self){var document=local;if(!(ConfigManager.localSkinEnabled && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))||document==null)return original;hash=document.hash();slim=document.slim();}
        else{var ready=remote.get(id);if(!(ConfigManager.skinP2pReceiving && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))||!RemoteAccess.online()||!direct.contains(id)||ready==null)return original;hash=ready.textureHash;slim=ready.slim;}
        var texture=textures.get(hash);if(texture==null)return original;
        return skinCache.body(id,original,texture,slim?PlayerModelType.SLIM:PlayerModelType.WIDE);
    }
    public static void close(){otherRevision++;otherLoading=false;otherAttempted=false;other=null;for(var texture:textures.values())Minecraft.getInstance().getTextureManager().release(texture);textures.clear();remote.clear();pending.clear();direct=Set.of();skinCache.clear();}
    private static void notice(String text){com.customcosmetics.features.cape.CapeCosmetics.notice(text);}
}
