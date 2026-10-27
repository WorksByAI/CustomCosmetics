// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

/** Animation state follows display refresh; Minecraft still draws its normal complete frames. */
public final class AnimationClock {
    private static long sampledMillis,nextSample,lastFrame,readMonitorAt;
    private static int refresh=60;
    private AnimationClock(){}
    public static void frame(net.minecraft.client.Minecraft client){
        long now=System.nanoTime();
        if(now>=readMonitorAt){readMonitorAt=now+1_000_000_000L;refresh=com.customcosmetics.compatibility.WindowRefreshRate.get(client.getWindow());}
        update(now,System.currentTimeMillis(),refresh);
    }
    static void update(long nanos,long millis,int rate){lastFrame=nanos;if(nanos>=nextSample){sampledMillis=millis;nextSample=nanos+1_000_000_000L/Math.clamp(rate,30,1000);}}
    public static long millis(){return lastFrame!=0&&System.nanoTime()-lastFrame<1_000_000_000L?sampledMillis:System.currentTimeMillis();}
}
