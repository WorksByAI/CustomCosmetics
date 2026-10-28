// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessoryhelper;

import com.customcosmetics.config.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/** Same upstream exclusion field used by Skyblocker. No invented or bundled item blacklist. */
public final class AccessoryExclusions {
    public record Snapshot(boolean ready,Set<String> ignored){public Snapshot{ignored=Set.copyOf(ignored);}}
    private static final int LIMIT=2*1024*1024;
    private static Path cache(){return CosmeticsConfigDir.getCosmeticsConfigDir().resolve("accessory-exclusions.json");}
    private static final URI SOURCE=URI.create("https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/misc.json");
    private static Snapshot snapshot=new Snapshot(false,Set.of());
    private static boolean loading;private static long nextAttempt;
    private AccessoryExclusions(){}
    public static Snapshot snapshot(){return snapshot;}
    static Snapshot parse(String json){
        var root=JsonParser.parseString(json).getAsJsonObject();var value=root.get("ignored_talisman");
        if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()>2048)throw new IllegalArgumentException("Missing NEU ignored_talisman data");
        var ignored=new HashSet<String>();for(var entry:value.getAsJsonArray()){
            if(!entry.isJsonPrimitive()||!entry.getAsJsonPrimitive().isString()||!entry.getAsString().matches("[A-Z0-9_;-]{2,100}"))throw new IllegalArgumentException("Invalid accessory exclusion ID");ignored.add(entry.getAsString());
        }
        return new Snapshot(true,ignored);
    }
    public static void request(){
        if(loading||System.currentTimeMillis()<nextAttempt)return;loading=true;
        CompletableFuture.runAsync(()->{
            try{
                Path cache=cache();long modified=0;
                if(Files.isRegularFile(cache))try{
                    byte[] bytes;try(var in=Files.newInputStream(cache)){bytes=in.readNBytes(LIMIT+1);}if(bytes.length>LIMIT)throw new java.io.IOException("Exclusion cache too large");
                    var cached=parse(new String(bytes,StandardCharsets.UTF_8));Minecraft.getInstance().execute(()->snapshot=cached);modified=Files.getLastModifiedTime(cache).toMillis();
                }catch(Exception invalid){com.customcosmetics.client.CustomCosmeticsClient.LOGGER.warn("Accessory exclusion cache is invalid; refreshing public metadata");}
                if(ConfigManager.offlineMode||modified>System.currentTimeMillis()-Duration.ofHours(24).toMillis())return;
                byte[] bytes;
                try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build()){
                    var response=client.send(HttpRequest.newBuilder(SOURCE).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                    try(var body=response.body()){bytes=body.readNBytes(LIMIT+1);}if(response.statusCode()!=200||bytes.length>LIMIT)throw new java.io.IOException("Exclusion feed unavailable");
                }
                var loaded=parse(new String(bytes,StandardCharsets.UTF_8));Files.createDirectories(cache.getParent());var temporary=Files.createTempFile(cache.getParent(),"accessory-exclusions-",".tmp");
                try{Files.write(temporary,bytes);try{Files.move(temporary,cache,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,cache,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temporary);}
                Minecraft.getInstance().execute(()->snapshot=loaded);
            }catch(Exception unavailable){com.customcosmetics.client.CustomCosmeticsClient.LOGGER.warn("Accessory exclusions unavailable; keeping the last verified list: {}",unavailable.getMessage());}
            finally{Minecraft.getInstance().execute(()->{loading=false;nextAttempt=System.currentTimeMillis()+Duration.ofMinutes(10).toMillis();});}
        });
    }
}
