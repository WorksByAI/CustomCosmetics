// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Mod-owned UI text only. Catalogues are read once; no resource reload or gameplay hook. */
public final class UiText {
    private static final Map<String,String> EN=load("en_us"),ZH=load("zh_cn");
    private static final Map<String,String> CHINESE=chinese();
    private static final List<Map.Entry<String,String>> PREFIXES=CHINESE.entrySet().stream()
        .filter(e->e.getKey().endsWith(": ")||e.getKey().endsWith(" · ")||e.getKey().endsWith(" (")||e.getKey().endsWith(" #"))
        .sorted(Comparator.comparingInt((Map.Entry<String,String> e)->e.getKey().length()).reversed()).toList();
    private static final Set<String> ENUM_PREFIXES=Set.of("Mode: ","Style: ","Animation: ","Color Mode: ","Gradient Style: ","Editing: ","Skin Model: ","Snaplook View: ","Proxy Source: ","ESP Style: ","Nametag Style: ","Nametag Scaling: ","Audience: ","Names: ","Distance Text: ","Blocking List: ","Blocking Style: ","Allow Skipping: ","Allow Going Backwards: ","Go Backwards: ","Render Line: ","Separator: ");
    private static final ConcurrentHashMap<String,String> DYNAMIC=new ConcurrentHashMap<>();
    private static volatile String preference="auto",gameLanguage="en_us",effective="en_us";
    private static volatile int revision;
    private UiText(){}
    private static Map<String,String> load(String language){
        try(var stream=UiText.class.getResourceAsStream("/assets/customcosmetics/lang/"+language+".json")){
            if(stream==null)throw new IllegalStateException("Missing mod language catalogue: "+language);
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();var map=new HashMap<String,String>();
            for(var entry:root.entrySet())if(entry.getKey().startsWith("customcosmetics.ui.")||entry.getKey().startsWith("customcosmetics.slot."))map.put(entry.getKey(),entry.getValue().getAsString());
            return Map.copyOf(map);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    private static Map<String,String> chinese(){var result=new HashMap<String,String>();for(var e:EN.entrySet())if(e.getKey().startsWith("customcosmetics.ui."))result.put(e.getValue(),ZH.getOrDefault(e.getKey(),e.getValue()));return Map.copyOf(result);}
    public static String normalize(String choice){return "en_us".equals(choice)||"zh_cn".equals(choice)?choice:"auto";}
    public static void configure(String choice,String game){
        preference=normalize(choice);gameLanguage=game==null?"en_us":game;
        String next=preference.equals("auto")?(gameLanguage.equals("zh_cn")?"zh_cn":"en_us"):preference;
        if(!next.equals(effective)){effective=next;DYNAMIC.clear();revision++;}
    }
    public static int revision(){return revision;}
    public static String language(){return effective;}
    public static String name(String choice){return switch(normalize(choice)){case "zh_cn"->"简体中文";case "en_us"->"English";default->tr("Follow Game");};}
    /** Translation must not override the active font in chat, item tooltips or other game text. */
    public static net.minecraft.network.chat.MutableComponent component(String source){return net.minecraft.network.chat.Component.literal(tr(source));}
    /** Explicitly opt mod-screen tooltips into the interface font; never use for game messages. */
    public static net.minecraft.network.chat.MutableComponent guiComponent(String source){var text=component(source);return UiFonts.modern()?text.withStyle(style->style.withFont(UiFonts.CHINESE)):text;}
    public static String slotLabel(String id,String source){return effective.equals("zh_cn")?ZH.getOrDefault("customcosmetics.slot."+id,zh(source)):source;}
    public static String display(String source,boolean raw){return raw?source:tr(source);}
    public static String tr(String source){return effective.equals("zh_cn")?zh(source):source;}
    /** Search uses this independently of the selected interface language. */
    public static String zh(String source){
        if(source==null||source.isEmpty())return source;
        String direct=CHINESE.get(source);if(direct!=null)return direct;
        String cached=DYNAMIC.get(source);if(cached!=null)return cached;
        String result=source;
        int http=source.indexOf(" (HTTP ");if(http>0){String reason=CHINESE.get(source.substring(0,http));if(reason!=null)return reason+source.substring(http);}
        if(source.endsWith("  ▾"))result=zh(source.substring(0,source.length()-3))+"  ▾";
        else if(source.startsWith("✓ "))result="✓ "+zh(source.substring(2));
        else for(var prefix:PREFIXES)if(source.startsWith(prefix.getKey())){
            String value=source.substring(prefix.getKey().length());
            result=prefix.getValue()+(ENUM_PREFIXES.contains(prefix.getKey())?CHINESE.getOrDefault(value,value):value);break;
        }
        if(result.equals(source)&&Character.isDigit(source.charAt(0))){
            int digits=1;while(digits<source.length()&&Character.isDigit(source.charAt(digits)))digits++;
            String suffix=CHINESE.get(source.substring(digits));if(suffix!=null)result=source.substring(0,digits)+suffix;
        }
        if(result.equals(source)){
            int dot=source.indexOf(" · ");if(dot>0){String left=CHINESE.get(source.substring(0,dot)),right=CHINESE.get(source.substring(dot+3));if(left!=null&&right!=null)result=left+" · "+right;}
            int colon=source.indexOf(": ");
            if(colon>0){String title=CHINESE.get(source.substring(0,colon));if(title!=null){String value=source.substring(colon+2);result=title+"："+CHINESE.getOrDefault(value,value);}}
        }
        if(!result.equals(source)&&DYNAMIC.size()<2048)DYNAMIC.putIfAbsent(source,result);
        return result;
    }
}
