package com.customcosmetics.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityFrameCacheTest {
    @Test void identityScopeIsBoundedAndExpiresOnFrameOrSettingsChange(){
        var cache=new IdentityFrameCache<String,Object>(2);cache.begin(1,1);
        String a=new String("same"),b=new String("same");Object value=new Object();
        cache.put(a,value);assertSame(value,cache.get(a));assertNull(cache.get(b));cache.put(b,new Object());cache.put("extra",new Object());assertEquals(2,cache.size());
        cache.begin(1,1);assertSame(value,cache.get(a));cache.begin(2,1);assertNull(cache.get(a));
        cache.put(a,value);cache.begin(2,2);assertNull(cache.get(a));
    }
}
