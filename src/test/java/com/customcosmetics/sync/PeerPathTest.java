// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PeerPathTest {
    @Test void threeGoodProbesAreRequiredAndSlowerDirectPathsStayOnRelay(){
        var path=new PeerPath();path.pong(1000,990,5000);path.pong(1250,1240,5250);assertFalse(path.viable(1250,40));
        path.pong(1500,1490,5500);assertTrue(path.viable(1500,40));assertFalse(path.viable(2401,40));
        var slow=new PeerPath();for(int i=1;i<=5;i++)slow.pong(i*1000,i*1000-200,i*1000);assertFalse(slow.viable(5000,40));
    }
    @Test void packetLossTriggersFallbackAndRecoveryHasAHoldPeriod(){
        var path=new PeerPath();for(int i=1;i<=3;i++)path.pong(i*250,i*250-10,i*250);
        assertTrue(path.viable(750,40));path.lost(1000);path.lost(1250);assertFalse(path.viable(1250,40));
        for(int i=6;i<=28;i++)path.pong(i*250,i*250-10,i*250);
        assertTrue(path.viable(7000,40));
    }
    @Test void remoteClockOffsetsAreEstimatedWithoutComparingWallClocks(){
        var path=new PeerPath();path.pong(1020,1000,61010);assertEquals(60000,path.offset,.001);
        path.pong(1200,1000,61100);assertEquals(60000,path.offset,.001);
    }
}
