// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;

/** Shared bounded deduplication for authenticated HTTP and server WebSocket announcements. */
public final class ServerNoticeInbox {
    public record Notice(String id,String display,String message){}
    private final LinkedHashMap<String,Long> seen=new LinkedHashMap<>();
    public synchronized Notice offer(JsonObject value,long now){
        try{
            if(!"announcement".equals(string(value,"type"))||value.get("version").getAsInt()!=1)return null;
            String id=string(value,"id"),display=string(value,"display"),message=string(value,"message");
            long created=value.get("createdAt").getAsLong(),expires=value.get("expiresAt").getAsLong();
            if(!id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")||!display.equals("chat")&&!display.equals("title")
                    ||message.isBlank()||message.length()>(display.equals("title")?120:400)||message.split("\n",-1).length>3
                    ||message.chars().anyMatch(c->c<32&&c!='\n'&&c!='\r'||c==127)
                    ||created<0||expires<=now||created>now+30_000||expires<=created||expires-created>120_000)return null;
            seen.entrySet().removeIf(entry->entry.getValue()<=now);
            if(seen.containsKey(id))return null;
            if(seen.size()>=128)seen.remove(seen.keySet().iterator().next());
            seen.put(id,expires+60_000);return new Notice(id,display,message);
        }catch(RuntimeException invalid){return null;}
    }
    private static String string(JsonObject object,String name){
        var value=object.getAsJsonPrimitive(name);
        if(!value.isString())throw new IllegalArgumentException("Expected text");return value.getAsString();
    }
}
