// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
public final class PartyChatRules{
    private PartyChatRules(){}
    public static String prefix(String value){
        if(value==null||value.codePointCount(0,value.length())>16||value.codePoints().anyMatch(cp->cp<32||cp==127||cp==167||cp>=0xD800&&cp<=0xDFFF))return "`";
        return value;
    }
    public static boolean intercept(String message,boolean enabled,String prefix){return enabled&&message!=null&&!message.startsWith("/")&&message.startsWith(prefix(prefix));}
    public static String body(String message,String prefix){return message.substring(prefix(prefix).length()).strip();}
    public static boolean valid(String message){
        if(message==null||message.isBlank()||message.codePointCount(0,message.length())>256)return false;
        return message.codePoints().noneMatch(cp->cp<32||cp==127||cp==167||cp>=0xD800&&cp<=0xDFFF);
    }
}
