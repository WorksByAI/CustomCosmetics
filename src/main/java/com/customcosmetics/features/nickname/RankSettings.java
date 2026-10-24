// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;

/** Player-selectable MVP+/MVP++ and Hypixel plus colors. Arbitrary prefixes belong to the administrator. */
public record RankSettings(boolean enabled, String plusColor, String type) {
    public enum PlusColor {
        RED("Red", 'c'), GOLD("Gold", '6'), GREEN("Green", 'a'), YELLOW("Yellow", 'e'),
        LIGHT_PURPLE("Light Purple", 'd'), WHITE("White", 'f'), BLUE("Blue", '9'),
        DARK_GREEN("Dark Green", '2'), DARK_RED("Dark Red", '4'), DARK_AQUA("Dark Aqua", '3'),
        DARK_PURPLE("Dark Purple", '5'), DARK_GRAY("Dark Gray", '8'), BLACK("Black", '0'), DARK_BLUE("Dark Blue", '1');
        public final String label;
        public final char code;
        private final String id;
        private static final PlusColor[] COLORS=values();
        PlusColor(String label, char code) { this.label=label; this.code=code; this.id=name().toLowerCase(Locale.ROOT); }
        public String id() { return id; }
        public static PlusColor parse(String value) {
            for (var color : COLORS) if (color.id.equals(value)) return color;
            return RED;
        }
    }
    public static final RankSettings OFF = new RankSettings(false,"red");
    public RankSettings(boolean enabled,String plusColor){this(enabled,plusColor,"mvp_plus");}
    public RankSettings { plusColor=PlusColor.parse(plusColor).id();type=normalizeType(type); }
    public static String normalizeType(String type){return "mvp_plus_plus".equals(type)?type:"mvp_plus";}
    public String label(){return type.equals("mvp_plus_plus")?"[MVP++]":"[MVP+]";}
    public String prefix() {
        boolean doublePlus=type.equals("mvp_plus_plus");String body=doublePlus?"\u00a76":"\u00a7b";
        return body+"[MVP\u00a7"+PlusColor.parse(plusColor).code+(doublePlus?"++":"+")+body+"]";
    }
    public JsonObject json() {
        var json=new JsonObject();json.addProperty("enabled",enabled);json.addProperty("plusColor",plusColor);json.addProperty("type",type);return json;
    }
    public static RankSettings parse(JsonElement value) {
        if (value==null||!value.isJsonObject()) return OFF;
        try {
            var json=value.getAsJsonObject();
            return new RankSettings(json.has("enabled")&&json.get("enabled").getAsBoolean(),
                json.has("plusColor")?json.get("plusColor").getAsString():"red",json.has("type")?json.get("type").getAsString():"mvp_plus");
        } catch (RuntimeException invalid) { return OFF; }
    }
    /** A short single-line legacy-colored prefix, accepted only from the authenticated server's admin snapshot. */
    public static String adminPrefix(String value) {
        if(value==null||value.length()>128||value.isBlank())return "";
        StringBuilder result=new StringBuilder();
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if((c=='&'||c=='\u00a7')&&i+1<value.length()&&"0123456789abcdefklmnor".indexOf(Character.toLowerCase(value.charAt(i+1)))>=0){
                result.append('\u00a7').append(Character.toLowerCase(value.charAt(++i)));continue;
            }
            if(c<32||c==127||c=='\u00a7')return "";
            result.append(c);
        }
        return result.toString().strip();
    }
}
