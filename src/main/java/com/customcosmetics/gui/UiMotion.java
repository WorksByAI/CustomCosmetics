// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import java.util.function.LongSupplier;

/** Screen-owned finite transitions. No registry, tick hook, timer or idle clock polling. */
public final class UiMotion {
    private static final LongSupplier CLOCK=System::nanoTime;
    public enum Curve { SOFT, SPRING, GLIDE }
    private final Curve curve;
    private final LongSupplier clock;
    private float from,target;
    private long started;
    private boolean running;
    public UiMotion(float initial){this(initial,CLOCK,Curve.SOFT);}
    public UiMotion(float initial,Curve curve){this(initial,CLOCK,curve);}
    UiMotion(float initial,LongSupplier clock){this(initial,clock,Curve.SOFT);}
    UiMotion(float initial,LongSupplier clock,Curve curve){from=target=initial;this.clock=clock;this.curve=curve;}
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
        long duration=switch(curve){case SOFT->140_000_000L;case SPRING->240_000_000L;case GLIDE->360_000_000L;};
        float progress=(float)Math.clamp((now-started)/(double)duration,0,1);
        if(progress>=1){running=false;from=target;return target;}
        float remaining=progress-1;
        float eased=curve==Curve.SPRING?1+3*remaining*remaining*remaining+2*remaining*remaining:1+remaining*remaining*remaining;
        return from+(target-from)*eased;
    }
}
