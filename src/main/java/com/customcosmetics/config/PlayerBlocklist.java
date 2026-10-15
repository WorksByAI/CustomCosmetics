// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.config;

import java.util.*;

/** Local identity filter; it never changes the remote player's saved appearance. */
public final class PlayerBlocklist {
    public record Entry(UUID uuid,String account){}
    private PlayerBlocklist(){}
    public static boolean valid(String name){return name!=null&&name.matches("[A-Za-z0-9_]{1,16}");}
    public static List<Entry> normalize(Collection<Entry> entries){
        if(entries==null)return List.of();
        var unique=new LinkedHashMap<String,Entry>();
        for(var entry:entries)if(entry!=null&&entry.account()!=null){
            String clean=entry.account().strip();if(valid(clean))unique.putIfAbsent(entry.uuid()==null?clean.toLowerCase(Locale.ROOT):entry.uuid().toString(),new Entry(entry.uuid(),clean));
        }
        return List.copyOf(unique.values());
    }
    public static boolean contains(Collection<Entry> entries,UUID uuid,String account){
        return entries!=null&&entries.stream().anyMatch(entry->entry!=null&&(entry.uuid()!=null?entry.uuid().equals(uuid):account!=null&&account.equalsIgnoreCase(entry.account())));
    }
}
