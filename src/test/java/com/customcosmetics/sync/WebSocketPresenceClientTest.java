package com.customcosmetics.sync;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class WebSocketPresenceClientTest {
    private static final java.util.UUID PLAYER = java.util.UUID.fromString("12345678-1234-4234-8234-123456789abc");
    @Test void publicEndpointUsesWssAndLocalFixtureUsesWsWithoutChangingHostOrPort() {
        assertEquals(URI.create("wss://cc.toryhi.com:443/actions/ws"),WebSocketPresenceClient.endpoint("https://cc.toryhi.com:443"));
        assertEquals(URI.create("ws://127.0.0.1:25577/actions/ws"),WebSocketPresenceClient.endpoint("http://127.0.0.1:25577"));
        for(String base:new String[]{"http://a/path","https://user:pass@a","https://a?token=x","https://a#x","udp://a"})
            assertThrows(IllegalArgumentException.class,()->WebSocketPresenceClient.endpoint(base));
    }
    @Test void snapshotsPreserveWeaponIdentityAndFilterByLoadedPlayersAndWorldRevision() {
        String record="{\"type\":\"poses\",\"revision\":7,\"ttlMs\":1250,\"users\":{\""+PLAYER+"\":{\"hand\":\"main\",\"itemId\":\"skyblock:HYPERION\",\"remainingMs\":500}}}";
        var json=JsonParser.parseString(record).getAsJsonObject();
        var result=WebSocketPresenceClient.parseSnapshot(json,7,Set.of(PLAYER),1000);
        assertEquals("skyblock:HYPERION",result.get(PLAYER).itemId());assertEquals(1500,result.get(PLAYER).expiresAt());
        assertTrue(WebSocketPresenceClient.parseSnapshot(json,7,Set.of(),1000).isEmpty());
        assertThrows(IllegalArgumentException.class,()->WebSocketPresenceClient.parseSnapshot(json,8,Set.of(PLAYER),1000));
        json.getAsJsonObject("users").getAsJsonObject(PLAYER.toString()).addProperty("remainingMs",99999);
        assertThrows(IllegalArgumentException.class,()->WebSocketPresenceClient.parseSnapshot(json,7,Set.of(PLAYER),1000));
    }
}
