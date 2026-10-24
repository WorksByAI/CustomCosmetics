// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.google.gson.JsonParser;
import java.util.*;
import net.minecraft.network.chat.*;
import net.minecraft.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RankTextTest {
    private RankText renderer(String prefix){return new RankText(List.of(new RankText.Target("Alice",prefix)));}
    private FormattedCharSequence sequence(String text){return sink->StringDecomposer.iterateFormatted(text,Style.EMPTY,sink);}
    private String plain(FormattedCharSequence text){var s=new StringBuilder();text.accept((i,style,cp)->{s.appendCodePoint(cp);return true;});return s.toString();}
    @Test void selectedMvpPlusPlusColorsBothPlusesAndRoundTripsForEveryPreset(){
        for(var color:RankSettings.PlusColor.values()){
            var setting=new RankSettings(true,color.id(),"mvp_plus_plus");assertEquals(setting,RankSettings.parse(setting.json()));
            assertEquals("[MVP++]",setting.label());var r=renderer(setting.prefix());
            for(String original:List.of("Alice","[VIP] Alice","[MVP+] Alice","[MVP++] Alice","[YOUTUBE] Alice")){
                var text=r.apply(sequence(original));assertEquals("[MVP++] Alice",plain(text));int[] count={0};
                int expected=TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.getByCode(color.code)).getValue();
                text.accept((i,s,c)->{if(c=='+'){count[0]++;assertEquals(expected,s.getColor().getValue());}if(c=='['||c==']')assertEquals(0xFFAA00,s.getColor().getValue());return true;});
                assertEquals(2,count[0]);assertEquals(plain(text),plain(r.apply(text)));
                String legacy=r.apply(original);assertEquals("[MVP++] Alice",plain(sequence(legacy)));assertEquals(legacy,r.apply(legacy));
            }
        }
        assertEquals("mvp_plus",RankSettings.parse(JsonParser.parseString("{\"enabled\":true,\"plusColor\":\"red\"}")).type());
        assertEquals("mvp_plus",new RankSettings(true,"red","admin").type());
        assertEquals("[YOUTUBE] Alice",plain(renderer("\u00a7c[\u00a7fYOUTUBE\u00a7c]").apply(sequence("[MVP++] Alice"))));
    }
    @Test void allExistingRanksAndUnrankedNamesBecomeOneMvpPrefix(){
        var renderer=renderer(new RankSettings(true,"dark_blue").prefix());
        for(String rank:List.of("","[VIP] ","[VIP+] ","[MVP] ","[MVP+] ","[MVP++] ","[YOUTUBE] ","[ADMIN] ")){
            var result=renderer.apply(sequence("[100] "+rank+"Alice: hello"));
            assertEquals("[100] [MVP+] Alice: hello",plain(result));
            assertEquals(plain(result),plain(renderer.apply(result)));
            result.accept((i,style,cp)->{if(cp=='+')assertEquals(0x0000AA,style.getColor().getValue());return true;});
        }
    }
    @Test void boundariesUnicodeMultipleAccountsAndUnrelatedBadgesArePreserved(){
        var r=new RankText(List.of(new RankText.Target("Alice","\u00a7c[ADMIN]"),new RankText.Target("Bob","\u00a7c[\u00a7fYOUTUBE\u00a7c]")));
        assertEquals("😀 Party > [ADMIN] Alice: [YOUTUBE] Bob Alice_2 Malice",plain(r.apply(sequence("😀 Party > [VIP] Alice: Bob Alice_2 Malice"))));
        assertEquals("[GUILD] [ADMIN] Alice",plain(r.apply(sequence("[GUILD] Alice"))));
        assertSame(FormattedCharSequence.EMPTY,new RankText(List.of()).apply(FormattedCharSequence.EMPTY));
        var unchanged=sequence("No known account here");assertSame(unchanged,r.apply(unchanged));
    }
    @Test void customAdminLabelsAreIdempotentAndNamesInsideBadgesAreNotPlayers(){
        var r=renderer("\u00a7d[SKY ADMIN]");var text=r.apply(sequence("[MVP+] Alice"));
        assertEquals("[SKY ADMIN] Alice",plain(r.apply(text)));
        var bare=renderer("\u00a7dCUSTOM");assertEquals("CUSTOM Alice",plain(bare.apply(bare.apply(sequence("Alice")))));
        var mvp=new RankText(List.of(new RankText.Target("MVP","\u00a7c[ADMIN]")));
        assertEquals("[MVP+] Other",plain(mvp.apply(sequence("[MVP+] Other"))));
        assertEquals("[ADMIN] MVP",plain(mvp.apply(sequence("[MVP+] MVP"))));
    }
    @Test void nameAndMessageStylesAndClickActionsSurviveWithoutLeakingPrefixColors(){
        var style=Style.EMPTY.withColor(0x123456).withBold(true).withInsertion("Alice");
        var source=Component.literal("[VIP] ").withStyle(Style.EMPTY.withColor(0x55FF55)).append(Component.literal("Alice").setStyle(style)).append(Component.literal(": hello").withStyle(Style.EMPTY.withColor(0xFFFFFF)));
        var result=renderer("\u00a7c[ADMIN]").apply(source.getVisualOrderText());
        var glyphs=new ArrayList<Style>();result.accept((i,s,c)->{glyphs.add(s);return true;});
        assertEquals("[ADMIN] Alice: hello",plain(result));assertEquals(style,glyphs.get(8));assertEquals(0xFFFFFF,glyphs.get(13).getColor().getValue());
        assertEquals(0xFF5555,glyphs.getFirst().getColor().getValue());assertFalse(glyphs.getFirst().isBold());
    }
    @Test void officialPaletteAndUntrustedPayloadsStayRestricted(){
        assertEquals(14,RankSettings.PlusColor.values().length);
        for(var color:RankSettings.PlusColor.values())assertEquals(new RankSettings(true,color.id()),RankSettings.parse(new RankSettings(true,color.id()).json()));
        assertEquals("red",new RankSettings(true,"#ABCDEF").plusColor());
        assertEquals(RankSettings.OFF,RankSettings.parse(JsonParser.parseString("[]")));
        assertEquals("\u00a7c[ADMIN]",RankSettings.adminPrefix("&c[ADMIN]"));
        assertEquals("",RankSettings.adminPrefix("[ADMIN]\nAlice"));
        assertEquals("",RankSettings.adminPrefix("x".repeat(129)));
    }
    @Test void legacyStringsKeepThePlusColorAndOriginalNameAndDoNotGrowOnRepeatedPreparation(){
        var r=renderer(new RankSettings(true,"black").prefix());var text=r.apply("\u00a7a[VIP] Alice\u00a7f: hello");
        assertEquals("[MVP+] Alice: hello",plain(sequence(text)));assertEquals(text,r.apply(text));
        sequence(text).accept((i,s,c)->{if(c=='+')assertEquals(0,s.getColor().getValue());if(c=='A')assertEquals(0x55FF55,s.getColor().getValue());return true;});
    }
}
