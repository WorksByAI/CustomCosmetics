// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import java.util.*;

/** Counts players whose current visual state actually arrived on each path, not standby sockets. */
final class RealtimeConnections {
    enum Path { P2P, UDP, TCP }
    private record Key(UUID player,String channel) {}
    private record Delivery(Path path,long expiresAt,long order) {}
    private final Map<Key,Delivery> deliveries=new HashMap<>();
    private long order;

    void received(UUID player,String channel,Path path,long expiresAt){
        deliveries.put(new Key(player,channel),new Delivery(path,expiresAt,++order));
    }
    void remove(UUID player,String channel){deliveries.remove(new Key(player,channel));}
    void retainBlocking(Set<UUID> players){deliveries.keySet().removeIf(key->key.channel().equals("blocking")&&!players.contains(key.player()));}
    void clear(){deliveries.clear();}

    String status(boolean connected,Set<UUID> direct,long now){
        if(!connected)return "Connecting";
        deliveries.values().removeIf(delivery->delivery.expiresAt()<=now);
        var latest=new HashMap<UUID,Delivery>();
        deliveries.forEach((key,delivery)->latest.merge(key.player(),delivery,(a,b)->a.order()>b.order()?a:b));
        var counts=new EnumMap<Path,Integer>(Path.class);
        direct.forEach(id->counts.merge(Path.P2P,1,Integer::sum));
        latest.forEach((id,delivery)->{
            if(!direct.contains(id)&&delivery.path()!=Path.P2P)counts.merge(delivery.path(),1,Integer::sum);
        });
        var text=new StringBuilder("Connected");
        for(var path:Path.values())if(counts.getOrDefault(path,0)>0)text.append('\n').append(path).append(": ").append(counts.get(path));
        return text.toString();
    }
}
