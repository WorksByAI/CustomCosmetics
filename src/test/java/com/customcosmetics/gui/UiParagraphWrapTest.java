package com.customcosmetics.gui;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UiParagraphWrapTest {
    private static String visible(String text){return text.replaceAll("§[0-9a-fk-orA-FK-OR]","");}
    private static int pixels(String text){return visible(text).codePoints().map(c->Character.getType(c)==Character.NON_SPACING_MARK?0:Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN?10:5).sum();}
    @Test void mixedChineseParagraphUsesAvailableWidthBeyondEarlyAsciiSpaces(){
        String text="灵感来源于 CustomSkinLoader。皮肤分享仅在 P2P 打洞成功后对方可见。使用本功能产生的后果由使用者自行承担，模组开发者不承担相关责任。";
        var lines=UiParagraphWrap.lines(text,180,UiParagraphWrapTest::pixels);
        assertTrue(lines.size()>1);assertTrue(pixels(lines.getFirst())>=150,lines.toString());
        assertTrue(lines.stream().allMatch(line->pixels(line)<=180),lines.toString());
        assertEquals(text.replace(" ",""),String.join("",lines).replace(" ",""));
    }
    @Test void englishUsesWordBoundariesAndLongWordsStillFit(){
        assertEquals(List.of("alpha beta","gamma delta"),UiParagraphWrap.lines("alpha beta gamma delta",11,String::length));
        assertEquals(List.of("abcd","efgh","ij"),UiParagraphWrap.lines("abcdefghij",4,String::length));
    }
    @Test void fontMetricsAreSuppliedByTheActualRenderingStyle(){
        assertEquals(List.of("abc def"),UiParagraphWrap.lines("abc def",7,String::length));
        assertEquals(List.of("abc","def"),UiParagraphWrap.lines("abc def",7,text->text.length()*2));
    }
    @Test void surrogatePairsCombiningMarksAndEmojiSequencesRemainWhole(){
        String text="中😀e\u0301文👩‍💻末";
        var lines=UiParagraphWrap.lines(text,10,line->line.isEmpty()?0:line.codePointCount(0,line.length())*5);
        assertEquals(text,String.join("",lines));assertTrue(lines.contains("e\u0301"));assertTrue(lines.contains("👩‍💻"));
        for(String line:lines){assertFalse(Character.isHighSurrogate(line.charAt(line.length()-1)));assertFalse(Character.isLowSurrogate(line.charAt(0)));assertFalse(line.startsWith("\u200d")||line.endsWith("\u200d"));}
    }
    @Test void formattingPairsAndActiveStyleSurviveWrappedLines(){
        String text="§a绿色名字§l加粗文字§r恢复";var lines=UiParagraphWrap.lines(text,20,UiParagraphWrapTest::pixels);
        assertEquals(visible(text),visible(String.join("",lines)));
        assertTrue(lines.get(1).startsWith("§a"));assertTrue(lines.stream().allMatch(line->!line.endsWith("§")&&pixels(line)<=20));
        assertTrue(lines.stream().anyMatch(line->line.startsWith("§a§l")));
    }
    @Test void hardNewlinesAndAnOversizedGlyphDoNotLoseText(){
        assertEquals(List.of("a","","b"),UiParagraphWrap.lines("a\r\n\nb",10,String::length));
        assertEquals(List.of("😀"),UiParagraphWrap.lines("😀",1,UiParagraphWrapTest::pixels));
        assertThrows(IllegalArgumentException.class,()->UiParagraphWrap.lines("text",0,String::length));
    }
}
