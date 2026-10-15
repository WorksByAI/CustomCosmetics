// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
import java.util.*;

/** Membership stays local/in-memory and is never included in public users.json. */
public record PartyRoster(Set<UUID> members,Set<String> names,String source){
    public static final PartyRoster EMPTY=new PartyRoster(Set.of(),Set.of(),"None");
    public PartyRoster{
        members=Set.copyOf(members);
        var normalized=new HashSet<String>();for(String name:names){if(!name.matches("[A-Za-z0-9_]{3,16}"))throw new IllegalArgumentException("Invalid party name");normalized.add(name.toLowerCase(Locale.ROOT));}
        names=Set.copyOf(normalized);if(members.size()+names.size()>1000)throw new IllegalArgumentException("Party is too large");
    }
    public boolean contains(UUID id,String name){return members.contains(id)||name!=null&&names.contains(name.toLowerCase(Locale.ROOT));}
    public boolean empty(){return members.isEmpty()&&names.isEmpty();}
    public int size(){return members.size()+names.size();}
    public String title(){return "Party: "+size()+(size()==1?" Member.":" Members.");}
    public String sourceLabel(){return "Source: "+(empty()?"Unknown":source);}
}
