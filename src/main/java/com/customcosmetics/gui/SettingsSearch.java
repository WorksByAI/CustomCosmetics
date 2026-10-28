// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Immutable search data; screen widgets are created only when opening a result. */
public final class SettingsSearch {
    public record Entry(String section, String title, String description, String page, int offset) {}
    private record Prepared(Entry entry, String title, String haystack) {}
    private static final Pattern SEPARATORS = Pattern.compile("[\\p{Punct}\\s]+");
    public static final class Index {
        private final List<Prepared> entries;
        public Index(Collection<Entry> entries) {
            this.entries = entries.stream().distinct().map(entry -> new Prepared(entry, fold(entry.title()),
                fold(entry.section() + " " + entry.title() + " " + entry.description()+" "+UiText.zh(entry.section())+" "+UiText.zh(entry.title())+" "+UiText.zh(entry.description())))).toList();
        }
        public List<Entry> find(String query) {
            if(query==null||query.isBlank())return List.of();
            String bounded=query.length()>128?query.substring(0,128):query;
            String normalized=fold(bounded);
            for(var alias:ALIASES.entrySet())normalized=normalized.replace(alias.getKey()," "+alias.getValue()+" ");
            String needle=fold(normalized);if(needle.isEmpty())return List.of();
            String[] terms=needle.split("\\s+"),original=fold(bounded).split("\\s+");
            return entries.stream().filter(entry -> {
                boolean aliases=true,translated=true;
                for(String term:terms)if(!entry.haystack().contains(term)){aliases=false;break;}
                for(String term:original)if(!entry.haystack().contains(term)){translated=false;break;}
                return aliases||translated;
            }).sorted(Comparator.comparingInt((Prepared entry) -> entry.title().equals(needle)?0:entry.title().startsWith(needle)?1:2)
                .thenComparing(entry -> entry.entry().section()).thenComparing(entry -> entry.entry().title()))
                .map(Prepared::entry).toList();
        }
    }
    private static final Map<String,String> ALIASES=Map.ofEntries(
        Map.entry("伤害","damage"),Map.entry("暴击","critical"),Map.entry("大小","size"),Map.entry("颜色","color"),
        Map.entry("昵称","nick"),Map.entry("名字","name"),Map.entry("披风","cape"),Map.entry("追踪","tracer"),
        Map.entry("位置","position"),Map.entry("格挡","block"),Map.entry("挥动","swing"),Map.entry("速度","speed"),
        Map.entry("代理","proxy"),Map.entry("流光","shimmer"),Map.entry("鎏金","shimmer"),Map.entry("预设","preset"),Map.entry("同步","sync"),Map.entry("队伍","party"),
        Map.entry("装备","equipment"),Map.entry("物品","item"),Map.entry("字体","bold"),Map.entry("动画","animation"),
        Map.entry("地图","map"),Map.entry("替换","replace"),Map.entry("材质","material"),
        Map.entry("提示音","notification sound"),Map.entry("声音","sound"),Map.entry("音量","volume"),Map.entry("音高","pitch"),
        Map.entry("模块","module"),Map.entry("开关","enabled"),Map.entry("视角","view"),Map.entry("更新","update"));
    private SettingsSearch() {}
    private static String fold(String value){return SEPARATORS.matcher(Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)).replaceAll(" ").strip();}
    public static boolean sameSetting(String actual, String catalogue) {
        return fold(settingName(actual)).equals(fold(settingName(catalogue)));
    }
    private static String settingName(String title) {
        // Selected creature choices keep their display checkmark without changing their search destination.
        String name = title.startsWith("✓ ") ? title.substring(2) : title;
        int colon = name.indexOf(':'); return colon < 0 ? name : name.substring(0, colon);
    }
    public static List<Entry> find(Collection<Entry> entries,String query){
        return new Index(entries).find(query);
    }
}
