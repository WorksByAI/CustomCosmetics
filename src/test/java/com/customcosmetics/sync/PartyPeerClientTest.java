// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;
import com.customcosmetics.features.party.PartyRoster;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PartyPeerClientTest {
    private static final UUID A=UUID.fromString("12345678-1234-4234-8234-123456789abc"),B=UUID.fromString("82345678-1234-4234-8234-123456789abc");
    private static final String ID="1".repeat(32),WORLD="a".repeat(64),KEY="2".repeat(64);
    private static final PartyRoster ROSTER=new PartyRoster(Set.of(A,B),Set.of(),"fixture");
    private static JsonArray ids(){var ids=new JsonArray();ids.add(ID);return ids;}
    private static JsonObject offer(boolean a){var m=new JsonObject();m.addProperty("id",ID);m.addProperty("key",KEY);m.addProperty("selfEpoch",a?101:102);m.addProperty("selfScope",1);
        m.addProperty("uuid",(a?B:A).toString());m.addProperty("name",a?"Bob":"Alice");m.addProperty("epoch",a?102:101);m.addProperty("scope",1);m.addProperty("world",WORLD);
        m.addProperty("expiresAt",20000);m.addProperty("serverTime",10000);m.addProperty("relayRttMs",20);
        var candidates=new JsonArray();var c=new JsonObject();c.addProperty("host","127.0.0.1");c.addProperty("port",a?30002:30001);candidates.add(c);m.add("candidates",candidates);return m;}
    private static JsonObject position(long seq,long clock){var m=new JsonObject();m.addProperty("type","state");m.addProperty("channel","position");m.addProperty("scope",1);m.addProperty("world",WORLD);m.addProperty("sequence",seq);m.addProperty("sampleTime",clock);
        var p=new JsonObject();p.addProperty("x",1000+seq);p.addProperty("y",80);p.addProperty("z",10000);p.addProperty("width",.6);p.addProperty("height",1.8);m.add("value",p);return m;}
    private record Packet(boolean fromA,byte[] bytes,InetSocketAddress destination){}
    private static final class Arena {
        final InetSocketAddress aa=new InetSocketAddress("127.0.0.1",30001),ba=new InetSocketAddress("127.0.0.1",30002);
        final ArrayDeque<Packet> wire=new ArrayDeque<>();final List<JsonObject> ar=new ArrayList<>(),br=new ArrayList<>();final List<Packet> sent=new ArrayList<>();
        final PartyPeerClient a=new PartyPeerClient((bytes,to)->wire.add(new Packet(true,bytes,to)),ar::add),b=new PartyPeerClient((bytes,to)->wire.add(new Packet(false,bytes,to)),br::add);
        long now=10000,seq;final long offset;
        Arena(long offset)throws Exception {this.offset=offset;a.context(new PartyPeerClient.Context(A,101,1,WORLD,ROSTER,true));b.context(new PartyPeerClient.Context(B,102,1,WORLD,ROSTER,true));
            a.permit(ids(),now);b.permit(ids(),now+offset);a.offer(offer(true),now);b.offer(offer(false),now+offset);}
        void step(boolean deliver){now+=20;a.tick(now,20);b.tick(now+offset,20);a.publish(position(++seq,now),now);b.publish(position(seq,now+offset),now+offset);
            int count=0;while(!wire.isEmpty()){var p=wire.remove();sent.add(p);assertTrue(++count<100,"Probe reply loop");
                if(deliver){if(p.fromA)b.receive(p.bytes,aa,now+offset);else a.receive(p.bytes,ba,now);}}
        }
        void run(int milliseconds,boolean deliver){for(int i=0;i<milliseconds;i+=20)step(deliver);}
    }
    @Test void encryptedPeersPunchBothWaysAndShareSenderTimelineDespiteDifferentClockOrigins()throws Exception {
        var arena=new Arena(60000);arena.run(1800,true);
        assertEquals(Set.of(B),arena.a.direct());assertEquals(Set.of(A),arena.b.direct());assertFalse(arena.ar.isEmpty());assertFalse(arena.br.isEmpty());
        var record=arena.ar.getLast();assertEquals(B.toString(),record.get("uuid").getAsString());assertEquals(102,record.get("epoch").getAsLong());
        assertTrue(record.get("sampleTime").getAsLong()>60000);assertTrue(record.get("expiresAt").getAsLong()<=3000);
        for(var packet:arena.sent)assertEquals('4',packet.bytes[3]);
    }
    @Test void lostDirectPathReturnsToRelayAndSwitchingOffDestroysAllPairState()throws Exception {
        var arena=new Arena(0);arena.run(1800,true);assertFalse(arena.a.direct().isEmpty());arena.run(1600,false);
        assertTrue(arena.a.direct().isEmpty());assertTrue(arena.b.direct().isEmpty());
        assertFalse(arena.a.sendingDirect());assertFalse(arena.b.sendingDirect());
        arena.a.context(new PartyPeerClient.Context(A,101,2,WORLD,ROSTER,false));assertEquals(0,arena.a.size());
        arena.a.offer(offer(true),arena.now);assertEquals(0,arena.a.size());
    }
    @Test void removingAuthorityOrChangingWorldImmediatelyInvalidatesOffers()throws Exception {
        var arena=new Arena(0);arena.run(1000,true);arena.a.permit(new JsonArray(),arena.now);arena.a.tick(arena.now,20);assertEquals(0,arena.a.size());assertTrue(arena.a.direct().isEmpty());
        arena.a.context(new PartyPeerClient.Context(A,101,2,"b".repeat(64),ROSTER,true));arena.a.permit(ids(),arena.now);arena.a.offer(offer(true),arena.now);assertEquals(0,arena.a.size());
    }
    @Test void expiredAuthorityCannotBeRenewedByReplayingAnOldEncryptedOffer()throws Exception {
        var arena=new Arena(0);arena.run(4200,true);assertEquals(0,arena.a.size());arena.a.offer(offer(true),arena.now);assertEquals(0,arena.a.size());
    }
    @Test void replayedTamperedOrUnrelatedAddressDatagramsDoNotProduceAnotherRecord()throws Exception {
        var arena=new Arena(0);arena.run(1200,true);var packet=arena.sent.stream().filter(p->p.fromA&&p.bytes[4]==1).findFirst().orElseThrow();
        int size=arena.br.size();arena.b.receive(packet.bytes,arena.aa,arena.now);assertEquals(size,arena.br.size());
        var changed=packet.bytes.clone();changed[changed.length-1]^=1;arena.b.receive(changed,arena.aa,arena.now);assertEquals(size,arena.br.size());
        arena.b.receive(packet.bytes,new InetSocketAddress("127.0.0.2",30001),arena.now);assertEquals(size,arena.br.size());
    }
    @Test void refreshingTheSamePairDoesNotReuseEncryptionCounters()throws Exception {
        var arena=new Arena(0);arena.run(1000,true);long highest=arena.sent.stream().filter(p->p.fromA&&p.bytes[4]==1).mapToLong(p->java.nio.ByteBuffer.wrap(p.bytes,21,8).getLong()).max().orElseThrow();
        arena.a.offer(offer(true),arena.now);arena.sent.clear();arena.run(500,true);
        assertTrue(arena.sent.stream().filter(p->p.fromA&&p.bytes[4]==1).allMatch(p->java.nio.ByteBuffer.wrap(p.bytes,21,8).getLong()>highest));
    }
    @Test void droppingAndReauthorizingTheSamePairNeverRestartsNoncesOrAcceptsOldPackets()throws Exception {
        var arena=new Arena(0);arena.run(1000,true);
        long highest=arena.sent.stream().filter(p->p.fromA&&p.bytes[4]==2).mapToLong(p->java.nio.ByteBuffer.wrap(p.bytes,21,8).getLong()).max().orElseThrow();
        arena.a.close();arena.a.context(new PartyPeerClient.Context(A,101,1,WORLD,ROSTER,true));arena.a.permit(ids(),arena.now);arena.a.offer(offer(true),arena.now);
        arena.sent.clear();arena.run(300,true);
        assertTrue(arena.sent.stream().filter(p->p.fromA&&p.bytes[4]==2).allMatch(p->java.nio.ByteBuffer.wrap(p.bytes,21,8).getLong()>highest));
    }
    @Test void reportDistinguishesMissingAuthorizationBlockedUdpAndSuccessfulDirectWithoutSecrets()throws Exception{
        var arena=new Arena(0);arena.run(1500,false);
        String report=String.join("\n",arena.a.diagnostics(arena.now,20));
        assertTrue(report.contains("No authenticated probe reply"));assertTrue(report.contains("states=0"));
        assertFalse(report.contains(ID));assertFalse(report.contains(KEY));assertFalse(report.contains("127.0.0.1"));
        arena.run(6500,true);arena.a.permit(ids(),arena.now);arena.b.permit(ids(),arena.now);arena.a.offer(offer(true),arena.now);arena.b.offer(offer(false),arena.now);
        arena.run(1800,true);assertTrue(String.join("\n",arena.a.diagnostics(arena.now,20)).contains("P2P selected"));
        arena.a.permit(new JsonArray(),arena.now);arena.a.tick(arena.now,20);
        assertTrue(String.join("\n",arena.a.diagnostics(arena.now,20)).contains("No peer offer"));
    }
    @Test void invalidWorldAndSessionOffersExplainTheRejection()throws Exception{
        var arena=new Arena(0);arena.a.close();arena.a.context(new PartyPeerClient.Context(A,101,1,WORLD,ROSTER,true));arena.a.permit(ids(),arena.now);
        var otherWorld=offer(true);otherWorld.addProperty("world","b".repeat(64));arena.a.offer(otherWorld,arena.now);
        assertTrue(String.join("\n",arena.a.diagnostics(arena.now,20)).contains("Peer world does not match"));
        var stale=offer(true);stale.addProperty("selfScope",2);arena.a.offer(stale,arena.now);
        assertTrue(String.join("\n",arena.a.diagnostics(arena.now,20)).contains("another local session or scope"));
    }
    @Test @SuppressWarnings("unchecked") void recoveredCandidateReceivesShadowStateWhenTheOldFastPathHasStopped()throws Exception{
        var arena=new Arena(0);arena.run(1000,true);arena.wire.clear();arena.now+=1300;
        var peersField=PartyPeerClient.class.getDeclaredField("peers");peersField.setAccessible(true);var peer=((Map<?,?>)peersField.get(arena.a)).values().iterator().next();
        var pathsField=peer.getClass().getDeclaredField("paths");pathsField.setAccessible(true);var paths=(Map<InetSocketAddress,PeerPath>)pathsField.get(peer);
        var recovered=new InetSocketAddress("127.0.0.1",30003);var path=new PeerPath();for(int i=0;i<3;i++)path.pong(arena.now,arena.now-80,arena.now-40);paths.put(recovered,path);
        arena.a.tick(arena.now,20);arena.a.publish(position(999,arena.now),arena.now);
        assertEquals(recovered,arena.wire.stream().filter(packet->packet.bytes[4]==1).findFirst().orElseThrow().destination());
        assertTrue(arena.a.direct().isEmpty(),"A recovered probe alone must not suppress the relay before fresh direct state arrives");
    }
}
