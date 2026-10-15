// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

/** Probe-driven hysteresis. Never switches a slow UDP path to a known slower fallback. */
public final class RealtimeRoute {
    private boolean udp,healthy;private long udpSeen=Long.MIN_VALUE/2,changed;private double udpMs=-1,wssMs=-1;private int good,bad;
    public void wss(long rtt){if(rtt>=0&&rtt<=10000)wssMs=wssMs<0?rtt:wssMs*.7+rtt*.3;}
    public void udp(long now,long rtt,boolean healthy){
        if(rtt<0||rtt>10000)return;
        if(now-udpSeen>2500){good=bad=0;udpMs=-1;}
        udpSeen=now;this.healthy=healthy;udpMs=udpMs<0?rtt:udpMs*.7+rtt*.3;
        boolean better=healthy&&(wssMs<0||udpMs<=Math.max(180,wssMs*1.1));
        good=better?good+1:0;bad=healthy&&wssMs>=0&&udpMs>Math.max(180,wssMs*1.4+40)?bad+1:0;
    }
    public boolean select(long now){
        boolean before=udp;
        if(!healthy||now-udpSeen>2500)udp=false;
        else if(udp){if(bad>=5&&now-changed>=10000)udp=false;}
        else if(good>=3&&(changed==0||now-changed>=10000))udp=true;
        if(before!=udp){changed=now;good=bad=0;}
        return udp;
    }
    public double udpMs(){return udpMs;}public double wssMs(){return wssMs;}
}
