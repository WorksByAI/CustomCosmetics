// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.config.ConfigManager;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresenceSnapshotTest {
    @Test void capeSnapshotIgnoresMalformedOptionalReviewMetadata() {
        var payload=new JsonObject();var capes=new JsonObject();var id=new UUID(0,1);String hash="a".repeat(64);
        capes.addProperty(id.toString(),hash);payload.add("capes",capes);payload.add("capeReview",new JsonObject());
        var snapshot=CapeSync.prepareSnapshot(payload);
        capes.addProperty(id.toString(),"b".repeat(64));
        assertEquals(hash,snapshot.hashes().get(id));assertEquals(0,snapshot.pending());assertEquals(0,snapshot.revision());
        assertThrows(UnsupportedOperationException.class,()->snapshot.hashes().clear());
    }

    @Test void largePayloadCanDecodeOffThreadWithoutPublishingOrRetainingMutableJson() throws Exception {
        var payload=new JsonObject();payload.addProperty("authVersion",2);
        var users=new JsonObject();payload.add("users",users);
        for(int i=0;i<1000;i++){
            var user=new JsonObject();user.addProperty("name","Player"+i);
            var features=new JsonObject();var rank=new JsonObject();rank.addProperty("enabled",true);rank.addProperty("plusColor","gold");
            var nick=new JsonObject();nick.add("rank",rank);features.add("nickTweaks",nick);user.add("features",features);
            users.add(new UUID(0,i+1).toString(),user);
        }
        Object before=PresenceSync.appearanceSnapshot();
        Method decode=PresenceSync.class.getDeclaredMethod("decodePayload",JsonObject.class);decode.setAccessible(true);
        Object decoded=CompletableFuture.supplyAsync(()->{
            try{return decode.invoke(null,payload);}catch(Exception failure){throw new AssertionError(failure);}
        }).get(10,TimeUnit.SECONDS);
        assertSame(before,PresenceSync.appearanceSnapshot(),"Worker decoding published client render state");
        Method getUsers=decoded.getClass().getDeclaredMethod("users");getUsers.setAccessible(true);
        @SuppressWarnings("unchecked") var snapshot=(Map<UUID,PresenceSync.RemoteUserState>)getUsers.invoke(decoded);
        assertEquals(1000,snapshot.size());
        users.getAsJsonObject(new UUID(0,1).toString()).addProperty("name","Changed");
        assertEquals("Player0",snapshot.get(new UUID(0,1)).name());
        assertThrows(UnsupportedOperationException.class,snapshot::clear);
    }

    @Test void cacheWritesCoalesceWithoutBlockingClientAndOldEndpointsAreDiscarded() throws Exception {
        boolean oldOffline=ConfigManager.offlineMode;ConfigManager.offlineMode=false;
        var field=PresenceSync.class.getDeclaredField("ENDPOINT_VERSION");field.setAccessible(true);
        var version=(AtomicLong)field.get(null);long original=version.get();
        Method schedule=PresenceSync.class.getDeclaredMethod("cacheLater",long.class,Runnable.class);schedule.setAccessible(true);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var stale=new AtomicInteger();var superseded=new AtomicInteger();
        try {
            schedule.invoke(null,original,(Runnable)()->{
                entered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();}
            });
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            schedule.invoke(null,original,(Runnable)superseded::incrementAndGet);
            schedule.invoke(null,original,(Runnable)finished::countDown);
            release.countDown();assertTrue(finished.await(5,TimeUnit.SECONDS));
            assertEquals(0,superseded.get(),"Cache work accumulated instead of retaining the newest snapshot");
            version.incrementAndGet();
            schedule.invoke(null,original,(Runnable)stale::incrementAndGet);
            var writer=PresenceSync.class.getDeclaredField("CACHE_WRITER");writer.setAccessible(true);
            ((java.util.concurrent.ExecutorService)writer.get(null)).submit(()->{}).get(5,TimeUnit.SECONDS);
            assertEquals(0,stale.get());
        } finally {release.countDown();version.set(original);ConfigManager.offlineMode=oldOffline;}
    }
}
