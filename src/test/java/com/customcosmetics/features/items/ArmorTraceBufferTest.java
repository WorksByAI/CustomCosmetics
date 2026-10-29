// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArmorTraceBufferTest {
    @Test void colorChangesBypassThrottleWithoutConsumingNewKeys(){
        var trace=new ArmorTraceBuffer(0);
        assertTrue(trace.allow("armor",0,0xFF000000L));
        assertFalse(trace.allow("armor",1,0xFF000000L));
        assertTrue(trace.allow("armor",2,0xFF0554C9L));
        assertFalse(trace.allow("armor",3,0xFF0554C9L));
        for(int i=0;i<ArmorTraceBuffer.MAX_KEYS-1;i++)assertTrue(trace.allow("other"+i,4));
        assertTrue(trace.allow("armor",5,0xFF000000L));
        assertFalse(trace.allow("overflow",5));
    }
    @Test void recordingExpiresAndFreezeRejectsLateEvents(){
        long start=100;var trace=new ArmorTraceBuffer(start);
        assertTrue(trace.allow("inventory",start));trace.add(new JsonObject(),start);
        assertFalse(trace.allow("inventory",start+ArmorTraceBuffer.INTERVAL-1));
        assertTrue(trace.allow("inventory",start+ArmorTraceBuffer.INTERVAL));
        assertTrue(trace.expired(start+ArmorTraceBuffer.DURATION));
        assertFalse(trace.allow("render",start+ArmorTraceBuffer.DURATION));
        var result=trace.finish();assertEquals(1,result.getAsJsonArray("events").size());
        assertFalse(trace.allow("render",start+1));trace.add(new JsonObject(),start+1);assertEquals(1,trace.finish().getAsJsonArray("events").size());
    }
    @Test void diagnosticKeysAndEventsHaveHardLimits(){
        var trace=new ArmorTraceBuffer(0);
        for(int i=0;i<ArmorTraceBuffer.MAX_KEYS;i++)assertTrue(trace.allow("key"+i,0));
        assertFalse(trace.allow("overflow",0));
        for(int i=0;i<ArmorTraceBuffer.MAX_EVENTS+10;i++)trace.add(new JsonObject(),i);
        var result=trace.finish();assertEquals(ArmorTraceBuffer.MAX_EVENTS,result.getAsJsonArray("events").size());assertEquals(11,result.get("dropped").getAsInt());
    }
}
