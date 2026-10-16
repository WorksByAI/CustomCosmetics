// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

/** Prefer proven direct UDP; fall back to the warm relay only when quality becomes poor. */
final class PeerPath {
    enum Decision { READY, COOLDOWN, PROBING, STALE, LOSS, SLOW }
    double rtt=-1,offset,loss;long lastPong=Long.MIN_VALUE/2,holdUntil;int good;
    private int outcomes,samples;
    void pong(long now,long sent,long remoteClock){
        double sample=now-sent;if(sample<0||sample>3000)return;
        if(rtt<0||sample<=rtt)offset=remoteClock-(sent+sample/2);
        rtt=rtt<0?sample:rtt*.75+sample*.25;lastPong=now;outcome(false);good++;
    }
    private void outcome(boolean lost){outcomes=(outcomes<<1|(lost?1:0))&0xffff;samples=Math.min(16,samples+1);loss=(double)Integer.bitCount(outcomes)/samples;}
    private boolean poorLoss(){return samples>=12&&loss>=.5;}
    void lost(long now){outcome(true);if(poorLoss()){good=0;holdUntil=now+2000;}}
    long probeTimeout(){return (long)Math.clamp(rtt<0?1000:rtt*4+250,1000,3000);}
    long freshness(){return (long)Math.clamp(rtt<0?1200:rtt*4+500,1200,2500);}
    boolean viable(long now,double relayRtt){
        return decision(now,relayRtt)==Decision.READY;
    }
    static double rttLimit(double relayRtt){return Math.max(200,relayRtt*2+40);}
    Decision decision(long now,double relayRtt){
        if(now<holdUntil)return Decision.COOLDOWN;
        if(poorLoss())return Decision.LOSS;
        if(good<3)return Decision.PROBING;
        if(now-lastPong>freshness())return Decision.STALE;
        return rtt<=rttLimit(relayRtt)?Decision.READY:Decision.SLOW;
    }
}
