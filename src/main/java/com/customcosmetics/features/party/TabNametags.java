// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import net.minecraft.network.chat.Component;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Matches identities and whole account names, never a fixed TAB row or column. */
public final class TabNametags {
    public record Row(UUID uuid,String account,Component display){}
    private TabNametags(){}
    public static Component find(UUID uuid,String account,List<Row> rows){
        var exact=rows.stream().filter(r->r.uuid().equals(uuid)&&r.display()!=null).toList();
        if(!exact.isEmpty())return unique(exact);
        var profile=rows.stream().filter(r->r.account().equalsIgnoreCase(account)&&r.display()!=null).toList();
        if(!profile.isEmpty())return unique(profile);
        if(!account.matches("[A-Za-z0-9_]{3,16}"))return null;
        var token=Pattern.compile("(?<![A-Za-z0-9_])"+Pattern.quote(account)+"(?![A-Za-z0-9_])",Pattern.CASE_INSENSITIVE);
        return unique(rows.stream().filter(r->{
            if(r.display()==null)return false;
            String text=Normalizer.normalize(r.display().getString().replaceAll("§[0-9a-fk-orxA-FK-ORX]",""),Normalizer.Form.NFKC).strip();
            var match=token.matcher(text);if(!match.find())return false;
            String prefix=text.substring(0,match.start()).strip(),suffix=text.substring(match.end()).strip();
            // SkyBlock level/rank prefixes, including both supplied TAB layouts.
            if(prefix.matches("\\[?[0-9]{1,4}\\]?")||prefix.matches("(?:\\[[^\\]\\r\\n]{1,32}\\]\\s*)+"))return true;
            // Bare names may have badge glyphs, but headings/statistics such as Players (21) are not names.
            return prefix.isEmpty()&&!suffix.isEmpty()&&!suffix.matches(".*[A-Za-z0-9_].*");
        }).toList());
    }
    private static Component unique(List<Row> rows){
        Component result=null;
        for(var row:rows){if(result!=null&&!result.equals(row.display()))return null;result=row.display();}
        return result;
    }
}
