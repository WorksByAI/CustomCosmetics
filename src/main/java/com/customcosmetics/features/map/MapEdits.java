// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.util.*;

/** Immutable editor data. Coordinates are independent of Minecraft world storage. */
public final class MapEdits {
    private static final java.util.regex.Pattern MATERIAL=java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+(?:\\[[a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*\\])?");
    /** Avoid MapN's linear probing for spatial keys; retain immutable snapshots without recopying them. */
    public static <V> Map<Position,V> immutable(Map<Position,V> source){
        if(source instanceof Snapshot<?>)return source;if(source.isEmpty())return Map.of();
        var copy=new HashMap<Position,V>(source);copy.forEach((p,v)->{Objects.requireNonNull(p);Objects.requireNonNull(v);});return new Snapshot<>(copy);
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
    private Map<Position,Edit> edits=Map.of();private final Deque<Map<Position,Edit>> undo=new ArrayDeque<>();
    public Map<Position,Edit> snapshot(){return edits;}
    public void replace(Map<Position,Edit> next){var snapshot=immutable(next);undo.push(edits);while(undo.size()>32)undo.removeLast();edits=snapshot;}
    public void put(Edit edit){var copy=new LinkedHashMap<>(edits);copy.put(edit.position,edit);replace(copy);}
    public void remove(Position p){var copy=new LinkedHashMap<>(edits);copy.remove(p);replace(copy);}
    public boolean undo(){if(undo.isEmpty())return false;edits=undo.pop();return true;}
    public void clear(){if(!edits.isEmpty())replace(Map.of());}
    public void reset(){edits=Map.of();undo.clear();}
    public Preset preset(String name,Position origin,String fingerprint){return new Preset(name,edits.values().stream().map(e->new Edit(e.position.subtract(origin),e.material,e.originallyAir)).toList(),fingerprint);}
}
