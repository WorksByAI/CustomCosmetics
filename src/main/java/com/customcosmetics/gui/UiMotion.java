// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import java.util.function.LongSupplier;

/** Screen-owned finite transitions. No registry, tick hook, timer or idle clock polling. */
public final class UiMotion {
    private static final LongSupplier CLOCK=System::nanoTime;
    private static final long DURATION=140_000_000;
    private final LongSupplier clock;
    private float from,target;
    private long started;
    private boolean running;
    public UiMotion(float initial){this(initial,CLOCK);}
    UiMotion(float initial,LongSupplier clock){from=target=initial;this.clock=clock;}
    public void snap(float value){from=target=value;running=false;}
    public float value(float next,boolean animate){
        if(!animate){snap(next);return next;}
        if(!running&&next==target)return target;
        long now=clock.getAsLong();
        if(next!=target){from=sample(now);target=next;started=now;running=true;}
        return sample(now);
    }
    private float sample(long now){
        if(!running)return target;
        float progress=(float)Math.clamp((now-started)/(double)DURATION,0,1);
        if(progress>=1){running=false;from=target;return target;}
        float eased=1-(1-progress)*(1-progress)*(1-progress);
        return from+(target-from)*eased;
    }
}
