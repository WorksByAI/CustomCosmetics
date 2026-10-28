// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.skins;
import com.customcosmetics.config.PlayerBlocklist;
import java.util.*;
/** Local-only policy. Rebuild identity indexes on configuration changes, never per player/frame. */
public final class SkinRules {
    private SkinRules(){}
    public static String mode(String value){return "steve".equals(value)||"alex".equals(value)||"custom".equals(value)?value:"original";}
    public static final class Blocks {
        private record Index(List<PlayerBlocklist.Entry> source,Set<UUID> ids,NavigableSet<String> names){}
        private volatile Index index;
        public boolean contains(List<PlayerBlocklist.Entry> entries,UUID id,String name){
            if(entries==null||entries.isEmpty())return false;
            var current=index;
            if(current==null||current.source!=entries){
                var ids=new HashSet<UUID>();var names=new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                for(var entry:entries)if(entry!=null){if(entry.uuid()!=null)ids.add(entry.uuid());else if(entry.account()!=null)names.add(entry.account());}
                current=new Index(entries,Set.copyOf(ids),Collections.unmodifiableNavigableSet(names));index=current;
            }
            return id!=null&&current.ids.contains(id)||name!=null&&current.names.contains(name);
        }
    }
}
