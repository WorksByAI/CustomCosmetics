// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

/** Evidence is required before choosing direct UDP; gaps and loss immediately favor the warm relay. */
final class PeerPath {
    double rtt=-1,offset,loss;long lastPong=Long.MIN_VALUE/2,holdUntil;int good;
    void pong(long now,long sent,long remoteClock){
        double sample=now-sent;if(sample<0||sample>1000)return;
        if(rtt<0||sample<=rtt)offset=remoteClock-(sent+sample/2);
        rtt=rtt<0?sample:rtt*.75+sample*.25;lastPong=now;loss*=.8;good++;
    }
    void lost(long now){loss=.8*loss+.2;if(loss>.3){good=0;holdUntil=now+5000;}}
    boolean viable(long now,double relayRtt){
        return now>=holdUntil&&good>=3&&now-lastPong<=900&&loss<.25&&rtt<=Math.max(30,relayRtt*1.15+5);
    }
}
