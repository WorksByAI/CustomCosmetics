// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Local Catacombs context, independent of the server address or Mod API handshake timing. */
public final class DungeonSidebar {
    private static final Pattern LOCATION=Pattern.compile("^[^a-z0-9]*(?:the\\s+)?catacombs(?:\\s*\\(([fm][1-7]|e)\\))?\\s*$");
    private static final Pattern KUUDRA=Pattern.compile("^[^a-z0-9]*kuudra['’ʼ]?s hollow(?:\\s*\\((?:t[1-5]|tier [1-5]|basic|hot|burning|fiery|infernal)\\))?\\s*$");
    private static final Pattern FORMAT=Pattern.compile("§[0-9a-fk-orxA-FK-ORX]"),INVISIBLE=Pattern.compile("[\\p{Cf}\\p{Cc}\\p{Co}]"),SPACE=Pattern.compile("\\s+");
    private static final ThreadLocal<Map<String,String>> NORMALIZED=ThreadLocal.withInitial(()->new LinkedHashMap<>(256,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> entry){return size()>256;}
    });
    private DungeonSidebar(){}
    public static String normalize(String text){
        if(text==null)return "";var cache=NORMALIZED.get();var found=cache.get(text);if(found!=null)return found;
        String result=SPACE.matcher(INVISIBLE.matcher(FORMAT.matcher(Normalizer.normalize(text,Normalizer.Form.NFKC)).replaceAll("")).replaceAll("")).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
        if(text.length()<=1024)cache.put(text,result);return result;
    }
    public static String floor(Collection<String> rows){for(String line:rows){var match=LOCATION.matcher(normalize(line));if(match.matches()&&match.group(1)!=null)return match.group(1).toUpperCase(Locale.ROOT);}return "";}
    static boolean branded(String title,Collection<String> rows){return normalize(title).contains("skyblock")||rows.stream().anyMatch(s->normalize(s).contains("www.hypixel.net"));}
    static boolean outsideDungeon(Collection<String> rows){
        for(String line:rows){
            if(line==null)continue;
            String visible=FORMAT.matcher(Normalizer.normalize(line,Normalizer.Form.NFKC)).replaceAll("").strip();
            if((visible.startsWith("⏣")||visible.startsWith("\ue067"))&&!LOCATION.matcher(normalize(line)).matches())return true;
        }
        return false;
    }
    public static boolean kuudra(String title,Collection<String> rows){
        var lines=rows.stream().filter(Objects::nonNull).map(DungeonSidebar::normalize).toList();
        return (normalize(title).contains("skyblock")||lines.stream().anyMatch(s->s.contains("www.hypixel.net")))&&lines.stream().anyMatch(s->KUUDRA.matcher(s).matches());
    }
    public static boolean matches(String title,Collection<String> rows){
        var lines=rows.stream().filter(Objects::nonNull).map(DungeonSidebar::normalize).toList();
        boolean branded=normalize(title).contains("skyblock")||lines.stream().anyMatch(s->s.contains("www.hypixel.net"));
        if(!branded)return false;
        boolean timed=lines.stream().anyMatch(s->s.startsWith("time elapsed:"));
        boolean cleared=lines.stream().anyMatch(s->s.startsWith("cleared:")&&s.contains("%"));
        for(String line:lines){var location=LOCATION.matcher(line);if(location.matches()&&(location.group(1)!=null||timed&&cleared))return true;}
        return false;
    }
}
