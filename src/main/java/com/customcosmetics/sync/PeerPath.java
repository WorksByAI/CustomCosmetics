// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

/** Prefer proven direct UDP; fall back to the warm relay only when quality becomes poor. */
final class PeerPath {
    enum Decision { READY, COOLDOWN, PROBING, STALE, LOSS, SLOW }
    double rtt=-1,offset,loss;long lastPong=Long.MIN_VALUE/2,holdUntil;int good;
    void pong(long now,long sent,long remoteClock){
        double sample=now-sent;if(sample<0||sample>1000)return;
        if(rtt<0||sample<=rtt)offset=remoteClock-(sent+sample/2);
        rtt=rtt<0?sample:rtt*.75+sample*.25;lastPong=now;loss*=.8;good++;
    }
    void lost(long now){loss=.8*loss+.2;if(loss>.3){good=0;holdUntil=now+5000;}}
    boolean viable(long now,double relayRtt){
        return decision(now,relayRtt)==Decision.READY;
    }
    static double rttLimit(double relayRtt){return Math.max(200,relayRtt*2+40);}
    Decision decision(long now,double relayRtt){
        if(now<holdUntil)return Decision.COOLDOWN;
        if(good<3)return Decision.PROBING;
        if(now-lastPong>900)return Decision.STALE;
        if(loss>=.25)return Decision.LOSS;
        return rtt<=rttLimit(relayRtt)?Decision.READY:Decision.SLOW;
    }
}
