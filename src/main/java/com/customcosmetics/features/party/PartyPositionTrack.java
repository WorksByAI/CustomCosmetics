// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import com.customcosmetics.sync.PartyClient;

/** Short snapshot buffer for unloaded teammates only; it never moves a game entity. */
public final class PartyPositionTrack {
    private final PartyClient.Teammate[] samples=new PartyClient.Teammate[16];
    private int start,size;
    private double offset,interval=50;private long cursor=Long.MIN_VALUE;
    public PartyClient.Position render(PartyClient.Teammate next,long now){
        var last=size==0?null:sample(size-1);
        if(last!=null&&last.epoch()==next.epoch()&&last.scope()==next.scope()&&next.sampledAt()<last.sampledAt())next=last;
        if(last==null||last.epoch()!=next.epoch()||last.scope()!=next.scope()||
                next.sampledAt()-last.sampledAt()>=1500||distanceSquared(last.position(),next.position())>=32*32){
            java.util.Arrays.fill(samples,null);start=0;size=1;samples[0]=next;
            offset=next.receivedAt()-next.sampledAt();interval=50;cursor=Long.MIN_VALUE;
        }else if(next.sampledAt()>last.sampledAt()){
            double measured=Math.clamp(next.sampledAt()-last.sampledAt(),50,1000);
            interval=size==1?measured:interval*.75+measured*.25;
            offset=Math.min(offset,next.receivedAt()-next.sampledAt());
            if(size==samples.length)removeFirst();
            samples[(start+size++)&15]=next;
        }
        long time=Math.max(cursor,(long)(now-offset-Math.clamp(interval*1.2,75,1200)));cursor=time;
        while(size>2&&sample(1).sampledAt()<=time)removeFirst();
        var first=sample(0);last=sample(size-1);
        if(size==1||time<=first.sampledAt())return first.position();
        PartyClient.Teammate before=first;
        for(int i=1;i<size;i++){
            var after=sample(i);
            if(after.sampledAt()>=time&&after.sampledAt()>before.sampledAt())return blend(before.position(),after.position(),(double)(time-before.sampledAt())/(after.sampledAt()-before.sampledAt()));
            before=after;
        }
        // Bridge one short gap, then hold. Loss never makes a marker drift indefinitely.
        var previous=sample(size-2);
        double elapsed=last.sampledAt()-previous.sampledAt(),extra=Math.min(75,Math.max(0,time-last.sampledAt()));
        double distance=Math.sqrt(distanceSquared(previous.position(),last.position()));
        if(distance*1000/elapsed>64)return last.position();
        return blend(previous.position(),last.position(),1+extra/elapsed);
    }
    private PartyClient.Teammate sample(int index){return samples[(start+index)&15];}
    private void removeFirst(){samples[start]=null;start=(start+1)&15;size--;}
    private static double distanceSquared(PartyClient.Position a,PartyClient.Position b){double x=a.x()-b.x(),y=a.y()-b.y(),z=a.z()-b.z();return x*x+y*y+z*z;}
    private static PartyClient.Position blend(PartyClient.Position a,PartyClient.Position b,double t){
        return new PartyClient.Position(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t,b.width(),b.height());
    }
}
