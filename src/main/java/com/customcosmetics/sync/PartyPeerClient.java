// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.features.party.PartyRoster;
import com.customcosmetics.features.party.PositionAudience;
import com.google.gson.*;
import com.customcosmetics.features.skins.SkinTransfer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.IOException;

/** Ephemeral, pair-key-authenticated UDP hole punching; game sockets and login tokens are never used. */
public final class PartyPeerClient implements AutoCloseable {
    public interface Sender {void send(byte[] bytes,InetSocketAddress address)throws IOException;}
    public interface Receiver {void record(JsonObject record)throws Exception;}
    public record Context(UUID self,long epoch,long scope,String world,PartyRoster roster,boolean enabled,PositionAudience audience,boolean partyConsent,Set<UUID> appearancePlayers){
        public Context(UUID self,long epoch,long scope,String world,PartyRoster roster,boolean enabled,PositionAudience audience,boolean partyConsent){this(self,epoch,scope,world,roster,enabled,audience,partyConsent,Set.of());}
        public Context(UUID self,long epoch,long scope,String world,PartyRoster roster,boolean enabled){this(self,epoch,scope,world,roster,enabled,PositionAudience.PARTY,true);}
    }
    private record Pending(long sent,InetSocketAddress address){}
    private static final int MAX_PEERS=16;
    public static final int SKIN_BYTES_PER_SECOND=1_000_000,SKIN_BURST_BYTES=64*1024;
    private static final class Replay {
        long highest,bits;
        boolean accept(long sequence){if(sequence<=0)return false;if(sequence>highest){long gap=sequence-highest;bits=gap>=64?1:bits<<gap|1;highest=sequence;return true;}
            long gap=highest-sequence;if(gap>=64||(bits&(1L<<gap))!=0)return false;bits|=1L<<gap;return true;}
    }
    private static final class Peer {
        String id,key;UUID uuid;String name;long epoch,scope,deadline,nextProbe,nextShadow,lastData=Long.MIN_VALUE/2,window,remoteWantUntil,probingSince;double relayRtt=500;
        int count,packets;boolean want,remoteWant;UdpPacket cipher;Counters counters;
        boolean sendPosition,receivePosition,appearance;int appearanceVersion;
        long appearanceUntil,appearanceNow;PresenceSync.PeerAppearance appearanceState;AppearanceTransfer appearanceTransfer;
        long probesSent,probesReceived,pongsReceived,statesReceived,replays,invalidPackets,blockedSources,expiredStates,sendFailures,budgetDrops;
        List<InetSocketAddress> candidates=List.of();final Map<InetSocketAddress,PeerPath> paths=new HashMap<>();final Map<Long,Pending> pending=new HashMap<>();
        InetSocketAddress address,observedAddress;
        SkinTransfer skin;
    }
    private static final class Counters {long[] outgoing=new long[3];Replay[] replay={null,new Replay(),new Replay()};long ping;}
    private final Sender sender;private final Receiver receiver;private final Map<String,Peer> peers=new HashMap<>();
    // Forget keys/addresses on opt-out, but retain nonce/replay counters for this session.
    // Re-authorizing an unchanged pair ID must never restart AES-GCM nonces.
    private final Map<String,Counters> counters=new HashMap<>();
    private AppearanceTransfer.Document appearanceDocument;private Map<UUID,PresenceSync.PeerAppearance> appearanceSnapshot=Map.of();
    private Set<UUID> connected=Set.of();
    private Context context;private Set<String> permitted=Set.of();private Set<UUID> selected=Set.of();private boolean changed;
    private long permittedUntil;
    private double tokens=25000;private long budgetTime;
    private long offersReceived,offersAccepted,offersRejected;private String lastRejection="";
    private SkinTransfer.Source skinSource=SkinTransfer.Source.NONE;private double skinTokens=SKIN_BURST_BYTES;private long skinBudgetTime;private int skinRoundRobin;
    public PartyPeerClient(Sender sender,Receiver receiver){this.sender=sender;this.receiver=receiver;}
    public void skins(SkinTransfer.Source source){skinSource=Objects.requireNonNull(source);}
    public void context(Context next){
        if(Objects.equals(context,next))return;
        if(context!=null&&context.self().equals(next.self())&&context.epoch()==next.epoch()&&context.scope()==next.scope()&&context.world().equals(next.world())&&context.roster().equals(next.roster())&&context.enabled()==next.enabled()&&context.audience().equals(next.audience())&&context.partyConsent()==next.partyConsent()){
            context=next;
            peers.values().removeIf(peer->{if(!next.appearancePlayers().contains(peer.uuid)){peer.appearance=false;peer.appearanceState=null;}return !peer.appearance&&!peer.sendPosition&&!peer.receivePosition;});
            publishAppearances(Long.MIN_VALUE);return;
        }
        close();context=next;
    }
    public void restrictAppearancePlayers(Set<UUID> allowed){if(context!=null)context(new Context(context.self(),context.epoch(),context.scope(),context.world(),context.roster(),context.enabled(),context.audience(),context.partyConsent(),allowed));}
    public void appearance(AppearanceTransfer.Document document){appearanceDocument=document;}
    public Map<UUID,PresenceSync.PeerAppearance> appearances(){return appearanceSnapshot;}
    private void publishAppearances(long now){
        boolean same=true;int count=0;
        for(var peer:peers.values())if(peer.appearanceState!=null&&peer.appearance&&peer.appearanceUntil>now){count++;if(appearanceSnapshot.get(peer.uuid)!=peer.appearanceState)same=false;}
        if(same&&count==appearanceSnapshot.size())return;
        var values=new HashMap<UUID,PresenceSync.PeerAppearance>();for(var peer:peers.values())if(peer.appearanceState!=null&&peer.appearance&&peer.appearanceUntil>now)values.put(peer.uuid,peer.appearanceState);
        appearanceSnapshot=Map.copyOf(values);
    }
    public Set<UUID> direct(){return selected;}
    public Set<UUID> connected(){return connected;}
    public boolean sendingDirect(){return peers.values().stream().anyMatch(p->p.remoteWant);}
    public boolean changed(){boolean value=changed;changed=false;return value;}
    public int size(){return peers.size();}
    private void reject(String reason){offersRejected++;lastRejection=reason;}
    public List<String> diagnostics(long now,double relayRtt){
        var lines=new ArrayList<String>();
        lines.add("P2P: direct="+selected.size()+", offers="+peers.size()+", authorized="+(now<permittedUntil?permitted.size():0));
        lines.add("Appearance: connected="+connected.size()+", received="+appearanceSnapshot.size()+"; refresh=5000ms");
        lines.add("Offer history: received="+offersReceived+", accepted="+offersAccepted+", rejected="+offersRejected+(lastRejection.isEmpty()?"":"; last rejection: "+lastRejection));
        if(peers.isEmpty())lines.add("No peer offer. Remote consent, UDP verification, mutual party and same-world eligibility cannot be confirmed locally.");
        for(var p:peers.values().stream().sorted(Comparator.comparing(peer->peer.name)).toList()){
            double relay=(relayRtt>=0?relayRtt:500)+p.relayRtt;
            var best=p.paths.entrySet().stream().min(Comparator.comparingDouble(e->e.getValue().rtt<0?Double.MAX_VALUE:e.getValue().rtt)).orElse(null);
            var usable=p.paths.entrySet().stream().filter(e->e.getValue().viable(now,relay)).min(Comparator.comparingDouble(e->e.getValue().rtt)).orElse(null);
            var path=usable!=null?usable.getValue():best==null?null:best.getValue();
            String reason=selected.contains(p.uuid)?"P2P selected":p.pongsReceived==0?(p.probesReceived==0?"No authenticated probe reply; UDP reachability is unconfirmed":"Inbound probes arrive, but no replies to our probes; reachability may be asymmetric"):path==null?"No authenticated probe reply":switch(path.decision(now,relay)){
                case COOLDOWN->"Recovering after packet loss ("+Math.max(0,path.holdUntil-now)+"ms remaining)";
                case PROBING->"Waiting for probe replies ("+path.good+"/3)";
                case STALE->"Probe replies stopped";
                case LOSS->"Probe loss estimate too high";
                case SLOW->"Direct RTT exceeds quality limit";
                case READY->"Probes work, but no fresh direct state";
            };
            lines.add("Peer "+p.name+": "+reason+"; candidates="+p.candidates.size()+", learned endpoint="+(p.observedAddress!=null)+", remote receiving P2P="+p.remoteWant);
            lines.add("  Probes sent/replies/inbound="+p.probesSent+"/"+p.pongsReceived+"/"+p.probesReceived+", states="+p.statesReceived+", state age="+age(now,p.lastData));
            lines.add("  Probe interval="+probeInterval(p,now)+"ms; retry in="+Math.max(0,p.nextProbe-now)+"ms");
            lines.add("  Direct RTT="+ms(path==null?-1:path.rtt)+", estimated relay RTT="+ms(relay)+", P2P limit="+ms(PeerPath.rttLimit(relay))+", probe loss estimate="+(path==null?"unknown":Math.round(path.loss*100)+"%"));
            lines.add("  Drops: invalid="+p.invalidPackets+", replay="+p.replays+", source="+p.blockedSources+", expired="+p.expiredStates+", send errors="+p.sendFailures+", budget="+p.budgetDrops);
        }
        return List.copyOf(lines);
    }
    static String ms(double value){return value<0?"unknown":Math.round(value)+"ms";}
    static String age(long now,long received){return received==-1||received==Long.MIN_VALUE/2?"never":Math.max(0,now-received)+"ms";}
    public void permit(JsonArray ids,long now){
        if(ids==null||ids.size()>MAX_PEERS){permitted=Set.of();peers.clear();return;}
        var allowed=new HashSet<String>();for(var id:ids){String value=id.getAsString();if(value.matches("[a-f0-9]{32}"))allowed.add(value);}
        permitted=Set.copyOf(allowed);permittedUntil=now+4000;peers.keySet().retainAll(allowed);
    }
    public void offer(JsonObject grant,long now)throws Exception {
        offersReceived++;
        if(context==null||!context.enabled()||context.world().isEmpty()){reject("Local P2P context is disabled or world is unknown");return;}
        String id=grant.get("id").getAsString(),key=grant.get("key").getAsString();UUID uuid=UUID.fromString(grant.get("uuid").getAsString());
        String name=grant.get("name").getAsString();long epoch=grant.get("epoch").getAsLong(),scope=grant.get("scope").getAsLong();
        long remaining=grant.get("expiresAt").getAsLong()-grant.get("serverTime").getAsLong();
        if(now>=permittedUntil||!permitted.contains(id)){reject("No current server authorization for this pair");return;}
        if(!key.matches("[a-f0-9]{64}")||epoch<1||scope<1||!name.matches("[A-Za-z0-9_]{3,16}")){reject("Invalid authenticated offer fields");return;}
        boolean party=context.partyConsent()&&context.roster().contains(uuid,name);
        boolean sendPosition=(grant.has("sendPosition")?grant.get("sendPosition").getAsBoolean():party)&&context.audience().sendsTo(uuid,name,context.roster(),context.partyConsent());
        boolean receivePosition=(grant.has("receivePosition")?grant.get("receivePosition").getAsBoolean():party)&&context.audience().accepts(uuid,name,context.roster(),context.partyConsent());
        boolean appearance=grant.has("appearance")&&grant.get("appearance").getAsBoolean()&&context.appearancePlayers().contains(uuid);
        if(uuid.equals(context.self())||!sendPosition&&!receivePosition&&!appearance){reject("Peer has no permitted position sharing direction");return;}
        if(grant.get("selfEpoch").getAsLong()!=context.epoch()||grant.get("selfScope").getAsLong()!=context.scope()){reject("Offer belongs to another local session or scope");return;}
        if(!grant.get("world").getAsString().equals(context.world())){reject("Peer world does not match");return;}
        if(remaining<=0||remaining>10000){reject("Offer is expired or has an invalid lifetime");return;}
        var candidates=new ArrayList<InetSocketAddress>();var list=grant.getAsJsonArray("candidates");if(list.isEmpty()||list.size()>2){reject("Missing or invalid IPv4 candidates");return;}
        for(var value:list){var candidate=value.getAsJsonObject();String host=candidate.get("host").getAsString();int port=candidate.get("port").getAsInt();
            if(!host.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")||port<1||port>65535){reject("Invalid IPv4 candidate or port");return;}
            var address=InetAddress.getByName(host);if(address.isAnyLocalAddress()||address.isMulticastAddress()){reject("Unusable IPv4 candidate");return;}
            candidates.add(new InetSocketAddress(address,port));
        }
        Peer peer=peers.get(id);
        if(peer==null){if(peers.size()>=MAX_PEERS||!counters.containsKey(id)&&counters.size()>=4096){reject("Direct peer capacity reached");return;}peers.values().removeIf(p->p.uuid.equals(uuid));peer=new Peer();peer.id=id;peer.key=key;peer.uuid=uuid;peer.name=name;peer.epoch=epoch;peer.scope=scope;peer.probingSince=now;
            peer.counters=counters.computeIfAbsent(id,ignored->new Counters());
            peer.skin=new SkinTransfer(document->skinSource.received(uuid,document));
            Peer receivingPeer=peer;
            peer.appearanceTransfer=new AppearanceTransfer(features->{
                if(features==null){receivingPeer.appearanceState=null;receivingPeer.appearanceUntil=0;return;}
                var decoded=PresenceSync.decodePeerAppearance(uuid,name,features);
                if(receivingPeer.appearanceState==null||!decoded.signature().equals(receivingPeer.appearanceState.signature()))receivingPeer.appearanceState=decoded;
                receivingPeer.appearanceUntil=receivingPeer.appearanceNow+15_000;
            });
            peer.cipher=new UdpPacket(HexFormat.of().parseHex(id),HexFormat.of().parseHex(key),context.self().toString().compareTo(uuid.toString())<0,4);peers.put(id,peer);
        }else if(!peer.key.equals(key)||peer.epoch!=epoch||peer.scope!=scope||!peer.uuid.equals(uuid)){reject("Conflicting pair session");return;}
        peer.deadline=now+remaining;peer.candidates=List.copyOf(candidates);peer.relayRtt=Math.clamp(grant.get("relayRttMs").getAsDouble(),0,10000);
        peer.sendPosition=sendPosition;peer.receivePosition=receivePosition;peer.appearance=appearance;if(!appearance)peer.appearanceState=null;
        peer.paths.keySet().removeIf(a->!allowed(candidates,a));
        if(peer.observedAddress!=null&&!allowed(candidates,peer.observedAddress))peer.observedAddress=null;
        offersAccepted++;lastRejection="";
    }
    private static boolean allowed(List<InetSocketAddress> candidates,InetSocketAddress source){return candidates.stream().anyMatch(a->a.getAddress().equals(source.getAddress()));}
    private static long probeInterval(Peer peer,long now){
        long last=peer.probingSince;for(var path:peer.paths.values())last=Math.max(last,path.lastPong);
        long idle=Math.max(0,now-last);return idle<5000?250:idle<30000?1000:3000;
    }
    public void tick(long now,double relayRtt){
        if(now>=permittedUntil){permitted=Set.of();peers.clear();}
        peers.values().removeIf(p->p.deadline<=now);
        var next=new HashSet<UUID>();
        for(var p:peers.values()){
            if(p.remoteWant&&now>=p.remoteWantUntil){p.remoteWant=false;changed=true;}
            for(var iterator=p.pending.entrySet().iterator();iterator.hasNext();){var pending=iterator.next().getValue();var path=p.paths.computeIfAbsent(pending.address(),ignored->new PeerPath());if(now-pending.sent()>path.probeTimeout()){path.lost(now);iterator.remove();}}
            double relay=(relayRtt>=0?relayRtt:500)+p.relayRtt;
            var best=p.paths.entrySet().stream().filter(e->e.getValue().viable(now,relay)).min(Comparator.comparingDouble(e->e.getValue().rtt)).orElse(null);
            // Warm the recovered path before requiring fresh direct state to suppress the relay.
            if(best!=null)p.address=best.getKey();
            boolean appearanceReady=best!=null&&p.appearance&&p.appearanceVersion==1;
            if(appearanceReady)p.appearanceTransfer.tick(now,appearanceDocument,m->sendAppearance(p,m,now));
            else {p.appearanceTransfer.suspend();p.appearanceState=null;}

            p.want=best!=null&&now-p.lastData<=Math.max(1200,best.getValue().freshness());
            if(p.want)next.add(p.uuid);
            if(now>=p.nextProbe){p.nextProbe=now+probeInterval(p,now);
                var addresses=new LinkedHashSet<InetSocketAddress>();
                if(p.observedAddress!=null&&allowed(p.candidates,p.observedAddress))addresses.add(p.observedAddress);
                if(p.address!=null&&allowed(p.candidates,p.address))addresses.add(p.address);
                addresses.addAll(p.candidates);
                for(var address:addresses){if(p.pending.size()>=16)break;long ping=++p.counters.ping;var m=new JsonObject();m.addProperty("type","probe");m.addProperty("ping",ping);m.addProperty("want",p.want);m.addProperty("appearanceVersion",p.appearance?1:0);
                    if(send(p,2,m,address,now)){p.probesSent++;p.pending.put(ping,new Pending(now,address));}}
            }
        }
        var all=new HashSet<>(next);for(var peer:peers.values())if(peer.appearance&&peer.appearanceVersion==1&&appearancePath(peer,now))all.add(peer.uuid);
        if(!all.equals(connected))connected=Set.copyOf(all);
        var result=Set.copyOf(next);if(!result.equals(selected)){for(var p:peers.values())if(result.contains(p.uuid)!=selected.contains(p.uuid))p.nextProbe=0;selected=result;changed=true;}
        publishAppearances(now);
        if(skinSource!=SkinTransfer.Source.NONE&&!peers.isEmpty()){
            var local=skinSource.local();var ordered=new ArrayList<>(peers.values());int first=Math.floorMod(skinRoundRobin++,ordered.size());
            for(int i=0;i<ordered.size();i++){var p=ordered.get((first+i)%ordered.size());if((p.want&&p.remoteWant||p.appearance&&p.appearanceVersion==1&&appearancePath(p,now))&&p.address!=null)p.skin.tick(now,local,skinSource.receiving(p.uuid,p.name),m->sendSkin(p,m,now));else p.skin.suspend();}
        }
    }
    private boolean appearancePath(Peer peer,long now){var path=peer.address==null?null:peer.paths.get(peer.address);return path!=null&&path.good>=3&&now-path.lastPong<=path.freshness()&&path.viable(now,peer.relayRtt+500);}
    private boolean sendAppearance(Peer peer,JsonObject message,long now){
        message.addProperty("type","appearance");message.addProperty("scope",context.scope());message.addProperty("world",context.world());return send(peer,1,message,peer.address,now);
    }
    private boolean sendSkin(Peer peer,JsonObject message,long now){
        message.addProperty("type","skin");message.addProperty("scope",context.scope());message.addProperty("world",context.world());
        int size=message.toString().getBytes(StandardCharsets.UTF_8).length+UdpPacket.HEADER+16+28;
        skinTokens=Math.min(SKIN_BURST_BYTES,skinTokens+Math.max(0,now-skinBudgetTime)*(SKIN_BYTES_PER_SECOND/1000.0));skinBudgetTime=now;if(skinTokens<size)return false;
        if(send(peer,1,message,peer.address,now,true)){skinTokens-=size;return true;}return false;
    }
    public void publish(JsonObject state,long now){
        for(var p:peers.values()){
            if("position".equals(state.get("channel").getAsString())&&!p.sendPosition)continue;
            if(p.deadline<=now||p.address==null||!allowed(p.candidates,p.address))continue;
            if(!p.remoteWant&&now<p.nextShadow)continue;
            p.nextShadow=now+500;var m=state.deepCopy();m.addProperty("sentAt",now);send(p,1,m,p.address,now);
        }
    }
    private boolean send(Peer p,int flow,JsonObject m,InetSocketAddress address,long now){
        return send(p,flow,m,address,now,false);
    }
    private boolean send(Peer p,int flow,JsonObject m,InetSocketAddress address,long now,boolean skin){
        try{if(!skin){tokens=Math.min(25000,tokens+Math.max(0,now-budgetTime)*250);budgetTime=now;}
            byte[] data=p.cipher.encode(flow,++p.counters.outgoing[flow],m.toString().getBytes(StandardCharsets.UTF_8));if(!skin&&tokens<data.length+28){p.budgetDrops++;return false;}
            if(!skin)tokens-=data.length+28;sender.send(data,address);return true;
        }catch(Exception unavailable){p.sendFailures++;return false;}
    }
    public boolean receive(byte[] bytes,InetSocketAddress source,long now){
        if(bytes.length<45||bytes.length>1200||bytes[0]!='C'||bytes[1]!='C'||bytes[2]!='P'||bytes[3]!='4')return false;
        var p=peers.get(HexFormat.of().formatHex(bytes,5,21));if(p==null||p.deadline<=now)return true;if(!allowed(p.candidates,source)){p.blockedSources++;return true;}
        if(now-p.window>=1000){p.window=now;p.count=p.packets=0;}if(++p.packets>2048)return true;
        try{
            var packet=p.cipher.decode(bytes);if(!p.counters.replay[packet.flow()].accept(packet.sequence())){p.replays++;return true;}
            var m=JsonParser.parseString(new String(packet.body(),StandardCharsets.UTF_8)).getAsJsonObject();String type=m.get("type").getAsString();
            if(!type.equals("skin")&&++p.count>80)return true;
            if(packet.flow()==2){
                p.appearanceVersion=m.has("appearanceVersion")&&m.get("appearanceVersion").getAsInt()==1?1:0;
                long ping=m.get("ping").getAsLong();boolean want=m.get("want").getAsBoolean();if(ping<1)return true;
                if(type.equals("probe")){
                    p.probesReceived++;
                    // A symmetric NAT can use a different port for us than for the relay.
                    // Learn only a fresh authenticated source on an authorized IP, then
                    // prove its return path with our own probes before accepting state.
                    var observed=p.candidates.contains(source)?null:source;
                    if(!Objects.equals(p.observedAddress,observed)){p.observedAddress=observed;p.nextProbe=Math.min(p.nextProbe,now+250);}
                    var reply=new JsonObject();reply.addProperty("type","pong");reply.addProperty("ping",ping);reply.addProperty("clock",now);reply.addProperty("want",p.want);reply.addProperty("appearanceVersion",p.appearance?1:0);send(p,2,reply,source,now);
                }
                else if(type.equals("pong")){
                    var pending=p.pending.remove(ping);if(pending==null||!pending.address().getAddress().equals(source.getAddress()))return true;
                    var path=p.paths.computeIfAbsent(source,ignored->new PeerPath());path.pong(now,pending.sent(),m.get("clock").getAsLong());
                    p.pongsReceived++;
                    if(p.address==null||!p.paths.containsKey(p.address)||path.rtt<p.paths.get(p.address).rtt)p.address=source;
                }else return true;
                p.remoteWantUntil=now+1000;if(p.remoteWant!=want){p.remoteWant=want;changed=true;}return true;
            }
            if(type.equals("appearance")){
                if(!p.appearance||p.appearanceVersion!=1||m.get("scope").getAsLong()!=p.scope||!m.get("world").getAsString().equals(context.world())||!appearancePath(p,now))return true;
                var verified=p.paths.get(source);if(verified==null||verified.good<3||now-verified.lastPong>verified.freshness())return true;
                p.appearanceNow=now;p.appearanceTransfer.receive(m,now,appearanceDocument);publishAppearances(now);return true;
            }
            if(type.equals("skin")){
                if(skinSource==SkinTransfer.Source.NONE||!(selected.contains(p.uuid)&&p.remoteWant||p.appearance&&p.appearanceVersion==1&&appearancePath(p,now))||m.get("scope").getAsLong()!=p.scope||!m.get("world").getAsString().equals(context.world()))return true;
                var path=p.paths.get(source);if(path==null||path.good<3||now-path.lastPong>path.freshness())return true;
                p.skin.receive(m,now,skinSource.local(),skinSource.receiving(p.uuid,p.name));return true;
            }
            if(!type.equals("state")||m.get("scope").getAsLong()!=p.scope||!m.get("world").getAsString().equals(context.world()))return true;
            String channel=m.get("channel").getAsString();long ttl=channel.equals("position")?3000:channel.equals("blocking")?1250:0;
            if(channel.equals("position")&&!p.receivePosition)return true;
            if(ttl==0||m.get("sequence").getAsLong()<1)return true;
            var path=p.paths.get(source);if(path==null||path.rtt<0)return true;
            long age=(long)Math.max(0,now-(m.get("sentAt").getAsLong()-path.offset));if(age>=ttl){p.expiredStates++;return true;}
            p.lastData=now;p.statesReceived++;
            var record=m.deepCopy();record.addProperty("type","record");record.addProperty("uuid",p.uuid.toString());record.addProperty("name",p.name);record.addProperty("epoch",p.epoch);
            record.addProperty("actorScope",p.scope);record.addProperty("scope",context.scope());record.addProperty("serverTime",0);record.addProperty("expiresAt",ttl-age);
            receiver.record(record);return true;
        }catch(Exception malformed){p.invalidPackets++;return true;}
    }
    @Override public void close(){peers.clear();appearanceSnapshot=Map.of();connected=Set.of();permitted=Set.of();if(!selected.isEmpty())changed=true;selected=Set.of();context=null;}
}
