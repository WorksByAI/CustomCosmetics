// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessoryhelper;
import com.customcosmetics.config.*;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/** Optional display feed, requested only when the enabled bag panel opens. */
public final class AccessoryItems {
    private static final int LIMIT=16*1024*1024;
    private static final Path CACHE=CosmeticsConfigDir.getCosmeticsConfigDir().resolve("accessory-items.json");
    private static Map<String,AccessoryItemCatalog.Item> items=Map.of();
    private static boolean loading;
    private static long nextAttempt;
    private AccessoryItems(){}
    public static Map<String,AccessoryItemCatalog.Item> items(){return items;}
    public static void request(AccessoryCatalog catalog){
        if(loading||catalog.size()==0||System.currentTimeMillis()<nextAttempt)return;
        loading=true;boolean offline=ConfigManager.offlineMode;
        CompletableFuture.runAsync(()->{
            try{
                long modified=0;
                if(Files.isRegularFile(CACHE)){
                    try{
                    byte[] bytes;try(var in=Files.newInputStream(CACHE)){bytes=in.readNBytes(LIMIT+1);}if(bytes.length>LIMIT)throw new java.io.IOException("Item cache too large");
                    var cached=AccessoryItemCatalog.parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),catalog);
                    Minecraft.getInstance().execute(()->items=cached);modified=Files.getLastModifiedTime(CACHE).toMillis();
                    }catch(Exception invalidCache){com.customcosmetics.client.CustomCosmeticsClient.LOGGER.warn("Accessory display cache is invalid; trying the public feed: {}",invalidCache.getMessage());}
                }
                if(offline||modified>System.currentTimeMillis()-Duration.ofHours(24).toMillis())return;
                var request=HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/resources/skyblock/items")).timeout(Duration.ofSeconds(20)).GET().build();
                var response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build().send(request,HttpResponse.BodyHandlers.ofInputStream());
                byte[] bytes;try(var in=response.body()){bytes=in.readNBytes(LIMIT+1);}if(response.statusCode()!=200||bytes.length>LIMIT)throw new java.io.IOException("Item feed unavailable");
                var loaded=AccessoryItemCatalog.parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),catalog);if(loaded.isEmpty())throw new java.io.IOException("Empty accessory display feed");
                Files.createDirectories(CACHE.getParent());Path temporary=Files.createTempFile(CACHE.getParent(),"accessory-items-",".tmp");
                try{Files.write(temporary,bytes);try{Files.move(temporary,CACHE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,CACHE,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temporary);}
                Minecraft.getInstance().execute(()->items=loaded);
            }catch(Exception failure){com.customcosmetics.client.CustomCosmeticsClient.LOGGER.warn("Accessory display feed unavailable; using cached names or IDs: {}",failure.getMessage());}
            finally{Minecraft.getInstance().execute(()->{loading=false;nextAttempt=System.currentTimeMillis()+Duration.ofMinutes(10).toMillis();});}
        });
    }
}
