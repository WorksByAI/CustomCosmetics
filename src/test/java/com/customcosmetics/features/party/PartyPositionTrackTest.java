// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
import com.customcosmetics.sync.PartyClient;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PartyPositionTrackTest {
    @Test void reducedReportingAtTwoToFiveHertzStaysSmoothAcrossPacketArrival(){
        for(int period:new int[]{200,500,1000,1020}){
            var track=new PartyPositionTrack();var latest=sample(0,0,1000,1,1);int next=1;double previous=0;
            for(long now=1000;now<12000;now+=16){
                while(1000L+next*period+(next%3)*15<=now){long sent=(long)next*period;latest=sample(sent*.004,sent,1000+sent+(next%3)*15,1,1);next++;}
                double x=track.render(latest,now).x();assertTrue(x>=previous-1e-9,"Backward motion at "+period+"ms");
                if(now>4000){assertTrue(x-previous<=.25,"Jump at "+period+"ms: "+(x-previous));assertTrue(x>previous,"Frozen frame at "+period+"ms");}
                assertTrue(x<=latest.position().x()+.31);previous=x;
            }
        }
    }
    private static final UUID ID=UUID.randomUUID();
    private static PartyClient.Teammate sample(double x,long source,long arrival,long epoch,long scope){
        return new PartyClient.Teammate(ID,"Alice",new PartyClient.Position(x,80,10000,.6,1.8),Long.MAX_VALUE,source,arrival,epoch,scope);
    }
    @Test void interpolatesBetweenSamplesInsteadOfJumpingAtPacketArrival(){
        var track=new PartyPositionTrack();track.render(sample(0,0,1000,1,1),1000);
        var last=sample(1,50,1050,1,1);track.render(last,1050);
        assertEquals(.5,track.render(last,1100).x(),1e-9);assertEquals(.6,track.render(last,1105).x(),1e-9);
    }
    @Test void repeatedFramesAndDifferentFrameRatesRenderTheSameCoordinate(){
        var a=new PartyPositionTrack();var b=new PartyPositionTrack();var first=sample(0,0,1000,1,1);var last=sample(1,50,1050,1,1);
        a.render(first,1000);b.render(first,1000);a.render(last,1050);b.render(last,1050);
        for(int t=1051;t<1100;t++)a.render(last,t);
        assertEquals(a.render(last,1100),b.render(last,1100));assertEquals(a.render(last,1100),a.render(last,1100));
    }
    @Test void packetLossExtrapolationIsShortAndThenStops(){
        var track=new PartyPositionTrack();track.render(sample(0,0,1000,1,1),1000);var last=sample(1,50,1050,1,1);
        track.render(last,1050);assertEquals(1.5,track.render(last,1150).x(),1e-9);
        assertEquals(2.5,track.render(last,2000).x(),1e-9);assertEquals(2.5,track.render(last,5000).x(),1e-9);
    }
    @Test void teleportsRejoinsWorldChangesAndLongGapsSnapWithoutTrails(){
        for(var next:java.util.List.of(sample(100,50,1050,1,1),sample(2,50,1050,2,1),sample(2,50,1050,1,2),sample(2,1500,2500,1,1))){
            var track=new PartyPositionTrack();track.render(sample(0,0,1000,1,1),1000);assertEquals(next.position(),track.render(next,next.receivedAt()));
        }
    }
    @Test void delayedOldSamplesNeverOverrideNewMovement(){
        var track=new PartyPositionTrack();track.render(sample(0,0,1000,1,1),1000);var last=sample(1,50,1050,1,1);track.render(last,1050);
        assertEquals(.5,track.render(sample(900,-100,1090,1,1),1100).x(),1e-9);
    }
    @Test void jitterAndSlowerReportingRemainMonotonicAndBounded(){
        var track=new PartyPositionTrack();double previous=0;
        for(int i=0;i<30;i++){
            var next=sample(i*.25,i*100,1000+i*100+(i%3)*20,1,1);
            for(long now=next.receivedAt();now<next.receivedAt()+80;now+=8){double x=track.render(next,now).x();assertTrue(x>=previous-1e-9);assertTrue(x<=next.position().x()+.2);previous=x;}
        }
    }
}
