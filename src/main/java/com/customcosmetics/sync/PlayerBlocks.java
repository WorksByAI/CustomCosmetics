// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.config.*;
import com.customcosmetics.utils.NickRenderUtils;
import java.util.*;

/** Independent, local-only filters. Identity lookup uses the existing sync cache. */
public final class PlayerBlocks {
    public enum Kind { ACCESSORIES, NICK, CAPE }
    public record Result(boolean changed,String message){}
    private PlayerBlocks(){}
    public static List<PlayerBlocklist.Entry> entries(Kind kind){return switch(kind){case NICK->ConfigManager.nickTweaksBlockedPlayers;case ACCESSORIES->ConfigManager.accessoriesBlockedPlayers;case CAPE->ConfigManager.capeBlockedPlayers;};}
    public static void set(Kind kind,List<PlayerBlocklist.Entry> entries){
        var clean=PlayerBlocklist.normalize(entries);
        switch(kind){case NICK->ConfigManager.nickTweaksBlockedPlayers=clean;case ACCESSORIES->ConfigManager.accessoriesBlockedPlayers=clean;case CAPE->ConfigManager.capeBlockedPlayers=clean;}
        ConfigManager.requestSave();if(kind==Kind.NICK)NickRenderUtils.invalidateLocalTargetsCache();
    }
    public static boolean blocked(Kind kind,UUID uuid,String account){return PlayerBlocklist.contains(entries(kind),uuid,account);}
    public static Result edit(boolean add,Kind kind,String input,List<PresenceSync.SyncedPlayer> known){
        String query=input==null?"":input.strip();
        if(query.length()>1&&query.startsWith("\"")&&query.endsWith("\""))query=query.substring(1,query.length()-1);
        String target=query;
        var accounts=known.stream().filter(p->p.name().equalsIgnoreCase(target)||p.uuid().toString().equalsIgnoreCase(target)).toList();
        var matches=accounts.isEmpty()?known.stream().filter(p->p.nickname().equalsIgnoreCase(target)).toList():accounts;
        if(matches.size()>1)return new Result(false,"Nickname matches multiple players: "+String.join(", ",matches.stream().map(PresenceSync.SyncedPlayer::name).toList())+". Use an account name.");
        PlayerBlocklist.Entry resolved;
        if(!matches.isEmpty()){var player=matches.getFirst();resolved=new PlayerBlocklist.Entry(player.uuid(),player.name());}
        else{
            var existing=entries(kind).stream().filter(e->e.account().equalsIgnoreCase(target)||e.uuid()!=null&&e.uuid().toString().equalsIgnoreCase(target)).findFirst();
            if(existing.isPresent())resolved=existing.get();
            else if(PlayerBlocklist.valid(target))resolved=new PlayerBlocklist.Entry(null,target);
            else return new Result(false,"Nickname not found in the local sync cache. Enter a synced nickname or a Minecraft account name.");
        }
        var next=new ArrayList<>(entries(kind));
        if(add){
            if(blocked(kind,resolved.uuid(),resolved.account()))return new Result(false,resolved.account()+" is already blocked for "+kind.name().toLowerCase(Locale.ROOT)+".");
            next.add(resolved);
        }else{
            if(!next.removeIf(entry->resolved.uuid()!=null?resolved.uuid().equals(entry.uuid())||entry.uuid()==null&&entry.account().equalsIgnoreCase(resolved.account()):entry.account().equalsIgnoreCase(resolved.account())))
                return new Result(false,resolved.account()+" is not blocked for "+kind.name().toLowerCase(Locale.ROOT)+".");
        }
        set(kind,next);return new Result(true,(add?"Blocked ":"Unblocked ")+resolved.account()+" for "+kind.name().toLowerCase(Locale.ROOT)+" (local only).");
    }
}
