// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/** Bounded diagnostic recording; never active during ordinary gameplay. */
public final class ArmorTraceBuffer {
    public static final int MAX_EVENTS=6144,MAX_KEYS=128;
    public static final long DURATION=10_000_000_000L,INTERVAL=40_000_000L;
    private final long started;
    private record Sample(long time,long signature){}
    private final Map<String,Sample> last=new HashMap<>();
    private final JsonArray events=new JsonArray();
    private boolean closed;
    private int dropped;
    public ArmorTraceBuffer(long started){this.started=started;}
    public boolean expired(long now){return now-started>=DURATION;}
    public synchronized boolean allow(String key,long now){
        return allow(key,now,0);
    }
    /** A changed tint is recorded immediately, without creating one key per animation color. */
    public synchronized boolean allow(String key,long now,long signature){
        if(closed||expired(now))return false;
        if(events.size()>=MAX_EVENTS||!last.containsKey(key)&&last.size()>=MAX_KEYS){dropped++;return false;}
        Sample previous=last.get(key);
        if(previous!=null&&previous.signature()==signature&&now-previous.time()<INTERVAL)return false;
        last.put(key,new Sample(now,signature));return true;
    }
    public synchronized void add(JsonObject event,long now){
        if(closed)return;
        if(events.size()>=MAX_EVENTS){dropped++;return;}
        event.addProperty("time_ms",Math.max(0,(now-started)/1_000_000L));events.add(event);
    }
    public synchronized JsonObject finish(){
        closed=true;var result=new JsonObject();result.add("events",events.deepCopy());result.addProperty("dropped",dropped);return result;
    }
}
