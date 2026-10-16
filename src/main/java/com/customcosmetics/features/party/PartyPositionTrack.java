// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import com.customcosmetics.sync.PartyClient;
import java.util.ArrayDeque;

/** Short snapshot buffer for unloaded teammates only; it never moves a game entity. */
public final class PartyPositionTrack {
    private final ArrayDeque<PartyClient.Teammate> samples=new ArrayDeque<>();
    private double offset,interval=50;private long cursor=Long.MIN_VALUE;
    public PartyClient.Position render(PartyClient.Teammate next,long now){
        var last=samples.peekLast();
        if(last!=null&&last.epoch()==next.epoch()&&last.scope()==next.scope()&&next.sampledAt()<last.sampledAt())next=last;
        if(last==null||last.epoch()!=next.epoch()||last.scope()!=next.scope()||
                next.sampledAt()-last.sampledAt()>=1500||distanceSquared(last.position(),next.position())>=32*32){
            samples.clear();samples.add(next);offset=next.receivedAt()-next.sampledAt();interval=50;cursor=Long.MIN_VALUE;
        }else if(next.sampledAt()>last.sampledAt()){
            double measured=Math.clamp(next.sampledAt()-last.sampledAt(),50,1000);
            interval=samples.size()==1?measured:interval*.75+measured*.25;
            offset=Math.min(offset,next.receivedAt()-next.sampledAt());samples.add(next);
            while(samples.size()>16)samples.removeFirst();
        }
        long time=Math.max(cursor,(long)(now-offset-Math.clamp(interval*1.2,75,1200)));cursor=time;
        while(samples.size()>2&&samples.stream().skip(1).findFirst().orElseThrow().sampledAt()<=time)samples.removeFirst();
        var first=samples.getFirst();last=samples.getLast();
        if(samples.size()==1||time<=first.sampledAt())return first.position();
        PartyClient.Teammate before=first;
        for(var after:samples){
            if(after.sampledAt()>=time&&after.sampledAt()>before.sampledAt())return blend(before.position(),after.position(),(double)(time-before.sampledAt())/(after.sampledAt()-before.sampledAt()));
            before=after;
        }
        // Bridge one short gap, then hold. Loss never makes a marker drift indefinitely.
        var previous=samples.stream().skip(samples.size()-2).findFirst().orElseThrow();
        double elapsed=last.sampledAt()-previous.sampledAt(),extra=Math.min(75,Math.max(0,time-last.sampledAt()));
        double distance=Math.sqrt(distanceSquared(previous.position(),last.position()));
        if(distance*1000/elapsed>64)return last.position();
        return blend(previous.position(),last.position(),1+extra/elapsed);
    }
    private static double distanceSquared(PartyClient.Position a,PartyClient.Position b){double x=a.x()-b.x(),y=a.y()-b.y(),z=a.z()-b.z();return x*x+y*y+z*z;}
    private static PartyClient.Position blend(PartyClient.Position a,PartyClient.Position b,double t){
        return new PartyClient.Position(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t,b.width(),b.height());
    }
}
