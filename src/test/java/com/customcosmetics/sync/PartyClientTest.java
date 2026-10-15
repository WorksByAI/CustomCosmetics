// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;
import com.customcosmetics.features.party.PartyRoster;
import com.google.gson.JsonParser;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PartyClientTest{
    private static final UUID A=UUID.fromString("12345678-1234-4234-8234-123456789abc");
    @Test void partyEndpointIsSeparateAndUsesTheSameTlsOrigin(){
        assertEquals(URI.create("wss://cc.toryhi.com:443/party/ws"),PartyClient.endpoint("https://cc.toryhi.com:443"));
        assertEquals(URI.create("ws://127.0.0.1:25577/party/ws"),PartyClient.endpoint("http://127.0.0.1:25577"));
        for(String address:List.of("https://a/path","https://a?token=secret","https://user:pass@a"))assertThrows(IllegalArgumentException.class,()->PartyClient.endpoint(address));
    }
    @Test void positionsHaveNoRangeCutoffButOnlyIncludePartyMembersAndExpire(){
        var json=JsonParser.parseString("{\"ttlMs\":3000,\"users\":{\""+A+"\":{\"name\":\"Alice\",\"x\":15000,\"y\":80,\"z\":5000,\"width\":0.6,\"height\":1.8}}}").getAsJsonObject();
        var map=PartyClient.parsePositions(json,new PartyRoster(Set.of(A),Set.of(),"test"),100);assertEquals(15000,map.get(A).position().x());assertEquals(3100,map.get(A).expiresAt());
        assertTrue(PartyClient.parsePositions(json,PartyRoster.EMPTY,100).isEmpty());
        json.getAsJsonObject("users").getAsJsonObject(A.toString()).addProperty("x",Double.NaN);assertThrows(IllegalArgumentException.class,()->PartyClient.parsePositions(json,new PartyRoster(Set.of(A),Set.of(),"test"),100));
    }
    @Test void nonFiniteAndOversizedPlayerDimensionsAreRejected(){
        assertTrue(PartyClient.validPosition(new PartyClient.Position(0,80,0,.6,1.8)));
        assertFalse(PartyClient.validPosition(new PartyClient.Position(Double.NaN,80,0,.6,1.8)));assertFalse(PartyClient.validPosition(new PartyClient.Position(0,80,0,0,1.8)));
    }
}
