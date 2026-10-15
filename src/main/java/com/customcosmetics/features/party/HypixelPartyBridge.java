// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

/** Uses the installed official Mod API, including its shared packet cooldowns. */
public final class HypixelPartyBridge{
    private Object api;private Method send;private Constructor<?> request;
    public boolean register(ClassLoader loader,Consumer<PartyRoster> consumer){
        try{
            var apiType=Class.forName("net.hypixel.modapi.HypixelModAPI",false,loader);api=apiType.getMethod("getInstance").invoke(null);
            var handler=Class.forName("net.hypixel.modapi.handler.ClientboundPacketHandler",false,loader);
            var packet=Class.forName("net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket",false,loader);
            var requestType=Class.forName("net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket",false,loader);request=requestType.getConstructor();
            send=Arrays.stream(apiType.getMethods()).filter(m->m.getName().equals("sendPacket")&&m.getParameterCount()==1&&m.getParameterTypes()[0].isAssignableFrom(requestType)).findFirst().orElseThrow();
            Object callback=Proxy.newProxyInstance(loader,new Class<?>[]{handler},(proxy,method,args)->{
                if(!method.getName().equals("handle"))return switch(method.getName()){case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];case "toString"->"CustomCosmetics party listener";default->null;};
                Object value=args[0];
                if(!(boolean)packet.getMethod("isExpectedVersion").invoke(value))return null;
                if(!(boolean)packet.getMethod("isInParty").invoke(value)){consumer.accept(PartyRoster.EMPTY);return null;}
                @SuppressWarnings("unchecked") Set<UUID> members=(Set<UUID>)packet.getMethod("getMembers").invoke(value);
                consumer.accept(new PartyRoster(members,Set.of(),"Hypixel Mod API"));return null;
            });
            apiType.getMethod("createHandler",Class.class,handler).invoke(api,packet,callback);return true;
        }catch(ReflectiveOperationException|LinkageError|NoSuchElementException unavailable){api=null;return false;}
    }
    public boolean request(){if(api==null)return false;try{return !Boolean.FALSE.equals(send.invoke(api,request.newInstance()));}catch(ReflectiveOperationException unavailable){return false;}}
}
