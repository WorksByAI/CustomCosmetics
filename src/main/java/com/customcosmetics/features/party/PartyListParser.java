// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
import java.util.*;
import java.util.regex.Pattern;

/** Only complete server /p list replies are committed; normal chat cannot add members. */
public final class PartyListParser{
    private static final Pattern HEADER=Pattern.compile("^Party Members \\((\\d{1,4})\\)$");
    private static final Pattern LINE=Pattern.compile("^Party (?:Leader|Moderators|Members):\\s*(.*)$");
    private final Set<String> draft=new HashSet<>();private int expected;private long deadline;
    public Optional<PartyRoster> accept(String raw,long now){
        Optional<PartyRoster> completed=Optional.empty();
        for(String line:raw.lines().toList()){
            var result=acceptLine(line,now);if(result.isPresent())completed=result;
        }
        return completed;
    }
    private Optional<PartyRoster> acceptLine(String raw,long now){
        if(now>deadline){expected=0;draft.clear();}
        String text=raw.replaceAll("§[0-9a-fk-orA-FK-OR]","").trim();var header=HEADER.matcher(text);
        if(header.matches()){expected=Integer.parseInt(header.group(1));draft.clear();deadline=now+5000;if(expected<1||expected>1000)expected=0;return Optional.empty();}
        if(expected==0)return Optional.empty();var line=LINE.matcher(text);if(!line.matches())return Optional.empty();
        for(String word:line.group(1).replaceAll("\\[[^]]*]","").split("[^A-Za-z0-9_]+"))if(word.matches("[A-Za-z0-9_]{3,16}"))draft.add(word.toLowerCase(Locale.ROOT));
        if(draft.size()!=expected)return Optional.empty();var result=new PartyRoster(Set.of(),draft,"/p list");expected=0;draft.clear();return Optional.of(result);
    }
    public void reset(){draft.clear();expected=0;deadline=0;}
    public static boolean leftOrJoined(String raw){
        String text=raw.replaceAll("§[0-9a-fk-orA-FK-OR]","").trim();
        return text.equals("You left the party.")||text.startsWith("You are not currently in a party")||text.startsWith("You are not in a party")||
            text.startsWith("The party was disbanded")||text.startsWith("Your party has been disbanded")||text.matches("^(?:\\[[^]]+] )?[A-Za-z0-9_]{3,16} has disbanded the party[.!]$")||
            text.startsWith("You have been kicked from the party")||text.startsWith("You have been removed from the party")||
            text.startsWith("You have joined ")&&text.endsWith(" party!");
    }
    public static boolean changed(String raw){
        String text=raw.replaceAll("§[0-9a-fk-orA-FK-OR]","").trim();
        return text.equals("You left the party.")||text.startsWith("You are not currently in a party")||text.startsWith("You are not in a party")||
            text.matches("^(?:\\[[^]]+] )?[A-Za-z0-9_]{3,16} (?:(?:has )?(?:joined|left)|was removed from|has been removed from) the party[.!]$")||
            text.startsWith("The party was disbanded")||text.startsWith("Your party has been disbanded")||text.startsWith("You have been kicked from the party")||text.startsWith("You have been removed from the party")||text.matches("^(?:\\[[^]]+] )?[A-Za-z0-9_]{3,16} has disbanded the party[.!]$")||
            text.startsWith("You have removed ")&&text.contains(" from your party")||
            text.startsWith("You have joined ")&&text.endsWith(" party!");
    }
}
