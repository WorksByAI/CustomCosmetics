// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;
import com.customcosmetics.features.party.PartyRoster;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RealtimeCadenceTest {
    @Test void expirationKeepsUnchangedSnapshotsAndDoesNotMutatePublishedMaps(){
        var a=UUID.randomUUID();var b=UUID.randomUUID();
        var poses=java.util.Map.of(a,new BlockingTransport.Pose(com.customcosmetics.features.blocking.BlockHand.MAIN,100,"minecraft:diamond_sword"),
            b,new BlockingTransport.Pose(com.customcosmetics.features.blocking.BlockHand.MAIN,200,"minecraft:diamond_sword"));
        assertSame(poses,RealtimeClient.unexpiredBlocks(poses,99));
        assertEquals(java.util.Map.of(b,poses.get(b)),RealtimeClient.unexpiredBlocks(poses,100));
        assertEquals(2,poses.size());assertTrue(RealtimeClient.unexpiredBlocks(poses,200).isEmpty());
        var position=new PartyClient.Position(1,2,3,.6,1.8);
        var teammates=java.util.Map.of(a,new PartyClient.Teammate(a,"Alice",position,100),b,new PartyClient.Teammate(b,"Bob",position,200));
        assertSame(teammates,RealtimeClient.unexpiredPositions(teammates,99));
        assertEquals(java.util.Map.of(b,teammates.get(b)),RealtimeClient.unexpiredPositions(teammates,100));
        assertEquals(2,teammates.size());assertTrue(RealtimeClient.unexpiredPositions(teammates,200).isEmpty());
    }
    @Test void positionHintsCannotExceedFiveUdpOrTwoWssReportsPerSecond(){
        assertEquals(200,RealtimeClient.positionInterval(true,1));assertEquals(500,RealtimeClient.positionInterval(false,1));
        assertEquals(250,RealtimeClient.positionInterval(true,250));assertEquals(1000,RealtimeClient.positionInterval(false,Integer.MAX_VALUE));
        assertEquals(1000,RealtimeClient.positionInterval(true,Integer.MAX_VALUE));
        assertEquals(200,RealtimeClient.sampleInterval(true,true,1000));assertEquals(200,RealtimeClient.sampleInterval(true,false,1000));
        assertEquals(500,RealtimeClient.sampleInterval(false,false,100));assertEquals(200,RealtimeClient.sampleInterval(false,true,50));
    }
    @Test void nearbyInterestChangesKeepPartyPositionScopeButWorldRosterAndConsentChangesDoNot(){
        var roster=new PartyRoster(Set.of(UUID.randomUUID()),Set.of(),"test");
        var a=new RealtimeClient.View("a".repeat(64),Set.of(),roster,0,true);
        assertTrue(RealtimeClient.sameScope(a,new RealtimeClient.View(a.world(),Set.of(UUID.randomUUID()),roster,0,true)));
        assertFalse(RealtimeClient.sameScope(a,new RealtimeClient.View("b".repeat(64),a.players(),roster,0,true)));
        assertFalse(RealtimeClient.sameScope(a,new RealtimeClient.View(a.world(),a.players(),PartyRoster.EMPTY,0,true)));
        assertFalse(RealtimeClient.sameScope(a,new RealtimeClient.View(a.world(),a.players(),roster,1,true)));
        assertFalse(RealtimeClient.sameScope(a,new RealtimeClient.View(a.world(),a.players(),roster,0,false)));
        var shared=new com.customcosmetics.features.party.PositionAudience("public",Set.of(),Set.of(),true);
        assertFalse(RealtimeClient.sameScope(a,new RealtimeClient.View(a.world(),a.players(),roster,0,true,false,shared)));
    }
}
