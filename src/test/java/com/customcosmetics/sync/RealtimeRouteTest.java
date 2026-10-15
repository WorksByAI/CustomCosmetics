// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RealtimeRouteTest {
    @Test void missingUdpImmediatelyUsesFallbackAndThreeGoodProbesEnablePrimary(){
        var route=new RealtimeRoute();route.wss(300);assertFalse(route.select(1000));
        for(long now=1000;now<=3000;now+=1000){route.udp(now,40,true);assertEquals(now==3000,route.select(now));}
        assertFalse(route.select(6000));
    }
    @Test void congestionSwitchesOnlyAfterSustainedEvidenceAndRecoveryWaitsBeforeReturning(){
        var route=new RealtimeRoute();route.wss(80);
        for(long now=1000;now<=3000;now+=1000){route.udp(now,30,true);route.select(now);}
        assertTrue(route.select(3000));
        for(long now=4000;now<=18000;now+=1000){route.udp(now,800,true);route.select(now);}
        assertFalse(route.select(18000));
        for(long now=19000;now<=22000;now+=1000){route.udp(now,30,true);route.select(now);}
        assertFalse(route.select(22000));
        for(long now=23000;now<=35000;now+=1000){route.udp(now,30,true);route.select(now);}
        assertTrue(route.select(35000));
    }
    @Test void aSlowerKnownWssPathDoesNotReplaceWorkingUdp(){
        var route=new RealtimeRoute();route.wss(500);
        for(long now=1000;now<=30000;now+=1000){route.udp(now,250,true);route.select(now);}
        assertTrue(route.select(30000));
    }
    @Test void brokenMeshDisablesPrimaryEvenWhenItsUdpProbeIsFast(){
        var route=new RealtimeRoute();for(long now=1000;now<=3000;now+=1000){route.udp(now,10,true);route.select(now);}
        assertTrue(route.select(3000));route.udp(4000,10,false);assertFalse(route.select(4000));
    }
}
