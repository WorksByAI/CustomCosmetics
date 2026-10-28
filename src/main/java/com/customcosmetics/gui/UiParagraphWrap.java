// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import java.text.BreakIterator;
import java.util.*;
import java.util.function.ToIntFunction;

/** Screen-build-only wrapping with Unicode line boundaries and the caller's actual font metrics. */
final class UiParagraphWrap {
    private UiParagraphWrap(){}
    static List<String> lines(String text,int width,ToIntFunction<String> measure){
        if(width<1)throw new IllegalArgumentException("Positive paragraph width required");
        var result=new ArrayList<String>();String carry="";
        for(String paragraph:text.split("\\R",-1)){
            if(paragraph.isEmpty()){result.add(carry);continue;}
            var boundaries=BreakIterator.getLineInstance(Locale.ROOT);boundaries.setText(paragraph);
            var legal=new BitSet(paragraph.length()+1);
            for(int at=boundaries.first();at!=BreakIterator.DONE;at=boundaries.next())legal.set(at);
            var characters=BreakIterator.getCharacterInstance(Locale.ROOT);characters.setText(paragraph);
            var ends=new ArrayList<Integer>();int at=0;
            while(at<paragraph.length()){
                // Formatting belongs to the next glyph, never to a line on its own.
                while(at+1<paragraph.length()&&paragraph.charAt(at)=='§')at+=1+Character.charCount(paragraph.codePointAt(at+1));
                if(at==paragraph.length()){
                    if(ends.isEmpty())ends.add(at);else ends.set(ends.size()-1,at);break;
                }
                at=characters.following(at);ends.add(at);
            }
            int first=0,start=0;
            while(first<ends.size()){
                int fit=first,lastBreak=-1;
                for(int i=first;i<ends.size();i++){
                    String candidate=carry+paragraph.substring(start,ends.get(i)).stripTrailing();
                    int measured=measure.applyAsInt(candidate);
                    if(measured>width&&i>first)break;
                    fit=i+1;if(legal.get(ends.get(i)))lastBreak=fit;
                    if(measured>width)break; // One oversized glyph remains intact.
                }
                int next=fit==ends.size()?fit:lastBreak>first?lastBreak:fit;
                int end=ends.get(next-1);String raw=paragraph.substring(start,end);
                result.add(carry+raw.stripTrailing());carry=formatting(carry+raw);
                first=next;start=end;
                while(first<ends.size()&&paragraph.substring(start,ends.get(first)).isBlank()){start=ends.get(first++);}
            }
        }
        return List.copyOf(result);
    }
    private static String formatting(String text){
        char color=0;boolean[] styles=new boolean[5];
        for(int i=0;i+1<text.length();i++)if(text.charAt(i)=='§'){
            char code=Character.toLowerCase(text.charAt(++i));
            if(code=='r'){color=0;Arrays.fill(styles,false);}
            else if(code>='0'&&code<='9'||code>='a'&&code<='f'){color=code;Arrays.fill(styles,false);}
            else if(code>='k'&&code<='o')styles[code-'k']=true;
        }
        var result=new StringBuilder();if(color!=0)result.append('§').append(color);
        for(int i=0;i<styles.length;i++)if(styles[i])result.append('§').append((char)('k'+i));
        return result.toString();
    }
}
