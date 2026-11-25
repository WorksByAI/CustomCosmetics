package com.customcosmetics.update;

import com.customcosmetics.client.ClientEdition;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpdateIoTest {
    @Test void deadlineAndCancellationInterruptTheWorkerAndReleaseItsResources()throws Exception{
        for(boolean cancel:new boolean[]{false,true}){
            var entered=new CountDownLatch(1);var closed=new CountDownLatch(1);
            var future=UpdateIo.submit(()->{
                entered.countDown();try{new CountDownLatch(1).await();return true;}finally{closed.countDown();}
            },Duration.ofMillis(cancel?10000:200));
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            if(cancel)assertTrue(future.cancel(true));
            else{var error=assertThrows(ExecutionException.class,()->future.get(2,TimeUnit.SECONDS));assertInstanceOf(SocketTimeoutException.class,error.getCause());}
            assertTrue(closed.await(2,TimeUnit.SECONDS),"Timed-out or cancelled update kept running");
        }
    }
    @Test void aSlowTrickleCannotKeepAReleaseCheckAlivePastItsOverallDeadline()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setExecutor(Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()));
        server.createContext("/latest",exchange->{
            try{
                exchange.sendResponseHeaders(200,0);
                for(int n=0;n<300;n++){exchange.getResponseBody().write(' ');exchange.getResponseBody().flush();Thread.sleep(100);}
            }catch(Exception disconnected){}finally{exchange.close();}
        });server.start();
        try{
            URI endpoint=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/latest");
            assertTimeoutPreemptively(Duration.ofSeconds(13),()->assertThrows(SocketTimeoutException.class,
                ()->GitHubUpdateCheck.fetch(endpoint,"1.0.0",ClientEdition.LEGIT)));
        }finally{server.stop(0);((ExecutorService)server.getExecutor()).shutdownNow();}
    }
}
