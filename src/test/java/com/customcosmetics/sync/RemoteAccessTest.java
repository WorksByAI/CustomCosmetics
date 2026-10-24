// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

class RemoteAccessTest {
    @Test void slowChannelConstructionDoesNotHoldTheGlobalLockAndCannotSurviveModeChanges() throws Exception {
        RemoteAccess.offline(false);
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var closed=new AtomicInteger();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var creating=executor.submit(()->RemoteAccess.channel(()->{
                entered.countDown();assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));
                return (AutoCloseable)closed::incrementAndGet;
            }));
            try {
                assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
                executor.submit(()->{RemoteAccess.offline(true);RemoteAccess.offline(false);})
                    .get(2,java.util.concurrent.TimeUnit.SECONDS);
            } finally {release.countDown();}
            var failure=assertThrows(java.util.concurrent.ExecutionException.class,
                ()->creating.get(5,java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(IOException.class,failure.getCause());assertEquals(1,closed.get());
        } finally {release.countDown();}
    }
    @AfterEach void restore(){RemoteAccess.offline(false);}
    @Test void offlineClosesAllIoAndOldRequestsRemainInvalidAfterReturningOnline() throws Exception {
        RemoteAccess.offline(false);var raw=new Fake();var connection=RemoteAccess.manage(raw);
        assertEquals(200,connection.getResponseCode());long before=RemoteAccess.generation();
        var channels=new AtomicInteger();RemoteAccess.channel(()->(AutoCloseable)channels::incrementAndGet);
        RemoteAccess.offline(true);assertTrue(raw.closed);assertEquals(1,channels.get());assertFalse(RemoteAccess.current(before));
        assertThrows(IOException.class,connection::getResponseCode);
        assertThrows(IOException.class,()->RemoteAccess.manage(new Fake()));
        assertThrows(IOException.class,()->RemoteAccess.channel(()->{throw new AssertionError("Offline mode created a socket");}));
        RemoteAccess.offline(false);assertThrows(IOException.class,connection::getResponseCode);
        assertEquals(200,RemoteAccess.manage(new Fake()).getResponseCode());
    }
    @Test void managedRequestsRetainMethodHeadersBodyAndTimeoutSettings()throws Exception{
        var raw=new Fake();var connection=RemoteAccess.manage(raw);
        connection.setRequestMethod("POST");connection.setRequestProperty("Accept","application/json");
        connection.setDoOutput(true);connection.setReadTimeout(2500);connection.setInstanceFollowRedirects(false);
        connection.getOutputStream().write("body".getBytes());
        assertEquals("POST",raw.getRequestMethod());assertEquals("application/json",raw.getRequestProperty("Accept"));
        assertEquals(2500,raw.getReadTimeout());assertFalse(raw.getInstanceFollowRedirects());
        assertEquals("body",raw.body.toString());connection.disconnect();assertTrue(raw.closed);
    }
    private static final class Fake extends HttpURLConnection {
        boolean closed;ByteArrayOutputStream body=new ByteArrayOutputStream();
        Fake()throws Exception{super(URI.create("http://localhost:8123").toURL());}
        @Override public void connect(){} @Override public void disconnect(){closed=true;}
        @Override public boolean usingProxy(){return false;}
        @Override public int getResponseCode(){return 200;}
        @Override public OutputStream getOutputStream(){return body;}
    }
}
