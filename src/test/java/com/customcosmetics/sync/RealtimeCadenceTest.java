// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;
import com.customcosmetics.features.party.PartyRoster;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RealtimeCadenceTest {
    @Test void positionHintsCannotExceedTwentyUdpOrTenWssReportsPerSecond(){
        assertEquals(50,RealtimeClient.positionInterval(true,1));assertEquals(100,RealtimeClient.positionInterval(false,1));
        assertEquals(250,RealtimeClient.positionInterval(true,250));assertEquals(1000,RealtimeClient.positionInterval(false,Integer.MAX_VALUE));
    }
    @Test void nearbyInterestChangesKeepPartyPositionScopeButWorldRosterAndConsentChangesDoNot(){
        var roster=new PartyRoster(Set.of(UUID.randomUUID()),Set.of(),"test");
        var a=new RealtimeClient.View("a".repeat(64),Set.of(),roster,0,true);
        assertTrue(RealtimeClient.sameScope(a,new RealtimeClient.View(a.world(),Set.of(UUID.randomUUID()),roster,0,true)));
        assertFalse(RealtimeClient.sameScope(a,new RealtimeClient.View("b".repeat(64),a.players(),roster,0,true)));
        assertFalse(RealtimeClient.sameScope(a,new RealtimeClient.View(a.world(),a.players(),PartyRoster.EMPTY,0,true)));
        assertFalse(RealtimeClient.sameScope(a,new RealtimeClient.View(a.world(),a.players(),roster,1,true)));
        assertFalse(RealtimeClient.sameScope(a,new RealtimeClient.View(a.world(),a.players(),roster,0,false)));
    }
}
