// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import java.text.Normalizer;
import java.util.*;

/** Search data comes from the actual controls, so labels and destinations stay in step. */
public final class SettingsSearch {
    public record Entry(String section, String title, String description, String page, int offset) {}
    private static final Map<String,String> ALIASES=Map.ofEntries(
        Map.entry("伤害","damage"),Map.entry("暴击","critical"),Map.entry("大小","size"),Map.entry("颜色","color"),
        Map.entry("昵称","nick"),Map.entry("名字","name"),Map.entry("披风","cape"),Map.entry("追踪","tracer"),
        Map.entry("位置","position"),Map.entry("格挡","block"),Map.entry("挥动","swing"),Map.entry("速度","speed"),
        Map.entry("代理","proxy"),Map.entry("流光","shimmer"),Map.entry("鎏金","shimmer"),Map.entry("预设","preset"),Map.entry("同步","sync"),Map.entry("队伍","party"),
        Map.entry("装备","equipment"),Map.entry("物品","item"),Map.entry("字体","bold"),Map.entry("动画","animation"));
    private SettingsSearch() {}
    private static String fold(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+"," ").strip();}
    public static List<Entry> find(Collection<Entry> entries,String query){
        if(query==null||query.isBlank())return List.of();
        String normalized=fold(query.length()>128?query.substring(0,128):query);
        for(var alias:ALIASES.entrySet())normalized=normalized.replace(alias.getKey()," "+alias.getValue()+" ");
        String needle=fold(normalized);if(needle.isEmpty())return List.of();
        var terms=needle.split("\\s+");
        return entries.stream().distinct().filter(entry->{String haystack=fold(entry.section()+" "+entry.title()+" "+entry.description());return Arrays.stream(terms).allMatch(haystack::contains);})
            .sorted(Comparator.comparingInt((Entry e)->fold(e.title()).equals(needle)?0:fold(e.title()).startsWith(needle)?1:2)
                .thenComparing(Entry::section).thenComparing(Entry::title)).toList();
    }
}
