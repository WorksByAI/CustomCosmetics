// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

/** Immutable account index and bounded match-plan cache; never changes a server component or chat packet. */
public final class RankText {
    public record Target(String name,String prefix) {}
    private record Glyph(int cp,Style style) {}
    private record Prefix(List<Glyph> glyphs,String text) {}
    private record Edit(int from,int to,List<Glyph> prefix) {}
    private final Map<String,Prefix> targets=new HashMap<>();
    private static final Map<net.minecraft.network.chat.TextColor,String> LEGACY_COLORS=legacyColors();
    private static final char[] HEX="0123456789abcdef".toCharArray();
    private static final Pattern ACCOUNTS=Pattern.compile("(?<![A-Za-z0-9_])[A-Za-z0-9_]{1,16}(?![A-Za-z0-9_])");
    private final Map<String,List<Edit>> plans=new LinkedHashMap<>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,List<Edit>> eldest){return size()>512;}
    };
    private static final Pattern RANK=Pattern.compile("\\[(?:VIP\\+?|MVP\\+{0,2}|YOUTUBE|ADMIN|GM|MOD|HELPER|OWNER|PIG\\+{0,3})]",Pattern.CASE_INSENSITIVE);
    public RankText(List<Target> values){
        var prefixes=new HashMap<String,Prefix>();
        for(var target:values)if(target.name()!=null&&ACCOUNTS.matcher(target.name()).matches()&&!target.prefix().isEmpty())
            targets.put(target.name().toLowerCase(Locale.ROOT),prefixes.computeIfAbsent(target.prefix(),prefix->{
                var glyphs=glyphs(sink->StringDecomposer.iterateFormatted(prefix,Style.EMPTY,sink));var plain=new StringBuilder();
                var normalized=new ArrayList<Glyph>(glyphs.size());
                for(var glyph:glyphs){plain.appendCodePoint(glyph.cp());normalized.add(new Glyph(glyph.cp(),glyph.style().withBold(glyph.style().isBold())));}
                return new Prefix(List.copyOf(normalized),plain.toString());
            }));
    }
    public boolean active(){return !targets.isEmpty();}
    public static FormattedCharSequence sequence(FormattedText text){
        return sink->text.visit((style,part)->StringDecomposer.iterateFormatted(part,style,sink)?Optional.<Boolean>empty():Optional.of(false),Style.EMPTY).orElse(true);
    }
    public static Component component(FormattedCharSequence sequence){
        var result=Component.empty();var run=new StringBuilder();Style[] current={Style.EMPTY};
        sequence.accept((i,s,c)->{
            if(!s.equals(current[0])){if(!run.isEmpty()){result.append(Component.literal(run.toString()).setStyle(current[0]));run.setLength(0);}current[0]=s;}
            run.appendCodePoint(c);return true;
        });
        if(!run.isEmpty())result.append(Component.literal(run.toString()).setStyle(current[0]));return result;
    }
    public FormattedCharSequence apply(FormattedCharSequence input){
        if(!active())return input;
        var plain=new StringBuilder();input.accept((i,s,c)->{plain.appendCodePoint(c);return true;});
        var edits=plan(plain.toString());if(edits.isEmpty())return input;
        var source=glyphs(input);
        var output=new ArrayList<Glyph>(source.size()+32);int cursor=0;boolean changed=false;
        for(var edit:edits){
            if(edit.from()<cursor)continue;
            output.addAll(source.subList(cursor,edit.from()));
            // Preserve click/hover/insertion on the name and surrounding message. Prefix colors are independent.
            Style base=source.get(edit.to()).style();
            for(var glyph:edit.prefix())output.add(new Glyph(glyph.cp(),glyph.style().applyTo(base)));
            output.add(new Glyph(' ',base));cursor=edit.to();changed=true;
        }
        if(!changed)return input;
        output.addAll(source.subList(cursor,source.size()));
        return sink->{int index=0;for(var glyph:output)if(!sink.accept(index++,glyph.style(),glyph.cp()))return false;return true;};
    }
    private synchronized List<Edit> plan(String plain){
        var cached=plans.get(plain);if(cached!=null)return cached;
        var edits=new ArrayList<Edit>();var matcher=ACCOUNTS.matcher(plain);
        while(matcher.find()){
            var prefix=targets.get(matcher.group().toLowerCase(Locale.ROOT));if(prefix==null)continue;
            int start=matcher.start(),end=start;
            if(plain.lastIndexOf('[',start)>plain.lastIndexOf(']',start))continue;
            while(end>0&&plain.charAt(end-1)==' ')end--;
            int from=start;
            String visiblePrefix=prefix.text();int existing=end-visiblePrefix.length();
            if(existing>=0&&plain.regionMatches(existing,visiblePrefix,0,visiblePrefix.length()))from=existing;
            if(end>0&&plain.charAt(end-1)==']'){
                int opening=plain.lastIndexOf('[',end-1);
                if(opening>=0&&(RANK.matcher(plain.substring(opening,end)).matches()||plain.substring(opening,end).contentEquals(visiblePrefix)))from=opening;
            }
            // Plans use codepoint indices, including supplementary characters before an account name.
            edits.add(new Edit(plain.codePointCount(0,from),plain.codePointCount(0,start),prefix.glyphs()));
        }
        var result=List.copyOf(edits);if(plain.length()<=8192)plans.put(plain,result);return result;
    }
    public String apply(String text){
        if(!active()||text==null||text.isEmpty())return text;
        FormattedCharSequence source=sink->StringDecomposer.iterateFormatted(text,Style.EMPTY,sink);
        var result=apply(source);if(result==source)return text;
        return legacy(result);
    }
    public static String legacy(FormattedCharSequence result){
        var output=new StringBuilder();Style[] previous={Style.EMPTY};
        result.accept((index,style,cp)->{
            if(!style.equals(previous[0])){
                output.append('\u00a7').append('r');
                if(style.getColor()!=null){int rgb=style.getColor().getValue();
                    String legacy=LEGACY_COLORS.get(style.getColor());
                    if(legacy!=null)output.append(legacy);else{output.append("\u00a7x");for(int shift=20;shift>=0;shift-=4)output.append('\u00a7').append(HEX[(rgb>>shift)&15]);}}
                if(style.isBold())output.append("\u00a7l");if(style.isItalic())output.append("\u00a7o");
                if(style.isUnderlined())output.append("\u00a7n");if(style.isStrikethrough())output.append("\u00a7m");if(style.isObfuscated())output.append("\u00a7k");previous[0]=style;
            }
            output.appendCodePoint(cp);return true;
        });return output.toString();
    }
    private static List<Glyph> glyphs(FormattedCharSequence sequence){
        var out=new ArrayList<Glyph>();sequence.accept((i,s,c)->{out.add(new Glyph(c,s));return true;});return out;
    }
    private static Map<net.minecraft.network.chat.TextColor,String> legacyColors(){
        var colors=new HashMap<net.minecraft.network.chat.TextColor,String>();
        for(var format:net.minecraft.ChatFormatting.values()){
            var color=net.minecraft.network.chat.TextColor.fromLegacyFormat(format);if(color!=null)colors.put(color,format.toString());
        }
        return Map.copyOf(colors);
    }
}
