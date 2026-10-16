// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PeerPathTest {
    @Test void threeGoodProbesAreRequiredAndSlowerDirectPathsStayOnRelay(){
        var path=new PeerPath();path.pong(1000,990,5000);path.pong(1250,1240,5250);assertFalse(path.viable(1250,40));
        path.pong(1500,1490,5500);assertTrue(path.viable(1500,40));assertFalse(path.viable(2701,40));
        var slow=new PeerPath();for(int i=1;i<=5;i++)slow.pong(i*1000,i*1000-500,i*1000);assertFalse(slow.viable(5000,40));
    }
    @Test void directIsPreferredOverAFasterRelayUntilItIsClearlyTooSlow(){
        var direct=new PeerPath();for(int i=1;i<=3;i++)direct.pong(i*1000,i*1000-150,i*1000);
        assertTrue(direct.viable(3000,20));assertEquals(200,PeerPath.rttLimit(20));
        var poor=new PeerPath();for(int i=1;i<=3;i++)poor.pong(i*1000,i*1000-350,i*1000);
        assertEquals(PeerPath.Decision.SLOW,poor.decision(3000,20));
        assertTrue(poor.viable(3000,300),"Overseas direct remains preferable when the relay is also slow");
    }
    @Test void diagnosticsDescribeEachQualityGate(){
        var path=new PeerPath();assertEquals(PeerPath.Decision.PROBING,path.decision(0,20));
        for(int i=1;i<=3;i++)path.pong(i*1000,i*1000-10,i*1000);
        assertEquals(PeerPath.Decision.READY,path.decision(3000,20));
        assertEquals(PeerPath.Decision.STALE,path.decision(4201,20));
        for(int i=0;i<12;i++)path.lost(3000);path.holdUntil=0;
        assertEquals(PeerPath.Decision.LOSS,path.decision(3000,20));
        path.holdUntil=4000;assertEquals(PeerPath.Decision.COOLDOWN,path.decision(3000,20));
    }
    @Test void briefProbeLossDoesNotDisplaceHealthyDirectButSustainedLossDoes(){
        var path=new PeerPath();for(int i=1;i<=3;i++)path.pong(i*250,i*250-10,i*250);
        assertTrue(path.viable(750,40));path.lost(1000);path.lost(1250);assertTrue(path.viable(1250,40));
        for(int i=0;i<12;i++)path.lost(1250);assertFalse(path.viable(1250,40));
        for(int i=6;i<=28;i++)path.pong(i*250,i*250-10,i*250);
        assertTrue(path.viable(7000,40));
    }
    @Test void probeDeadlineAndFreshnessAdaptToAnAuthenticatedSlowerPath(){
        var path=new PeerPath();path.pong(1000,700,850);
        assertEquals(1450,path.probeTimeout());assertEquals(1700,path.freshness());
        path.pong(2500,1300,1900);assertTrue(path.lastPong==2500,"A delayed reply must not be counted as both received and lost");
    }
    @Test void remoteClockOffsetsAreEstimatedWithoutComparingWallClocks(){
        var path=new PeerPath();path.pong(1020,1000,61010);assertEquals(60000,path.offset,.001);
        path.pong(1200,1000,61100);assertEquals(60000,path.offset,.001);
    }
}
