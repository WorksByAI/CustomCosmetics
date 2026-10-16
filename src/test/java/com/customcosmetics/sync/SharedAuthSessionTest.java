// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SharedAuthSessionTest {
    private static final String BASE="https://example.com";
    private static final UUID ID=UUID.fromString("12345678-1234-4234-8234-123456789abc");
    private static PresenceAuthClient.Session session(String base,UUID uuid,String name,long lifetime){
        return new PresenceAuthClient.Session("a".repeat(64),uuid,name,System.currentTimeMillis()+lifetime,base);
    }

    @Test void concurrentCosmeticsPartyAndRealtimeConsumersUseOneProofAndTheSameSessionObject()throws Exception{
        try(var pool=Executors.newFixedThreadPool(8)){
            var cache=new SharedAuthSession(pool);var calls=new AtomicInteger();var release=new CountDownLatch(1);
            var verified=session(BASE,ID,"Alice",60_000);
            SharedAuthSession.Authenticate authenticate=()->{calls.incrementAndGet();assertTrue(release.await(5,TimeUnit.SECONDS));return verified;};
            var callers=new java.util.ArrayList<CompletableFuture<PresenceAuthClient.Session>>();
            for(int i=0;i<20;i++)callers.add(CompletableFuture.supplyAsync(()->cache.get(BASE,ID,"Alice",authenticate),pool).thenCompose(value->value));
            release.countDown();
            for(var caller:callers)assertSame(verified,caller.get(5,TimeUnit.SECONDS));
            assertEquals(1,calls.get());assertSame(verified,cache.get(BASE,ID,"alice",()->{throw new AssertionError("Reauthenticated a valid session");}).join());
        }
    }

    @Test void cancellingOneCallerDoesNotCancelAuthenticationForOtherChannels(){
        var work=new ArrayDeque<Runnable>();var cache=new SharedAuthSession(work::add);var verified=session(BASE,ID,"Alice",60_000);
        var party=cache.get(BASE,ID,"Alice",()->verified);var realtime=cache.get(BASE,ID,"Alice",()->{throw new AssertionError();});
        party.cancel(true);work.remove().run();assertSame(verified,realtime.join());assertEquals(0,work.size());
    }

    @Test void aFailedProofIsSharedButDoesNotPreventAnAutomaticRetry(){
        var work=new ArrayDeque<Runnable>();var cache=new SharedAuthSession(work::add);
        var first=cache.get(BASE,ID,"Alice",()->{throw new IOException("invalid Minecraft session");});
        var second=cache.get(BASE,ID,"Alice",()->{throw new AssertionError();});work.remove().run();
        assertEquals("invalid Minecraft session",assertThrows(CompletionException.class,first::join).getCause().getMessage());
        assertThrows(CompletionException.class,second::join);
        var verified=session(BASE,ID,"Alice",60_000);var retry=cache.get(BASE,ID,"Alice",()->verified);work.remove().run();assertSame(verified,retry.join());
    }

    @Test void switchingModeOrEndpointCannotRestoreAnOldPendingSession(){
        var work=new ArrayDeque<Runnable>();var cache=new SharedAuthSession(work::add);
        var old=cache.get(BASE,ID,"Alice",()->session(BASE,ID,"Alice",60_000));cache.reset();
        String other="https://other.example.com";var verified=session(other,ID,"Alice",60_000);
        var current=cache.get(other,ID,"Alice",()->verified);
        work.remove().run();assertThrows(CompletionException.class,old::join);assertNull(cache.current(BASE,ID,"Alice"));
        work.remove().run();assertSame(verified,current.join());assertSame(verified,cache.current(other,ID,"Alice"));
    }

    @Test void expiredWrongAccountOrWrongServerSessionsAreNeverReused(){
        var cache=new SharedAuthSession(Runnable::run);
        assertThrows(CompletionException.class,()->cache.get(BASE,ID,"Alice",()->session(BASE,ID,"Alice",1000)).join());
        var first=session(BASE,ID,"Alice",60_000);assertSame(first,cache.get(BASE,ID,"Alice",()->first).join());
        UUID other=UUID.randomUUID();var second=session(BASE,other,"Bob",60_000);assertSame(second,cache.get(BASE,other,"Bob",()->second).join());
        cache.invalidate(first);assertSame(second,cache.current(BASE,other,"Bob"));
        assertNull(cache.current(BASE,ID,"Alice"));assertNull(cache.current("https://other.example.com",other,"Bob"));
        cache.invalidate(second);assertNull(cache.current(BASE,other,"Bob"));
    }
}
