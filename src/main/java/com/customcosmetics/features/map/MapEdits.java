// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.util.*;

/** Immutable editor data. Coordinates are independent of Minecraft world storage. */
public final class MapEdits {
    public static final int LIMIT=8192;
    public record Position(int x,int y,int z) {
        public Position add(Position p){return new Position(x+p.x,y+p.y,z+p.z);}
        public Position subtract(Position p){return new Position(x-p.x,y-p.y,z-p.z);}
        public Position rotate(int turns){return switch(Math.floorMod(turns,4)){case 1->new Position(-z,y,x);case 2->new Position(-x,y,-z);case 3->new Position(z,y,-x);default->this;};}
    }
    public record Edit(Position position,String material,boolean originallyAir) {
        public Edit { Objects.requireNonNull(position);if(material==null||material.length()>512||!material.matches("[a-z0-9_.-]+:[a-z0-9_./-]+(?:\\[[a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*\\])?"))throw new IllegalArgumentException("Invalid block ID"); }
        public Edit transform(Position origin,int turns){return new Edit(position.rotate(turns).add(origin),material,originallyAir);}
        public boolean ghost(boolean currentlyAir){return originallyAir&&currentlyAir&&!material.equals("minecraft:air");}
    }
    public record Preset(String name,List<Edit> edits,String fingerprint) {
        public Preset { if(name==null||!name.matches("[A-Za-z0-9 _.-]{1,48}"))throw new IllegalArgumentException("Use 1-48 letters, numbers, spaces, _ . or -");edits=List.copyOf(edits);if(edits.size()>LIMIT)throw new IllegalArgumentException("Too many edits");fingerprint=fingerprint==null?"":fingerprint; }
        public Map<Position,Edit> place(Position origin,int turns){var result=new LinkedHashMap<Position,Edit>();for(var edit:edits){var placed=edit.transform(origin,turns);result.put(placed.position,placed);}return Map.copyOf(result);}
    }
    private Map<Position,Edit> edits=Map.of();private final Deque<Map<Position,Edit>> undo=new ArrayDeque<>();
    public Map<Position,Edit> snapshot(){return edits;}
    public void replace(Map<Position,Edit> next){if(next.size()>LIMIT)throw new IllegalArgumentException("Limit: "+LIMIT+" blocks");undo.push(edits);while(undo.size()>32)undo.removeLast();edits=Map.copyOf(next);}
    public void put(Edit edit){var copy=new LinkedHashMap<>(edits);copy.put(edit.position,edit);replace(copy);}
    public void remove(Position p){var copy=new LinkedHashMap<>(edits);copy.remove(p);replace(copy);}
    public boolean undo(){if(undo.isEmpty())return false;edits=undo.pop();return true;}
    public void clear(){if(!edits.isEmpty())replace(Map.of());}
    public void reset(){edits=Map.of();undo.clear();}
    public Preset preset(String name,Position origin,String fingerprint){return new Preset(name,edits.values().stream().map(e->new Edit(e.position.subtract(origin),e.material,e.originallyAir)).toList(),fingerprint);}
}
