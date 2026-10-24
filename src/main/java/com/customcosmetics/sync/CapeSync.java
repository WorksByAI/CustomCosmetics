// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.features.cape.*;
import com.customcosmetics.config.CosmeticsConfigDir;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.*;

/** Only Sync's read cycle downloads capes. Only explicit review submission uploads a cape. */
public final class CapeSync {
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger("CustomCosmetics/CapeSync");
    private static final AtomicBoolean submitting=new AtomicBoolean(),downloading=new AtomicBoolean();
    private static volatile long nextSubmissionAt;
    private static volatile String status="Not submitted";
    private static long notifiedRevision;
    private CapeSync(){}
    public static String status(){return status;}
    public static long cooldownSeconds(){return Math.max(0,(nextSubmissionAt-System.currentTimeMillis()+999)/1000);}
    public static boolean canSubmit(){return RemoteAccess.online()&&CapeCosmetics.localPng()!=null&&!submitting.get()&&cooldownSeconds()==0;}
    public static void modeChanged(){CapeCosmetics.clearRemote();}
    record Snapshot(Map<UUID,String> hashes,int pending,long revision) {}
    static Snapshot prepareSnapshot(JsonObject snapshot){
        var hashes=new HashMap<UUID,String>();
        if(snapshot.has("capes")&&snapshot.get("capes").isJsonObject())for(var entry:snapshot.getAsJsonObject("capes").entrySet()){
            try{String hash=entry.getValue().getAsString();if(hash.matches("[0-9a-f]{64}"))hashes.put(UUID.fromString(entry.getKey()),hash);}catch(RuntimeException ignored){}
        }
        int pending=0;long revision=0;
        if(snapshot.has("capeReview")&&snapshot.get("capeReview").isJsonObject()){
            try{var review=snapshot.getAsJsonObject("capeReview");pending=review.get("pending").getAsInt();revision=review.get("revision").getAsLong();}
            catch(RuntimeException ignored){pending=0;revision=0;}
        }
        return new Snapshot(Map.copyOf(hashes),pending,revision);
    }
    public static void acceptSnapshot(JsonObject snapshot,String base){
        if(!RemoteAccess.online())return;
        acceptSnapshot(prepareSnapshot(snapshot),base);
    }
    static void acceptSnapshot(Snapshot snapshot,String base){
        if(!RemoteAccess.online())return;
        var hashes=snapshot.hashes();
        CapeCosmetics.approved(hashes);
        var client=Minecraft.getInstance();var identity=PresenceSync.actionSession();
        if(identity!=null&&CapeAdministrators.includes(identity.uuid())){
            int pending=snapshot.pending();long revision=snapshot.revision();
            if(pending>0&&revision>notifiedRevision){notifiedRevision=revision;CapeCosmetics.reviewNotice(pending,base);}
        }
        var wanted=new LinkedHashSet<String>();if(client.player!=null&&hashes.containsKey(client.player.getUUID()))wanted.add(hashes.get(client.player.getUUID()));
        if(client.level!=null)for(var player:client.level.players())if(hashes.containsKey(player.getUUID())&&wanted.size()<32)wanted.add(hashes.get(player.getUUID()));
        long generation=RemoteAccess.generation();
        if(!downloading.compareAndSet(false,true))return;
        CompletableFuture.runAsync(()->{
            try{
                if(identity!=null&&CapeCosmetics.localPng()!=null&&RemoteAccess.current(generation)){
                    try{JsonObject response=requestJson(base+"/capes/status","GET",null,identity.token());if(RemoteAccess.current(generation)&&!submitting.get())updateStatus(response);}catch(Exception ignored){}
                }
                for(String hash:wanted){
                    if(!RemoteAccess.current(generation)||!PresenceSync.serviceBaseUrl().equals(base))return;
                    if(CapeCosmetics.loaded(hash))continue;
                    try{
                        var directory=CosmeticsConfigDir.getCosmeticsConfigDir().resolve("cape-cache");Files.createDirectories(directory);var path=directory.resolve(hash+".png");
                        byte[] bytes=Files.exists(path)&&Files.size(path)<=CapeImages.MAX_PNG?Files.readAllBytes(path):download(base+"/cape/"+hash+".png");
                        if(!CapeImages.hash(bytes).equals(hash))throw new IOException("Cape checksum mismatch.");
                        CapeImages.atlasPng(CapeImages.decode(bytes));
                        if(!RemoteAccess.current(generation))return;Files.write(path,bytes);
                        client.submit(()->{if(RemoteAccess.current(generation)&&PresenceSync.serviceBaseUrl().equals(base))try{CapeCosmetics.texture(bytes);}catch(IOException ignored){}}).join();
                    }catch(Exception ignored){/* Retry changed/missing textures at the next regular sync. */}
                }
            }finally{downloading.set(false);}
        });
    }
    public static boolean submit(){
        if(!canSubmit()||!submitting.compareAndSet(false,true))return false;
        byte[] png=CapeCosmetics.localPng();String base=PresenceSync.serviceBaseUrl();long generation=RemoteAccess.generation();
        long started=System.nanoTime();status="Authenticating account...";
        PresenceSync.authenticatedSession().thenAcceptAsync(identity->{
            if(!RemoteAccess.current(generation)||!PresenceSync.serviceBaseUrl().equals(base))return;
            try{
                status="Uploading cape...";
                var body=new JsonObject();body.addProperty("png",Base64.getEncoder().encodeToString(png));
                var response=requestJson(base+"/capes/submit","POST",body,identity.token());
                if(RemoteAccess.current(generation)){updateStatus(response);LOGGER.info("Cape submission accepted, bytes={}, elapsedMs={}",png.length,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));CapeCosmetics.notice("Cape submitted for review. You can submit again in 24 hours.");}
            }catch(Exception failure){if(RemoteAccess.current(generation))submissionFailed(failure,"upload",started);}
        }).exceptionally(failure->{if(RemoteAccess.current(generation))submissionFailed(failure,"authentication",started);return null;}).whenComplete((unused,failure)->submitting.set(false));
        return true;
    }
    private static void submissionFailed(Throwable failure,String phase,long started){
        boolean uncertain=phase.equals("upload")&&SyncFailure.fromException(failure,SyncFailure.Stage.UPLOAD).reason()==SyncFailure.Reason.TIMEOUT;
        status=uncertain?"Submission timed out; check review status with Sync":"Submission failed";
        String reason=safeFailure(failure);
        LOGGER.warn("Cape submission failed, phase={}, elapsedMs={}, reason={}",phase,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),reason);
        CapeCosmetics.notice("Cape submission failed during "+phase+": "+reason+(uncertain?". Review status will refresh with Sync.":""));
    }
    private static String safeFailure(Throwable error){while(error.getCause()!=null)error=error.getCause();return error instanceof CapeRequestFailure?error.getMessage():SyncFailure.fromException(error,SyncFailure.Stage.UPLOAD).description();}
    private static void updateStatus(JsonObject response){
        nextSubmissionAt=response.get("nextSubmissionAt").getAsLong();status=switch(response.get("status").getAsString()){case "pending"->"Pending review";case "approved"->"Approved";case "rejected"->"Rejected";default->"Not submitted";};
    }
    private static HttpURLConnection connection(String url,boolean submission)throws Exception{
        var connection=PresenceSync.openActionConnection(url);CapeRequestPolicy.configure(connection,submission);return connection;
    }
    private static JsonObject requestJson(String url,String method,JsonObject body,String token)throws Exception{
        HttpURLConnection connection=connection(url,method.equals("POST")&&url.endsWith("/capes/submit"));
        try{
            connection.setRequestMethod(method);connection.setRequestProperty("Authorization","Bearer "+token);
            if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");connection.setFixedLengthStreamingMode(bytes.length);try(var output=connection.getOutputStream()){output.write(bytes);}}
            int code=connection.getResponseCode();if(code<200||code>=300){
                if(code==429){long seconds=86400;try{seconds=Long.parseLong(connection.getHeaderField("Retry-After"));}catch(Exception ignored){}nextSubmissionAt=System.currentTimeMillis()+Math.clamp(seconds,1,86400)*1000;throw new CapeRequestFailure("daily cape submission limit (HTTP 429).");}
                throw new CapeRequestFailure(code==401?"invalid or expired sync token (HTTP 401).":code==403?"uploads are blocked for this account (HTTP 403).":code==413?"cape PNG must not exceed 2 MiB (HTTP 413).":"server rejected the cape (HTTP "+code+").");
            }
            try(var input=connection.getInputStream()){return JsonParser.parseString(new String(bounded(input,16384),StandardCharsets.UTF_8)).getAsJsonObject();}
        }finally{connection.disconnect();}
    }
    private static byte[] download(String url)throws Exception{
        var connection=connection(url,false);try{if(connection.getResponseCode()!=200)throw new IOException("Cape unavailable.");try(var input=connection.getInputStream()){return bounded(input,CapeImages.MAX_PNG);}}finally{connection.disconnect();}
    }
    private static byte[] bounded(InputStream input,int maximum)throws IOException{byte[] bytes=input.readNBytes(maximum+1);if(bytes.length>maximum)throw new IOException("Response too large.");return bytes;}
    private static final class CapeRequestFailure extends IOException {CapeRequestFailure(String message){super(message);}}
}
