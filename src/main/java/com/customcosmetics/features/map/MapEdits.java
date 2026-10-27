// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.util.*;

/** Immutable editor data. Coordinates are independent of Minecraft world storage. */
public final class MapEdits {
    private static final java.util.regex.Pattern MATERIAL=java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+(?:\\[[a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*\\])?");
    /** Avoid MapN's linear probing for spatial keys; retain immutable snapshots without recopying them. */
    public static <V> Map<Position,V> immutable(Map<Position,V> source){
        if(source instanceof Snapshot<?>||source instanceof Delta<?>)return source;if(source.isEmpty())return Map.of();
        var copy=new HashMap<Position,V>(source);copy.forEach((p,v)->{Objects.requireNonNull(p);Objects.requireNonNull(v);});return new Snapshot<>(copy);
    }
    /** Transfers a private builder to an immutable snapshot; caller must never mutate it again. */
    static <V> Map<Position,V> own(Map<Position,V> builder){return builder.isEmpty()?Map.of():new Snapshot<>(builder);}
    /** Sparse copy-on-write updates. A null value removes a coordinate without copying the base map. */
    static <V> Map<Position,V> change(Map<Position,V> previous,Map<Position,V> updates){
        var effective=new HashMap<Position,V>();int size=previous.size();
        updates.forEach((p,v)->{if(!Objects.equals(previous.get(p),v))effective.put(p,v);});
        if(effective.isEmpty())return previous;
        for(var e:effective.entrySet()){if(e.getValue()==null)size--;else if(!previous.containsKey(e.getKey()))size++;}
        Map<Position,V> base=previous;Map<Position,V> combined=effective;
        if(previous instanceof Delta<V> delta&&delta.updates.size()<4096&&effective.size()<4096){base=delta.base;combined=new HashMap<>(delta.updates);combined.putAll(effective);}
        return new Delta<>(base,combined,size,previous,Set.copyOf(effective.keySet()));
    }
    static Set<Position> differences(Map<Position,?> a,Map<Position,?> b){
        if(a==b)return Set.of();
        if(b instanceof Delta<?> d&&d.previous.get()==a)return d.recent;
        if(a instanceof Delta<?> d&&d.previous.get()==b)return d.recent;
        return null;
    }
    private static final class Delta<V> extends AbstractMap<Position,V>{
        private final Map<Position,V> base,updates;private final int count;
        private final java.lang.ref.WeakReference<Map<Position,V>> previous;private final Set<Position> recent;
        Delta(Map<Position,V> base,Map<Position,V> updates,int count,Map<Position,V> previous,Set<Position> recent){this.base=base;this.updates=Collections.unmodifiableMap(updates);this.count=count;this.previous=new java.lang.ref.WeakReference<>(previous);this.recent=recent;}
        @Override public V get(Object p){return updates.containsKey(p)?updates.get(p):base.get(p);}
        @Override public boolean containsKey(Object p){return updates.containsKey(p)?updates.get(p)!=null:base.containsKey(p);}
        @Override public int size(){return count;}
        @Override public Set<Entry<Position,V>> entrySet(){return new AbstractSet<>(){
            @Override public int size(){return count;}
            @Override public Iterator<Entry<Position,V>> iterator(){return java.util.stream.Stream.concat(base.entrySet().stream().filter(e->!updates.containsKey(e.getKey())),updates.entrySet().stream().filter(e->e.getValue()!=null)).iterator();}
        };}
    }
    private static final class Snapshot<V> extends AbstractMap<Position,V>{
        private final Map<Position,V> values;
        Snapshot(Map<Position,V> owned){values=Collections.unmodifiableMap(owned);}
        @Override public V get(Object p){return values.get(p);}
        @Override public boolean containsKey(Object p){return values.containsKey(p);}
        @Override public int size(){return values.size();}
        @Override public Set<Entry<Position,V>> entrySet(){return values.entrySet();}
    }
    public record Position(int x,int y,int z) {
        // Record's default 31*x/y/z hash forms enormous clusters for dense dungeon geometry.
        @Override public int hashCode(){int h=x*0x9e3779b9;h=Integer.rotateLeft(h,13)^y*0x85ebca6b;h=Integer.rotateLeft(h,11)^z*0xc2b2ae35;h^=h>>>16;h*=0x85ebca6b;h^=h>>>13;return h^(h>>>16);}
        public Position add(Position p){return new Position(x+p.x,y+p.y,z+p.z);}
        public Position subtract(Position p){return new Position(x-p.x,y-p.y,z-p.z);}
        public Position rotate(int turns){return switch(Math.floorMod(turns,4)){case 1->new Position(-z,y,x);case 2->new Position(-x,y,-z);case 3->new Position(z,y,-x);default->this;};}
    }
    public record Edit(Position position,String material,boolean originallyAir) {
        public Edit { Objects.requireNonNull(position);if(material==null||material.length()>512||!MATERIAL.matcher(material).matches())throw new IllegalArgumentException("Invalid block ID"); }
        public Edit transform(Position origin,int turns){return new Edit(position.rotate(turns).add(origin),material,originallyAir);}
        public boolean ghost(boolean currentlyAir){return originallyAir&&currentlyAir&&!material.equals("minecraft:air");}
    }
    public record Preset(String name,List<Edit> edits,String fingerprint) {
        public Preset { if(name==null||!name.matches("[A-Za-z0-9 _.-]{1,48}"))throw new IllegalArgumentException("Use 1-48 letters, numbers, spaces, _ . or -");edits=List.copyOf(edits);fingerprint=fingerprint==null?"":fingerprint; }
        public Map<Position,Edit> place(Position origin,int turns){var result=new LinkedHashMap<Position,Edit>();for(var edit:edits){var placed=edit.transform(origin,turns);result.put(placed.position,placed);}return immutable(result);}
    }
    private record History(Map<Position,Edit> before,Map<Position,Edit> after) {}
    private Map<Position,Edit> edits=Map.of();private final Deque<History> undo=new ArrayDeque<>();
    public Map<Position,Edit> snapshot(){return edits;}
    public void replace(Map<Position,Edit> next){var snapshot=immutable(next);undo.push(new History(edits,snapshot));while(undo.size()>32)undo.removeLast();edits=snapshot;}
    /** Automatic room discovery is not a user edit and must not consume the next Undo. */
    void restore(Map<Position,Edit> next){edits=immutable(next);}
    public void put(Edit edit){var next=change(edits,Map.of(edit.position,edit));if(next!=edits)replace(next);}
    public void remove(Position p){var next=change(edits,Collections.singletonMap(p,null));if(next!=edits)replace(next);}
    public Set<Position> undoPositions(){var entry=undo.peek();return entry==null?Set.of():differences(entry.after,entry.before);}
    java.util.function.Supplier<Map<Position,Edit>> undoTarget(){
        var entry=undo.peek();var current=edits;return entry==null?null:()->undoTarget(current,entry);
    }
    private static Map<Position,Edit> undoTarget(Map<Position,Edit> current,History entry){
        if(current==entry.after)return entry.before;
        // Preserve room data restored after the user action. Sparse edits remain O(changed positions).
        var changed=differences(entry.after,entry.before);
        if(changed!=null){var updates=new HashMap<Position,Edit>();for(var p:changed)updates.put(p,entry.before.get(p));return change(current,updates);}
        // Large batch undo is prepared by MapEditor on its background worker.
        var next=new HashMap<>(current);
        for(var e:entry.after.entrySet())if(!Objects.equals(e.getValue(),entry.before.get(e.getKey()))){
            var previous=entry.before.get(e.getKey());if(previous==null)next.remove(e.getKey());else next.put(e.getKey(),previous);
        }
        for(var e:entry.before.entrySet())if(!entry.after.containsKey(e.getKey()))next.put(e.getKey(),e.getValue());
        return own(next);
    }
    void finishUndo(Map<Position,Edit> prepared){if(undo.isEmpty())throw new IllegalStateException("No edit to undo");undo.pop();edits=immutable(prepared);}
    public boolean undo(){var target=undoTarget();if(target==null)return false;finishUndo(target.get());return true;}
    public void clear(){if(!edits.isEmpty())replace(Map.of());}
    public void reset(){edits=Map.of();undo.clear();}
    public Preset preset(String name,Position origin,String fingerprint){return new Preset(name,edits.values().stream().map(e->new Edit(e.position.subtract(origin),e.material,e.originallyAir)).toList(),fingerprint);}
}
