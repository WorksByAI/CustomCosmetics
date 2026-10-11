package com.customcosmetics.sync;

import com.customcosmetics.config.ConfigManager;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class PresenceProxyResolverTest {
    private final Map<Field,Object> settings = new HashMap<>();
    @BeforeEach void isolate() throws Exception {
        for (var field : ConfigManager.class.getFields()) if (Modifier.isStatic(field.getModifiers())) settings.put(field,field.get(null));
        PresenceProxyResolver.resetSession(); ConfigManager.syncProxyHost = ""; ConfigManager.syncProxyPort = 0; ConfigManager.syncProxyAuth = "";
        ConfigManager.syncProxyFallbackDirect = true; ConfigManager.syncProxySource = "none";
    }
    @AfterEach void restore() throws Exception { for (var entry : settings.entrySet()) entry.getKey().set(null,entry.getValue()); PresenceProxyResolver.resetSession(); }
    private HttpServer server(int status,AtomicReference<String> auth,AtomicInteger calls) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            calls.incrementAndGet(); auth.set(exchange.getRequestHeaders().getFirst("Proxy-Authorization"));
            byte[] bytes = "{}".getBytes(); exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start(); return server;
    }
    private int read(String url,int attempt) throws Exception {
        var connection = PresenceProxyResolver.openConnection(url,attempt);
        connection.setConnectTimeout(1000); connection.setReadTimeout(1000);
        try { int status = connection.getResponseCode(); var stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            if (stream != null) try (stream) { stream.readAllBytes(); } return status;
        } finally { connection.disconnect(); }
    }
    @Test void manualProxyReceivesAuthAndDirectFallbackDoesNotLeakIt() throws Exception {
        var proxyAuth = new AtomicReference<String>(); var directAuth = new AtomicReference<String>();
        var proxy = server(200,proxyAuth,new AtomicInteger()); var origin = server(200,directAuth,new AtomicInteger());
        try {
            ConfigManager.syncProxyHost = "127.0.0.1"; ConfigManager.syncProxyPort = proxy.getAddress().getPort();
            ConfigManager.syncProxyAuth = "fixture:secret"; ConfigManager.syncProxySource = "manual";
            ConfigManager.syncServerIp = "127.0.0.1"; ConfigManager.syncServerPort = origin.getAddress().getPort(); ConfigManager.syncServerHttps = false;
            assertEquals(200,read("http://unresolved.invalid/status",0));
            assertEquals("Basic " + Base64.getEncoder().encodeToString("fixture:secret".getBytes()),proxyAuth.get());
            assertEquals(200,read("http://127.0.0.1:"+origin.getAddress().getPort()+"/health",1)); assertNull(directAuth.get());
        } finally { proxy.stop(0); origin.stop(0); }
    }
    @Test void noneModeDoesNotUseTheConfiguredProxyOrSendItsCredentials() throws Exception {
        var auth = new AtomicReference<String>(); var origin = server(200,auth,new AtomicInteger());
        try { ConfigManager.syncProxyHost = "127.0.0.1"; ConfigManager.syncProxyPort = 1; ConfigManager.syncProxyAuth = "fixture:secret";
            assertEquals(200,read("http://127.0.0.1:"+origin.getAddress().getPort()+"/health",0)); assertNull(auth.get());
        } finally { origin.stop(0); }
    }
    @Test void manualFailureWithoutFallbackDoesNotSilentlySwitchOrEraseTheSettings() throws Exception {
        var originCalls = new AtomicInteger(); var proxy = server(500,new AtomicReference<>(),new AtomicInteger());
        var origin = server(200,new AtomicReference<>(),originCalls);
        try { ConfigManager.syncProxyHost = "127.0.0.1"; ConfigManager.syncProxyPort = proxy.getAddress().getPort();
            ConfigManager.syncProxySource = "manual"; ConfigManager.syncProxyFallbackDirect = false;
            String target = "http://127.0.0.1:"+origin.getAddress().getPort()+"/health";
            assertFalse(PresenceProxyResolver.establishSession(target)); assertEquals(500,read(target,1)); assertEquals(0,originCalls.get());
            assertEquals("manual",ConfigManager.syncProxySource); assertEquals(proxy.getAddress().getPort(),ConfigManager.syncProxyPort);
        } finally { proxy.stop(0); origin.stop(0); }
    }
    @Test void autoModeKeepsTheUsersChoiceAfterFindingADirectRoute() throws Exception {
        var origin = server(200,new AtomicReference<>(),new AtomicInteger());
        try { ConfigManager.syncProxySource = "auto"; assertTrue(PresenceProxyResolver.establishSession("http://127.0.0.1:"+origin.getAddress().getPort()+"/health"));
            assertEquals("auto",ConfigManager.syncProxySource); assertEquals("",ConfigManager.syncProxyHost); assertEquals(0,ConfigManager.syncProxyPort);
        } finally { origin.stop(0); }
    }
    @Test void autoModeUsesTheDetectedRouteOnItsFirstRequestAfterAnUnhealthyProxy() throws Exception {
        var origin = server(200,new AtomicReference<>(),new AtomicInteger()); var proxy = server(500,new AtomicReference<>(),new AtomicInteger());
        try {
            ConfigManager.syncServerIp = "127.0.0.1"; ConfigManager.syncServerPort = origin.getAddress().getPort(); ConfigManager.syncServerHttps = false;
            ConfigManager.syncProxySource = "auto"; ConfigManager.syncProxyHost = "127.0.0.1"; ConfigManager.syncProxyPort = proxy.getAddress().getPort();
            assertEquals(200,read(SyncEndpoint.baseUrl()+"/health",0));
            assertEquals("auto",ConfigManager.syncProxySource); assertEquals(proxy.getAddress().getPort(),ConfigManager.syncProxyPort);
        } finally { origin.stop(0); proxy.stop(0); }
    }
    @Test void manualModeWithFallbackCanReachTheOriginAfterProxyFailure() throws Exception {
        var directAuth = new AtomicReference<String>(); var origin = server(200,directAuth,new AtomicInteger());
        var proxy = server(500,new AtomicReference<>(),new AtomicInteger());
        try {
            ConfigManager.syncServerIp = "127.0.0.1"; ConfigManager.syncServerPort = origin.getAddress().getPort(); ConfigManager.syncServerHttps = false;
            ConfigManager.syncProxySource = "manual"; ConfigManager.syncProxyHost = "127.0.0.1"; ConfigManager.syncProxyPort = proxy.getAddress().getPort();
            ConfigManager.syncProxyAuth = "fixture:secret"; assertEquals(200,read(SyncEndpoint.baseUrl()+"/health",0)); assertNull(directAuth.get());
            assertEquals("manual",ConfigManager.syncProxySource); assertEquals(proxy.getAddress().getPort(),ConfigManager.syncProxyPort);
        } finally { origin.stop(0); proxy.stop(0); }
    }
    @Test void automaticRoutesDoNotSendManualCredentialsToADifferentProxy() throws Exception {
        var alternateAuth = new AtomicReference<String>();
        var origin = server(500,new AtomicReference<>(),new AtomicInteger());
        var configured = server(500,new AtomicReference<>(),new AtomicInteger());
        var alternate = server(200,alternateAuth,new AtomicInteger());
        String oldHost = System.getProperty("https.proxyHost"), oldPort = System.getProperty("https.proxyPort");
        try {
            ConfigManager.syncServerIp = "127.0.0.1"; ConfigManager.syncServerPort = origin.getAddress().getPort(); ConfigManager.syncServerHttps = false;
            ConfigManager.syncProxySource = "auto"; ConfigManager.syncProxyHost = "127.0.0.1"; ConfigManager.syncProxyPort = configured.getAddress().getPort();
            ConfigManager.syncProxyAuth = "fixture:secret";
            System.setProperty("https.proxyHost","127.0.0.1"); System.setProperty("https.proxyPort",Integer.toString(alternate.getAddress().getPort()));
            assertEquals(200,read(SyncEndpoint.baseUrl()+"/health",0)); assertNull(alternateAuth.get());
        } finally {
            if (oldHost == null) System.clearProperty("https.proxyHost"); else System.setProperty("https.proxyHost",oldHost);
            if (oldPort == null) System.clearProperty("https.proxyPort"); else System.setProperty("https.proxyPort",oldPort);
            origin.stop(0); configured.stop(0); alternate.stop(0);
        }
    }
}
