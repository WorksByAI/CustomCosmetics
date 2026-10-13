// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.cape;

import com.customcosmetics.config.*;
import com.customcosmetics.sync.RemoteAccess;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import java.util.*;
import java.nio.file.*;
import java.io.*;

public final class CapeCosmetics {
    private static LocalCapeStore local;
    private static final LinkedHashMap<String,Identifier> textures=new LinkedHashMap<>(16,.75f,true);
    private static volatile Map<UUID,String> approved=Map.of();
    private static int previewDepth;
    private static long textureRetry;
    private CapeCosmetics(){}
    public static void init(){try{local=new LocalCapeStore(CosmeticsConfigDir.getCosmeticsConfigDir());}catch(Exception e){notice("Could not load local cape: "+e.getMessage());}}
    public static void tick(){if(local!=null&&local.hash()!=null&&!loaded(local.hash())&&System.currentTimeMillis()>=textureRetry){textureRetry=System.currentTimeMillis()+5000;try{texture(local.copy());}catch(IOException ignored){}}}
    public static byte[] localPng(){return local==null?null:local.copy();}
    public static synchronized Identifier preview(){return local==null||local.hash()==null?null:textures.get(local.hash());}
    public static void saveLocal(byte[] png)throws IOException{local.save(png);texture(local.copy());}
    public static void clearLocal()throws IOException{local.clear();}
    public static void approved(Map<UUID,String> state){approved=Map.copyOf(state);}
    public static void clearRemote(){approved=Map.of();}
    public static synchronized boolean loaded(String hash){return textures.containsKey(hash);}
    public static synchronized Identifier texture(byte[] png)throws IOException{
        String hash=CapeImages.hash(png);Identifier existing=textures.get(hash);if(existing!=null)return existing;
        var id=Identifier.fromNamespaceAndPath("customcosmetics","cape/"+hash);
        var pixels=NativeImage.read(png);var texture=new DynamicTexture(()->"CustomCosmetics cape",pixels);
        Minecraft.getInstance().getTextureManager().register(id,texture);textures.put(hash,id);
        while(textures.size()>64){var iterator=textures.entrySet().iterator();var first=iterator.next();iterator.remove();Minecraft.getInstance().getTextureManager().release(first.getValue());}
        return id;
    }
    public static synchronized PlayerSkin skin(UUID uuid,PlayerSkin original){
        if(!ConfigManager.capeEnabled)return original;
        var client=Minecraft.getInstance();UUID self=client.player==null?null:client.player.getUUID();
        String hash=CapeVisibility.hash(uuid,self,RemoteAccess.online(),ConfigManager.capeEnabled,previewDepth>0,local==null?null:local.hash(),approved);
        Identifier id=hash==null?null:textures.get(hash);
        if(id==null)return original;
        ClientAsset.Texture asset=new ClientAsset.Texture(){public Identifier id(){return id;}public Identifier texturePath(){return id;}};
        return new PlayerSkin(original.body(),asset,asset,original.model(),original.secure());
    }
    public static void beginPreview(){previewDepth++;}public static void endPreview(){previewDepth=Math.max(0,previewDepth-1);}
    public static void notice(String text){var client=Minecraft.getInstance();client.execute(()->{if(client.player!=null)com.customcosmetics.compatibility.ClientUiAccess.chat(client).addClientSystemMessage(com.customcosmetics.utils.MessageUtils.createMessageWithPrefix(net.minecraft.network.chat.Component.literal(text)));});}
    public static void reviewNotice(int count,String base){
        var client=Minecraft.getInstance();client.execute(()->{
            if(!RemoteAccess.online()||client.player==null||!CapeAdministrators.includes(client.getUser().getProfileId()))return;
            var link=net.minecraft.network.chat.Component.literal("[Review]").withStyle(style->style.withUnderlined(true).withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(base+"/admin"))));
            com.customcosmetics.compatibility.ClientUiAccess.chat(client).addClientSystemMessage(com.customcosmetics.utils.MessageUtils.createMessageWithPrefix(net.minecraft.network.chat.Component.literal(count+" cape submission(s) await review. ").append(link)));
        });
    }
    public static synchronized void close(){for(var id:textures.values())Minecraft.getInstance().getTextureManager().release(id);textures.clear();approved=Map.of();}
}
