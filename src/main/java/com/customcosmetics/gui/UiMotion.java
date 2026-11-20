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
    private float travel;
    private long started;
    private boolean running;
    public UiMotion(float initial){this(initial,CLOCK,Curve.SOFT);}
    public UiMotion(float initial,Curve curve){this(initial,CLOCK,curve);}
    UiMotion(float initial,LongSupplier clock){this(initial,clock,Curve.SOFT);}
    UiMotion(float initial,LongSupplier clock,Curve curve){from=target=initial;this.clock=clock;this.curve=curve;}
    public void snap(float value){from=target=value;travel=0;running=false;}
    /** Signed distance over a fixed exposure, independent of frame rate. */
    float travel(){return travel;}
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
        if(progress>=1){snap(target);return target;}
        float eased=ease(progress);
        float exposed=Math.min(1,progress+16_000_000f/duration);
        travel=(target-from)*(ease(exposed)-eased);
        return from+(target-from)*eased;
    }
    private float ease(float progress){
        float remaining=progress-1;
        return curve==Curve.SPRING?1+3*remaining*remaining*remaining+2*remaining*remaining:1+remaining*remaining*remaining;
    }
}
