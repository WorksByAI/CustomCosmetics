package com.customcosmetics.sync;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PresenceHttpsTest {
    @TempDir static Path directory;
    private static SSLContext serverTls;
    private static SSLContext trustedClientTls;
    private HttpsServer server;
    private final AtomicBoolean reachedBackend = new AtomicBoolean();
    private boolean originalSni;
    private String originalHost;

    @BeforeAll
    static void createLocalTestCertificate() throws Exception {
        Path keytool = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool");
        Path storePath = directory.resolve("localhost.p12");
        String password = "local-test-only";
        Process generation = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "localhost",
            "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost",
            "-validity", "2", "-storetype", "PKCS12", "-keystore", storePath.toString(),
            "-storepass", password, "-noprompt").redirectErrorStream(true).start();
        try {
            assertTrue(generation.waitFor(20, TimeUnit.SECONDS), "Local test certificate generation timed out");
            String output = new String(generation.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, generation.exitValue(), output);
        } finally { if (generation.isAlive()) generation.destroyForcibly(); }
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(storePath)) { store.load(input, password.toCharArray()); }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, password.toCharArray());
        serverTls = SSLContext.getInstance("TLS");
        serverTls.init(keys.getKeyManagers(), null, null);
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        trustedClientTls = SSLContext.getInstance("TLS");
        trustedClientTls.init(null, trust.getTrustManagers(), null);
    }

    @BeforeEach
    void startHttpsBackend() throws Exception {
        originalSni = com.customcosmetics.config.ConfigManager.syncServerSni;
        originalHost = com.customcosmetics.config.ConfigManager.syncServerIp;
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverTls));
        server.createContext("/health", exchange -> {
            reachedBackend.set(true);
            byte[] body = "{\"ok\":true,\"authVersion\":2}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopHttpsBackend() {
        if (server != null) server.stop(0);
        com.customcosmetics.config.ConfigManager.syncServerSni = originalSni;
        com.customcosmetics.config.ConfigManager.syncServerIp = originalHost;
    }

    @Test void sniCompatibilityRetainsCertificateTrustAndHostnameVerification() throws Exception {
        for (String host : new String[]{"localhost", "127.0.0.1"}) {
            com.customcosmetics.config.ConfigManager.syncServerIp = host;
            com.customcosmetics.config.ConfigManager.syncServerSni = false;
            HttpsURLConnection connection = open(host, true);
            var verifier = connection.getHostnameVerifier();
            SyncTls.configure(connection);
            assertSame(verifier, connection.getHostnameVerifier());
            try (var socket = (javax.net.ssl.SSLSocket) connection.getSSLSocketFactory().createSocket()) {
                assertEquals(java.util.List.of(), socket.getSSLParameters().getServerNames());
            }
            try {
                if (host.equals("localhost")) assertEquals(200, connection.getResponseCode());
                else assertThrows(IOException.class, connection::getResponseCode);
            } finally { connection.disconnect(); }
        }
        com.customcosmetics.config.ConfigManager.syncServerIp = "localhost";
        HttpsURLConnection untrusted = open("localhost", false);
        SyncTls.configure(untrusted);
        try { assertThrows(IOException.class, untrusted::getResponseCode); }
        finally { untrusted.disconnect(); }
    }

    @Test void sniCompatibilityDoesNotAffectUnrelatedHostsOrExplicitSniEnabledConnections() throws Exception {
        HttpsURLConnection connection = open("localhost", true);
        var factory = connection.getSSLSocketFactory();
        com.customcosmetics.config.ConfigManager.syncServerIp = "other.example";
        com.customcosmetics.config.ConfigManager.syncServerSni = false;
        SyncTls.configure(connection); assertSame(factory, connection.getSSLSocketFactory());
        com.customcosmetics.config.ConfigManager.syncServerIp = "localhost";
        com.customcosmetics.config.ConfigManager.syncServerSni = true;
        SyncTls.configure(connection); assertSame(factory, connection.getSSLSocketFactory());
        connection.disconnect();
    }

    @Test
    void readsHttpsJsonThroughTheProductionConnectionGuard() throws Exception {
        HttpsURLConnection connection = open("localhost", true);
        try {
            assertFalse(connection.getInstanceFollowRedirects());
            assertEquals(200, connection.getResponseCode());
            try (var input = connection.getInputStream()) {
                var response = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                assertTrue(response.get("ok").getAsBoolean());
                assertEquals(2, response.get("authVersion").getAsInt());
            }
            assertTrue(reachedBackend.get());
        } finally { connection.disconnect(); }
    }

    @Test
    void defaultTrustRejectsUntrustedCertificates() throws Exception {
        HttpsURLConnection connection = open("localhost", false);
        try {
            assertThrows(IOException.class, connection::getResponseCode);
            assertFalse(reachedBackend.get());
        } finally { connection.disconnect(); }
    }

    @Test
    void keepsHostnameVerificationEvenWithATrustedCertificate() throws Exception {
        HttpsURLConnection connection = open("127.0.0.1", true);
        try {
            assertThrows(IOException.class, connection::getResponseCode);
            assertFalse(reachedBackend.get());
        } finally { connection.disconnect(); }
    }

    private HttpsURLConnection open(String host, boolean trustTestCertificate) throws Exception {
        String url = "https://" + host + ":" + server.getAddress().getPort() + "/health";
        HttpsURLConnection connection = (HttpsURLConnection) PresenceAuthClient.openServiceConnection(url,
            request -> (HttpsURLConnection) URI.create(request).toURL().openConnection(Proxy.NO_PROXY));
        // Trust only this generated certificate on this connection; production TLS defaults stay untouched.
        if (trustTestCertificate) connection.setSSLSocketFactory(trustedClientTls.getSocketFactory());
        connection.setConnectTimeout(3_000);
        connection.setReadTimeout(3_000);
        return connection;
    }
}
