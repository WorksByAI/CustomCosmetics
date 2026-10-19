// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.utils;

import java.util.IdentityHashMap;

/** Bounded reuse within one render frame; mutable sequences are always revisited next frame. */
public final class IdentityFrameCache<K,V> {
    private final IdentityHashMap<K,V> entries=new IdentityHashMap<>();
    private final int limit;
    private long frame=Long.MIN_VALUE,revision=Long.MIN_VALUE;
    public IdentityFrameCache(int limit){if(limit<1)throw new IllegalArgumentException("limit");this.limit=limit;}
    public void begin(long nextFrame,long nextRevision){if(frame!=nextFrame||revision!=nextRevision){entries.clear();frame=nextFrame;revision=nextRevision;}}
    public V get(K key){return entries.get(key);}
    public void put(K key,V value){if(entries.size()<limit||entries.containsKey(key))entries.put(key,value);}
    public int size(){return entries.size();}
}
