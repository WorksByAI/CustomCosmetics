// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;
import com.customcosmetics.features.party.PartyRoster;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PartyPeerClientTest {
    @Test void skinsOnlyTravelAfterAuthenticatedDirectSelectionAndStopWhenItFails()throws Exception{
        var arena=new Arena(0);byte[] bytes=new byte[7000];new Random(765).nextBytes(bytes);var document=new com.customcosmetics.features.skins.SkinTransfer.Document(bytes,true);var received=new ArrayList<com.customcosmetics.features.skins.SkinTransfer.Document>();
        arena.a.skins(new com.customcosmetics.features.skins.SkinTransfer.Source(){public com.customcosmetics.features.skins.SkinTransfer.Document local(){return document;}public boolean receiving(){return false;}public void received(UUID id,com.customcosmetics.features.skins.SkinTransfer.Document skin){}});
        arena.b.skins(new com.customcosmetics.features.skins.SkinTransfer.Source(){public com.customcosmetics.features.skins.SkinTransfer.Document local(){return null;}public boolean receiving(){return true;}public void received(UUID id,com.customcosmetics.features.skins.SkinTransfer.Document skin){assertEquals(A,id);if(skin!=null)received.add(skin);}});
        arena.run(500,false);assertTrue(received.isEmpty());assertTrue(arena.sent.stream().noneMatch(p->p.bytes[4]==1));
        for(int i=0;i<210;i++){if(i%100==0){arena.a.permit(ids(),arena.now);arena.b.permit(ids(),arena.now);}arena.step(true);}assertEquals(Set.of(B),arena.a.direct());assertFalse(received.isEmpty());assertArrayEquals(bytes,received.getLast().bytes());assertTrue(received.getLast().slim());assertTrue(arena.sent.stream().allMatch(p->p.bytes.length<=1200));
        arena.run(3200,false);assertTrue(arena.a.direct().isEmpty());int count=received.size();arena.run(500,false);assertEquals(count,received.size());
    }
    @Test void appearanceOnlyPeersConnectWithoutPublishingOrGrantingPositionsAndRevokeImmediately()throws Exception{
        var arena=new Arena(60000);arena.states=false;
        var audience=com.customcosmetics.features.party.PositionAudience.PARTY;
        arena.a.context(new PartyPeerClient.Context(A,101,1,WORLD,PartyRoster.EMPTY,true,audience,false,Set.of(B)));
        arena.b.context(new PartyPeerClient.Context(B,102,1,WORLD,PartyRoster.EMPTY,true,audience,false,Set.of(A)));
        arena.a.permit(ids(),arena.now);arena.b.permit(ids(),arena.now+arena.offset);
        var ga=offer(true);ga.addProperty("appearance",true);ga.addProperty("sendPosition",false);ga.addProperty("receivePosition",false);
        var gb=offer(false);gb.addProperty("appearance",true);gb.addProperty("sendPosition",false);gb.addProperty("receivePosition",false);
        arena.a.offer(ga,arena.now);arena.b.offer(gb,arena.now+arena.offset);
        var features=new JsonObject();features.addProperty("creatureModel","sheep");arena.a.appearance(AppearanceTransfer.encode(features));
        arena.run(1800,true);
        assertEquals(Set.of(B),arena.a.connected());assertEquals(Set.of(A),arena.b.connected());
        assertTrue(arena.a.direct().isEmpty());assertTrue(arena.b.direct().isEmpty(),"Appearance readiness must not suppress relay positions");
        assertEquals("sheep",arena.b.appearances().get(A).user().creatureModel());assertTrue(arena.ar.isEmpty());assertTrue(arena.br.isEmpty());
        assertTrue(arena.sent.stream().allMatch(packet->packet.bytes.length<=1200));
        arena.run(1800,false);assertTrue(arena.b.appearances().isEmpty(),"Broken authenticated path must restore HTTP fallback");
        arena.b.context(new PartyPeerClient.Context(B,102,1,WORLD,PartyRoster.EMPTY,true,audience,false,Set.of()));assertTrue(arena.b.appearances().isEmpty());assertEquals(0,arena.b.size());
    }
    @Test void aLegacyGrantCannotAuthorizeAppearanceWithoutPositionConsent()throws Exception{
        var arena=new Arena(0);arena.a.context(new PartyPeerClient.Context(A,101,1,WORLD,PartyRoster.EMPTY,true,com.customcosmetics.features.party.PositionAudience.PARTY,false,Set.of(B)));
        arena.a.permit(ids(),arena.now);var grant=offer(true);grant.addProperty("sendPosition",false);grant.addProperty("receivePosition",false);arena.a.offer(grant,arena.now);assertEquals(0,arena.a.size());assertTrue(arena.a.appearances().isEmpty());
    }
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
        final InetSocketAddress aa=new InetSocketAddress("127.0.0.1",30001),ba;
        final ArrayDeque<Packet> wire=new ArrayDeque<>();final List<JsonObject> ar=new ArrayList<>(),br=new ArrayList<>();final List<Packet> sent=new ArrayList<>();
        final PartyPeerClient a=new PartyPeerClient((bytes,to)->wire.add(new Packet(true,bytes,to)),ar::add),b=new PartyPeerClient((bytes,to)->wire.add(new Packet(false,bytes,to)),br::add);
        boolean states=true;
        long now=10000,seq;final long offset;
        Arena(long offset)throws Exception {this(offset,false);}
        Arena(long offset,boolean destinationDependentMapping)throws Exception {this.offset=offset;ba=new InetSocketAddress("127.0.0.1",destinationDependentMapping?40002:30002);a.context(new PartyPeerClient.Context(A,101,1,WORLD,ROSTER,true));b.context(new PartyPeerClient.Context(B,102,1,WORLD,ROSTER,true));
            a.permit(ids(),now);b.permit(ids(),now+offset);a.offer(offer(true),now);b.offer(offer(false),now+offset);}
        void step(boolean deliver){now+=20;a.tick(now,20);b.tick(now+offset,20);if(states){a.publish(position(++seq,now),now);b.publish(position(seq,now+offset),now+offset);}
            int count=0;while(!wire.isEmpty()){var p=wire.remove();sent.add(p);assertTrue(++count<256,"Probe reply loop");
                if(deliver){if(p.fromA&&p.destination.equals(ba))b.receive(p.bytes,aa,now+offset);else if(!p.fromA&&p.destination.equals(aa))a.receive(p.bytes,ba,now);}}
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
    @Test void authenticatedInboundProbeTeachesTheDestinationDependentPortAndEstablishesBothDirections()throws Exception{
        var arena=new Arena(60000,true);arena.run(1800,true);
        assertEquals(Set.of(B),arena.a.direct(),"The publicly reachable peer must probe the authenticated source port, not only the relay-observed port");
        assertEquals(Set.of(A),arena.b.direct());assertFalse(arena.ar.isEmpty());assertFalse(arena.br.isEmpty());
        assertTrue(arena.sent.stream().anyMatch(p->p.fromA&&p.bytes[4]==2&&p.destination.equals(arena.ba)));
        arena.a.offer(offer(true),arena.now);arena.b.offer(offer(false),arena.now+arena.offset);arena.run(1000,true);
        assertEquals(Set.of(B),arena.a.direct(),"Refreshing the relay candidate must preserve the learned endpoint");
    }
    @Test void tamperedOrReplayedProbesCannotTeachAnotherEndpoint()throws Exception{
        var arena=new Arena(0,true);arena.step(false);
        var packet=arena.sent.stream().filter(p->!p.fromA&&p.bytes[4]==2).findFirst().orElseThrow();
        var tampered=packet.bytes.clone();tampered[tampered.length-1]^=1;
        var unrelated=new InetSocketAddress("127.0.0.2",45000);var spoofed=new InetSocketAddress("127.0.0.1",45000);
        arena.a.receive(tampered,spoofed,arena.now);arena.a.receive(packet.bytes,unrelated,arena.now);
        assertTrue(String.join("\n",arena.a.diagnostics(arena.now,20)).contains("learned endpoint=false"));
        arena.a.receive(packet.bytes,arena.ba,arena.now);arena.a.receive(packet.bytes,spoofed,arena.now);
        assertTrue(arena.a.direct().isEmpty(),"An inbound probe alone cannot suppress the relay");
        arena.sent.clear();arena.run(300,false);
        assertTrue(arena.sent.stream().anyMatch(p->p.fromA&&p.destination.equals(arena.ba)));
        assertFalse(arena.sent.stream().anyMatch(p->p.fromA&&p.destination.equals(spoofed)));
    }
    @Test void realUdpEndpointsExchangePositionsAndSkinWhenTheRelayAdvertisesAnotherPort()throws Exception{
        try(var as=new DatagramSocket(new InetSocketAddress("127.0.0.1",0));var bs=new DatagramSocket(new InetSocketAddress("127.0.0.1",0));var relayMapping=new DatagramSocket(new InetSocketAddress("127.0.0.1",0))){
            as.setSoTimeout(1);bs.setSoTimeout(1);var ar=new ArrayList<JsonObject>();var br=new ArrayList<JsonObject>();
            try(var a=new PartyPeerClient((bytes,to)->as.send(new DatagramPacket(bytes,bytes.length,to)),ar::add);var b=new PartyPeerClient((bytes,to)->bs.send(new DatagramPacket(bytes,bytes.length,to)),br::add)){
                var skin=new com.customcosmetics.features.skins.SkinTransfer.Document(new byte[4096],true);var received=new ArrayList<com.customcosmetics.features.skins.SkinTransfer.Document>();a.skins(skinSource(skin,null));b.skins(skinSource(null,received));
                a.context(new PartyPeerClient.Context(A,101,1,WORLD,ROSTER,true));b.context(new PartyPeerClient.Context(B,102,1,WORLD,ROSTER,true));
                var ao=offer(true);ao.getAsJsonArray("candidates").get(0).getAsJsonObject().addProperty("port",relayMapping.getLocalPort());
                var bo=offer(false);bo.getAsJsonArray("candidates").get(0).getAsJsonObject().addProperty("port",as.getLocalPort());
                a.permit(ids(),10000);b.permit(ids(),10000);a.offer(ao,10000);b.offer(bo,10000);
                long start=System.nanoTime(),sequence=0,nextState=0;
                while(System.nanoTime()-start<java.util.concurrent.TimeUnit.SECONDS.toNanos(3)&&(a.direct().isEmpty()||b.direct().isEmpty()||received.isEmpty())){
                    long now=10000+java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
                    a.tick(now,20);b.tick(now,20);if(now>=nextState){nextState=now+200;a.publish(position(++sequence,now),now);b.publish(position(sequence,now),now);}
                    drain(as,a,now);drain(bs,b,now);Thread.sleep(10);
                }
                assertEquals(Set.of(B),a.direct());assertEquals(Set.of(A),b.direct());assertFalse(ar.isEmpty());assertFalse(br.isEmpty());
                assertFalse(received.isEmpty(),a.diagnostics(12000,20)+" / "+b.diagnostics(12000,20));assertArrayEquals(skin.bytes(),received.getLast().bytes());
            }
        }
    }
    private static com.customcosmetics.features.skins.SkinTransfer.Source skinSource(com.customcosmetics.features.skins.SkinTransfer.Document document,List<com.customcosmetics.features.skins.SkinTransfer.Document> received){return new com.customcosmetics.features.skins.SkinTransfer.Source(){public com.customcosmetics.features.skins.SkinTransfer.Document local(){return document;}public boolean receiving(){return received!=null;}public void received(UUID id,com.customcosmetics.features.skins.SkinTransfer.Document skin){if(received!=null&&skin!=null)received.add(skin);}};}
    @Test void eightMbpsSkinBudgetTransfersLargeFilesWithoutStarvingPositionsOrProbes()throws Exception{
        assertEquals(1_000_000,PartyPeerClient.SKIN_BYTES_PER_SECOND);
        var arena=new Arena(0);byte[] content=new byte[3*1024*1024];new Random(456).nextBytes(content);
        var received=new ArrayList<com.customcosmetics.features.skins.SkinTransfer.Document>();
        arena.a.skins(skinSource(new com.customcosmetics.features.skins.SkinTransfer.Document(content,false),null));arena.b.skins(skinSource(null,received));
        // Warm the authenticated direct path, then measure a sustained busy interval.
        arena.run(1800,true);int first=arena.sent.size();long started=arena.now;long duration=2000;
        for(int i=0;i<duration;i+=20){if(i%1000==0){arena.a.permit(ids(),arena.now);arena.b.permit(ids(),arena.now);}arena.step(true);}
        var cipher=new UdpPacket(HexFormat.of().parseHex(ID),HexFormat.of().parseHex(KEY),false,4);long bytes=0;
        for(var packet:arena.sent.subList(first,arena.sent.size()))if(packet.fromA&&packet.bytes[4]==1){var message=JsonParser.parseString(new String(cipher.decode(packet.bytes).body(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();if(message.get("type").getAsString().equals("skin"))bytes+=packet.bytes.length+28;}
        assertTrue(bytes>1_000_000,"Transfer is still constrained by an old window, byte budget or packet limit: "+bytes);
        assertTrue(bytes<=PartyPeerClient.SKIN_BURST_BYTES+duration*PartyPeerClient.SKIN_BYTES_PER_SECOND/1000);
        for(int i=0;i<5000&&received.isEmpty();i+=20){if(i%1000==0){arena.a.permit(ids(),arena.now);arena.b.permit(ids(),arena.now);}arena.step(true);}
        assertFalse(received.isEmpty(),"Large skin did not finish: "+arena.b.diagnostics(arena.now,20));assertArrayEquals(content,received.getLast().bytes());
        assertFalse(arena.br.isEmpty());assertEquals(Set.of(B),arena.a.direct());assertTrue(arena.sent.stream().allMatch(p->p.bytes.length<=1200));
        assertTrue(arena.br.getLast().get("sampleTime").getAsLong()>=started+duration-40,"Skin traffic starved position updates");
    }
    private static void drain(DatagramSocket socket,PartyPeerClient peer,long now)throws Exception{
        for(int i=0;i<64;i++)try{var packet=new DatagramPacket(new byte[1200],1200);socket.receive(packet);
            assertTrue(peer.receive(Arrays.copyOf(packet.getData(),packet.getLength()),(InetSocketAddress)packet.getSocketAddress(),now));
        }catch(SocketTimeoutException done){return;}
    }
    @Test void prolongedFailureBacksOffAndManualReauthorizationRestartsFastProbing()throws Exception{
        var arena=new Arena(0);
        for(int i=0;i<40000;i+=20){if(i%1000==0){arena.a.permit(ids(),arena.now);arena.b.permit(ids(),arena.now);arena.a.offer(offer(true),arena.now);arena.b.offer(offer(false),arena.now);}arena.step(false);}
        assertTrue(String.join("\n",arena.a.diagnostics(arena.now,20)).contains("Probe interval=3000ms"));
        long before=arena.sent.stream().filter(p->p.fromA).count();arena.run(1000,false);
        assertTrue(arena.sent.stream().filter(p->p.fromA).count()-before<=1);
        arena.a.context(new PartyPeerClient.Context(A,101,2,WORLD,ROSTER,false));
        arena.a.context(new PartyPeerClient.Context(A,101,1,WORLD,ROSTER,true));arena.a.permit(ids(),arena.now);arena.a.offer(offer(true),arena.now);
        assertTrue(String.join("\n",arena.a.diagnostics(arena.now,20)).contains("Probe interval=250ms"));
    }
    @Test void oneWayPositionPermissionsAreEnforcedEvenThoughBothPeersHaveThePairKey()throws Exception{
        var arena=new Arena(0);arena.a.close();arena.b.close();
        var publicAudience=new com.customcosmetics.features.party.PositionAudience("public",Set.of(),Set.of(),false);
        var receiveAudience=new com.customcosmetics.features.party.PositionAudience("party",Set.of(),Set.of(),true);
        arena.a.context(new PartyPeerClient.Context(A,101,1,WORLD,PartyRoster.EMPTY,true,publicAudience,false));
        arena.b.context(new PartyPeerClient.Context(B,102,1,WORLD,PartyRoster.EMPTY,true,receiveAudience,false));
        arena.a.permit(ids(),arena.now);arena.b.permit(ids(),arena.now);
        var outgoing=offer(true);outgoing.addProperty("sendPosition",true);outgoing.addProperty("receivePosition",false);
        var incoming=offer(false);incoming.addProperty("sendPosition",false);incoming.addProperty("receivePosition",true);
        arena.a.offer(outgoing,arena.now);arena.b.offer(incoming,arena.now);arena.run(1800,true);
        assertTrue(arena.ar.isEmpty());assertFalse(arena.br.isEmpty());assertTrue(arena.b.direct().contains(A));assertTrue(arena.a.sendingDirect());
        arena.a.context(new PartyPeerClient.Context(A,101,2,WORLD,PartyRoster.EMPTY,true,com.customcosmetics.features.party.PositionAudience.PARTY,false));
        assertEquals(0,arena.a.size());assertTrue(arena.a.direct().isEmpty());
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
