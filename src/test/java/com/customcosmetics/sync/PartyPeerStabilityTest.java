// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.features.party.PartyRoster;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Three real encrypted protocol endpoints, with deterministic delay, loss and NAT mappings. */
class PartyPeerStabilityTest {
    private static final String WORLD="a".repeat(64);
    private static final class Mesh {
        record Delivery(long due,int from,int to,byte[] bytes){}
        final List<UUID> ids=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        final PartyRoster roster=new PartyRoster(Set.copyOf(ids),Set.of(),"fixture");
        final List<PartyPeerClient> clients=new ArrayList<>();
        final PriorityQueue<Delivery> wire=new PriorityQueue<>(Comparator.comparingLong(Delivery::due));
        final long[][] probes=new long[3][3];
        long now=10000,sequence;boolean interrupt;
        Mesh()throws Exception {
            for(int i=0;i<3;i++){
                int from=i;
                clients.add(new PartyPeerClient((bytes,to)->{
                    int target=to.getPort()-30001;if(target<0||target>=3)return;
                    UdpPacket.Message packet;
                    try{packet=cipher(from,target).decode(bytes);}catch(Exception error){throw new java.io.IOException(error);}
                    var message=JsonParser.parseString(new String(packet.body(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    boolean badPair=from==2||target==2;
                    if(interrupt&&badPair)return;
                    // Ten percent of replies disappear in adjacent pairs, independently per link.
                    if(message.get("type").getAsString().equals("pong")){
                        long count=++probes[from][target];if(count%20==10||count%20==11)return;
                    }
                    wire.add(new Delivery(now+140+(sequence%3)*10,from,target,bytes));
                },ignored->{}));
                clients.get(i).context(new PartyPeerClient.Context(ids.get(i),101+i,1,WORLD,roster,true));
            }
            authorize();
        }
        String pair(int a,int b){return String.format("%032x",Math.min(a,b)*3+Math.max(a,b)+1);}
        UdpPacket cipher(int from,int to)throws Exception {
            // Decode with the receiver's direction, exactly as the receiving client does.
            return new UdpPacket(HexFormat.of().parseHex(pair(from,to)),new byte[32],ids.get(to).toString().compareTo(ids.get(from).toString())<0,4);
        }
        void authorize()throws Exception{
            for(int i=0;i<3;i++){
                var permits=new JsonArray();for(int j=0;j<3;j++)if(i!=j)permits.add(pair(i,j));clients.get(i).permit(permits,now);
                for(int j=0;j<3;j++)if(i!=j){
                    var grant=new JsonObject();grant.addProperty("id",pair(i,j));grant.addProperty("key","0".repeat(64));grant.addProperty("uuid",ids.get(j).toString());grant.addProperty("name","Member"+j);
                    grant.addProperty("epoch",101+j);grant.addProperty("scope",1);grant.addProperty("selfEpoch",101+i);grant.addProperty("selfScope",1);grant.addProperty("world",WORLD);
                    grant.addProperty("serverTime",10000);grant.addProperty("expiresAt",20000);grant.addProperty("relayRttMs",120);
                    var candidates=new JsonArray();var address=new JsonObject();address.addProperty("host","127.0.0.1");address.addProperty("port",30001+j);candidates.add(address);grant.add("candidates",candidates);
                    clients.get(i).offer(grant,now);
                }
            }
        }
        void step()throws Exception{
            now+=20;if(now%1000==0)authorize();
            while(!wire.isEmpty()&&wire.peek().due<=now){var d=wire.remove();clients.get(d.to).receive(d.bytes,new InetSocketAddress("127.0.0.1",30001+d.from),now);}
            for(var c:clients)c.tick(now,180);
            if(now%200==0){sequence++;for(var c:clients){
                var state=new JsonObject();state.addProperty("type","state");state.addProperty("channel","position");state.addProperty("scope",1);state.addProperty("world",WORLD);state.addProperty("sequence",sequence);state.addProperty("sampleTime",now);
                state.add("value",new JsonObject());c.publish(state,now);
            }}
        }
        String report(){return String.join("\n",clients.getFirst().diagnostics(now,180));}
    }
    @Test void twoSimultaneousPeersStayDirectAcrossShortProbeLossBursts()throws Exception{
        var mesh=new Mesh();for(int i=0;i<6000;i+=20)mesh.step();
        for(int i=0;i<60000;i+=20){mesh.step();for(var c:mesh.clients)assertEquals(2,c.direct().size(),mesh.report());}
    }
    @Test void oneFailedPeerDoesNotResetTheOtherAndRecoveryRequiresFreshState()throws Exception{
        var mesh=new Mesh();for(int i=0;i<6000;i+=20)mesh.step();mesh.interrupt=true;
        for(int i=0;i<4000;i+=20){mesh.step();assertTrue(mesh.clients.getFirst().direct().contains(mesh.ids.get(1)),mesh.report());}
        assertEquals(Set.of(mesh.ids.get(1)),mesh.clients.getFirst().direct());
        mesh.interrupt=false;for(int i=0;i<8000;i+=20)mesh.step();
        for(var c:mesh.clients)assertEquals(2,c.direct().size(),mesh.report());
    }
}
