package com.customcosmetics.sync;

import com.customcosmetics.features.blocking.BlockHand;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockingTransportTest {
    private static final UUID ALICE = UUID.fromString("12345678-1234-4234-8234-123456789abc");
    private static final String EMPTY = "{\"actionVersion\":1,\"ttlMs\":2500,\"users\":{}}";

    @Test void ttlStartsAtRequestTimeAndInvalidEntriesCannotStick() throws Exception {
        String data = "{\"actionVersion\":1,\"ttlMs\":2500,\"users\":{" +
            "\"" + ALICE + "\":{\"hand\":\"main\",\"remainingMs\":1000}," +
            "\"not-uuid\":{\"hand\":\"main\",\"remainingMs\":2500}," +
            "\"1-1-1-1-1\":{\"hand\":\"off\",\"remainingMs\":2500}," +
            "\"22345678-1234-4234-8234-123456789abc\":{\"hand\":\"off\",\"remainingMs\":2501}," +
            "\"32345678-1234-4234-8234-123456789abc\":{\"hand\":\"none\",\"remainingMs\":2000}," +
            "\"42345678-1234-4234-8234-123456789abc\":{\"hand\":\"off\",\"remainingMs\":0}," +
            "\"52345678-1234-4234-8234-123456789abc\":null}}";
        var poses = BlockingTransport.parse(JsonParser.parseString(data).getAsJsonObject(), 1234);
        assertEquals(1, poses.size());
        assertEquals(new BlockingTransport.Pose(BlockHand.MAIN, 2234), poses.get(ALICE));
        assertThrows(UnsupportedOperationException.class, () -> poses.clear());
    }

    @Test void unsupportedMissingAndOversizedProtocolsAreRejected() {
        for (String data : new String[] {"{}", EMPTY.replace("2500", "2501"), EMPTY.replace("2500", "0"),
                EMPTY.replace("Version\":1", "Version\":2"), EMPTY.replace("{}", "[]")}) {
            assertThrows(IOException.class, () -> BlockingTransport.parse(JsonParser.parseString(data).getAsJsonObject(), 0), data);
        }
    }

    @Test void httpReadIsPublicAndWriteContainsOnlyOwnServiceTokenAndPose() throws Exception {
        var server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger reads = new AtomicInteger(), writes = new AtomicInteger();
        server.createContext("/actions", exchange -> {
            byte[] response;
            if (exchange.getRequestMethod().equals("GET")) {
                assertNull(exchange.getRequestHeaders().getFirst("Authorization")); reads.incrementAndGet();
                response = EMPTY.getBytes(StandardCharsets.UTF_8);
            } else {
                assertEquals("Bearer service-token-fixture", exchange.getRequestHeaders().getFirst("Authorization"));
                var body = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals(java.util.Set.of("hand", "sequence"), body.keySet());
                assertEquals("off", body.get("hand").getAsString()); assertEquals(7, body.get("sequence").getAsInt());
                writes.incrementAndGet(); response = "{\"ok\":true,\"actionVersion\":1}".getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var transport = new BlockingTransport(url -> (HttpURLConnection) URI.create(url).toURL().openConnection());
            assertTrue(transport.fetch(base, 100).isEmpty());
            transport.publish(base, new PresenceAuthClient.Session("service-token-fixture", ALICE, "Alice", Long.MAX_VALUE, base), BlockHand.OFF, 7);
            assertEquals(1, reads.get()); assertEquals(1, writes.get());
            assertThrows(IOException.class, () -> transport.publish(base,
                new PresenceAuthClient.Session("service-token-fixture", ALICE, "Alice", Long.MAX_VALUE, "https://other.example:443"), BlockHand.MAIN, 8));
            assertEquals(1, writes.get());
        } finally { server.stop(0); }
    }

    @Test void redirectsCannotForwardServiceTokensAndErrorBodiesAreNotExposed() throws Exception {
        var server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger redirected = new AtomicInteger();
        server.createContext("/actions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Location", "/sink");
            byte[] body = "private-token-in-error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(302, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/sink", exchange -> { redirected.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var transport = new BlockingTransport(url -> (HttpURLConnection) URI.create(url).toURL().openConnection());
            var error = assertThrows(IOException.class, () -> transport.publish(base,
                new PresenceAuthClient.Session("service-token-fixture", ALICE, "Alice", Long.MAX_VALUE, base), BlockHand.MAIN, 1));
            assertTrue(error.getMessage().contains("302")); assertFalse(error.getMessage().contains("private-token"));
            assertEquals(0, redirected.get());
        } finally { server.stop(0); }
    }
}
