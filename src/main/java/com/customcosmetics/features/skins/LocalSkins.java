// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.skins;
import com.customcosmetics.config.*;
import com.customcosmetics.sync.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
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
    private static final java.util.regex.Pattern TAB_ACCOUNT = java.util.regex.Pattern.compile(
        "^\\h*(?:(?:\\[[^\\]\\r\\n]+]\\h*|[0-9]{1,5}\\h+))*([A-Za-z0-9_]{1,16})(?![A-Za-z0-9_])");
    private static ReplacementSkin other, selfMask;
    private record Prepared(String hash, NativeImage pixels) {}
    private static final PlayerSkinCache skinCache=new PlayerSkinCache();
    private LocalSkins(){}
    private static final SkinTransfer.Source SOURCE=new SkinTransfer.Source(){
        public SkinTransfer.Document local(){return (ConfigManager.localSkinEnabled && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))&&(ConfigManager.skinP2pSharing && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))?local:null;}
        public boolean receiving(){return (ConfigManager.skinP2pReceiving && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))&&"original".equals(ConfigManager.otherPlayerSkinMode);}
        public boolean receiving(UUID id,String account){return receiving()&&!blocked.contains(ConfigManager.skinBlockedPlayers,id,account);}
        public void received(UUID id,SkinTransfer.Document skin){Minecraft.getInstance().execute(()->receive(id,skin));}
    };
    public static SkinTransfer.Source exchange(){return store==null||!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS)?SkinTransfer.Source.NONE:SOURCE;}
    public static void init(){var directory=CosmeticsConfigDir.getCosmeticsConfigDir().resolve("skins");other=new ReplacementSkin(directory.resolve("other-players"));selfMask=new ReplacementSkin(directory.resolve("self-mask"));store=new LocalSkinStore(directory);try{var png=store.load();if(png!=null)local=new SkinTransfer.Document(png,ConfigManager.localSkinSlim);}catch(IOException error){notice("Could not load local skin: "+error.getMessage());}}
    public static void tick(){if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))return;direct=RealtimeSync.directPeers();remote.keySet().retainAll(direct);pending.keySet().retainAll(direct);if(!(ConfigManager.skinP2pReceiving && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))){remote.clear();pending.clear();}
        if(!"original".equals(ConfigManager.otherPlayerSkinMode)){remote.clear();pending.clear();}
        else if(!ConfigManager.skinBlockedPlayers.isEmpty()){remote.keySet().removeIf(LocalSkins::blocked);pending.keySet().removeIf(LocalSkins::blocked);}
        if("custom".equals(ConfigManager.otherPlayerSkinMode))other.prepare();
        if(ConfigManager.nickHiderSelfSkin && "custom".equals(ConfigManager.nickHiderSelfSkinMode))selfMask.prepare();
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
        ConfigManager.otherPlayerSkinMode=SkinRules.mode(mode);other.invalidate();ConfigManager.requestSave();
    }
    public static String otherNotice(){return other.notice;}
    public static CompletableFuture<Void> importOther(Path file){return other.load(file, () -> ConfigManager.otherPlayerSkinMode = "custom");}
    public static String selfMaskMode(String mode) {
        String normalized = SkinRules.mode(mode);
        return normalized.equals("original") ? "auto" : normalized;
    }
    public static void selectSelfMask(String mode) {
        ConfigManager.nickHiderSelfSkinMode = selfMaskMode(mode);
        selfMask.invalidate();
        ConfigManager.requestSave();
    }
    public static String selfMaskNotice() { return selfMask.notice; }
    public static CompletableFuture<Void> importSelfMask(Path file) {
        return selfMask.load(file, () -> ConfigManager.nickHiderSelfSkinMode = "custom");
    }

    /** Local replacement files and user choices are separate from the P2P skin document. */
    private static final class ReplacementSkin {
        private final LocalSkinStore store;
        private String hash, notice = "";
        private long revision;
        private boolean loading, attempted;

        ReplacementSkin(Path directory) { store = new LocalSkinStore(directory); }
        Identifier texture() { return hash == null ? null : textures.get(hash); }
        void invalidate() { revision++; loading = attempted = false; notice = ""; }
        void prepare() {
            if (hash != null && texture() == null) { hash = null; attempted = false; }
            if (hash == null && !loading && !attempted) load(null, null);
        }

        CompletableFuture<Void> load(Path file, Runnable onImport) {
            long request = ++revision;
            loading = attempted = true;
            notice = "";
            return CompletableFuture.supplyAsync(() -> {
                try {
                    byte[] png = file == null ? store.load() : store.save(SkinImages.read(file));
                    return png == null ? null : new Prepared(SkinTransfer.hash(png), NativeImage.read(png));
                } catch (IOException error) { throw new CompletionException(error); }
            }).handle((prepared, error) -> {
                var applied = new CompletableFuture<Void>();
                Minecraft.getInstance().execute(() -> {
                    if (request != revision) {
                        if (prepared != null) prepared.pixels.close();
                        applied.complete(null);
                        return;
                    }
                    loading = false;
                    if (error != null) { notice = "Could not load skin."; applied.completeExceptionally(error); return; }
                    if (prepared == null) { notice = "Choose a custom skin first."; applied.complete(null); return; }
                    if (onImport != null) { onImport.run(); ConfigManager.requestSave(); }
                    if (!FeatureModules.enabled(FeatureModules.Feature.SKINS)) {
                        attempted = false;
                        prepared.pixels.close();
                        applied.complete(null);
                        return;
                    }
                    try {
                        registerTexture(prepared.hash, prepared.pixels);
                        hash = prepared.hash;
                        applied.complete(null);
                    } catch (RuntimeException failure) { prepared.pixels.close(); applied.completeExceptionally(failure); }
                });
                return applied;
            }).thenCompose(result -> result);
        }
    }
    private static Identifier registerTexture(String hash,NativeImage pixels){
        var existing=textures.get(hash);if(existing!=null){pixels.close();return existing;}
        var id=Identifier.fromNamespaceAndPath("customcosmetics","skin/"+hash);Minecraft.getInstance().getTextureManager().register(id,new DynamicTexture(()->"CustomSkyBlock skin",pixels));textures.put(hash,id);
        while(textures.size()>20){var first=textures.entrySet().iterator();var entry=first.next();first.remove();Minecraft.getInstance().getTextureManager().release(entry.getValue());}return id;
    }
    private static Identifier texture(byte[] png)throws IOException{
        String hash=SkinTransfer.hash(png);var existing=textures.get(hash);if(existing!=null)return existing;
        return registerTexture(hash,NativeImage.read(png));
    }
    public static PlayerSkin skin(UUID id,PlayerSkin original){return skin(id,null,original);}
    public static PlayerSkin rosterSkin(PlayerInfo entry, PlayerSkin original) {
        if (!ConfigManager.nickHiderSelfSkin || !FeatureModules.enabled(FeatureModules.Feature.SKINS)
                || !isOwnRosterEntry(entry)) return original;
        return hiddenSelfSkin(entry.getProfile().id(), original);
    }

    public static PlayerSkin tabSkin(PlayerInfo entry, PlayerSkin original) {
        if ("original".equals(ConfigManager.otherPlayerSkinMode)
                || !FeatureModules.enabled(FeatureModules.Feature.SKINS) || isOwnRosterEntry(entry)) return original;
        return globalSkin(entry.getProfile().id(), original);
    }

    private static boolean isOwnRosterEntry(PlayerInfo entry) {
        var client = Minecraft.getInstance();
        var user = client.getUser();
        var profile = entry.getProfile();
        if (profile.id().equals(user.getProfileId()) || profile.name().equalsIgnoreCase(user.getName())
                || client.player != null && profile.id().equals(client.player.getUUID())) return true;

        // SkyBlock can show a formatted roster row under a server-generated profile.
        var display = entry.getTabListDisplayName();
        if (display == null) return false;
        var account = TAB_ACCOUNT.matcher(ChatFormatting.stripFormatting(display.getString()));
        return account.find() && user.getName().equalsIgnoreCase(account.group(1));
    }

    private static PlayerSkin hiddenSelfSkin(UUID id, PlayerSkin original) {
        var model = original.model();
        var automatic = model == PlayerModelType.SLIM ? ALEX : STEVE;
        return switch (ConfigManager.nickHiderSelfSkinMode) {
            case "steve" -> skinCache.body(id, original, STEVE, PlayerModelType.WIDE);
            case "alex" -> skinCache.body(id, original, ALEX, PlayerModelType.SLIM);
            case "custom" -> {
                var texture = selfMask.texture();
                yield texture == null ? skinCache.body(id, original, automatic, model)
                    : skinCache.body(id, original, texture, ConfigManager.nickHiderSelfSkinSlim ? PlayerModelType.SLIM : PlayerModelType.WIDE);
            }
            default -> skinCache.body(id, original, automatic, model);
        };
    }

    private static PlayerSkin globalSkin(UUID id, PlayerSkin original) {
        return switch (ConfigManager.otherPlayerSkinMode) {
            case "steve" -> skinCache.body(id, original, STEVE, PlayerModelType.WIDE);
            case "alex" -> skinCache.body(id, original, ALEX, PlayerModelType.SLIM);
            case "custom" -> {
                var texture = other.texture();
                yield texture == null ? skinCache.body(id, original, STEVE, PlayerModelType.WIDE)
                    : skinCache.body(id, original, texture, ConfigManager.otherPlayerSkinSlim ? PlayerModelType.SLIM : PlayerModelType.WIDE);
            }
            default -> original;
        };
    }

    public static PlayerSkin skin(UUID id,String account,PlayerSkin original){
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))return original;
        var client=Minecraft.getInstance();boolean self=id.equals(client.getUser().getProfileId());String hash;boolean slim;
        if (self && ConfigManager.nickHiderSelfSkin) return hiddenSelfSkin(id, original);
        if(!self){
            if(!"original".equals(ConfigManager.otherPlayerSkinMode))return globalSkin(id,original);
            if(blocked(id,account)||account==null&&blocked(id))return original;
        }
        if(self){var document=local;if(!(ConfigManager.localSkinEnabled && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))||document==null)return original;hash=document.hash();slim=document.slim();}
        else{var ready=remote.get(id);if(!(ConfigManager.skinP2pReceiving && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))||!RemoteAccess.online()||!direct.contains(id)||ready==null)return original;hash=ready.textureHash;slim=ready.slim;}
        var texture=textures.get(hash);if(texture==null)return original;
        return skinCache.body(id,original,texture,slim?PlayerModelType.SLIM:PlayerModelType.WIDE);
    }
    public static void close(){if(other!=null)other.invalidate();if(selfMask!=null)selfMask.invalidate();for(var texture:textures.values())Minecraft.getInstance().getTextureManager().release(texture);textures.clear();remote.clear();pending.clear();direct=Set.of();skinCache.clear();}
    private static void notice(String text){com.customcosmetics.features.cape.CapeCosmetics.notice(text);}
}
