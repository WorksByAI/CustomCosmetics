// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.features.blocking.BlockHand;
import com.customcosmetics.features.party.PartyRoster;
import com.customcosmetics.features.party.PositionAudience;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Shared counters and world scope for primary UDP and home WSS; never touches game networking. */
public final class RealtimeClient implements WebSocket.Listener,AutoCloseable {
    interface DatagramFactory {DatagramSocket open()throws SocketException;}
    public record View(String world,Set<UUID> players,PartyRoster roster,int environment,boolean esp,boolean p2p,PositionAudience audience){
        public View(String world,Set<UUID> players,PartyRoster roster,int environment,boolean esp,boolean p2p){this(world,players,roster,environment,esp,p2p,PositionAudience.PARTY);}
        public View(String world,Set<UUID> players,PartyRoster roster,int environment,boolean esp){this(world,players,roster,environment,esp,false);}
    }
    private record Block(BlockHand hand,String itemId){}
    private record Stamp(long epoch,long sequence){}
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"CustomCosmetics-Realtime");t.setDaemon(true);return t;});
    private final HttpClient http=WebSocketPresenceClient.createHttpClient();
    private final RealtimeRoute route=new RealtimeRoute();private final boolean forceWss;
    private final UUID self;private final PartyPeerClient peers;private long epoch;private InetSocketAddress relayAddress;private JsonObject localCandidate;
    private final DatagramFactory datagrams;private final int requestedUdpPort;
    private String udpFailure="";
    private final Map<String,JsonObject> pendingStates=new LinkedHashMap<>();private long nextRelayPosition;
    private volatile boolean closed,ready,udpSelected;private volatile View desired=new View("",Set.of(),PartyRoster.EMPTY,0,false);
    private volatile Block block=new Block(BlockHand.NONE,"");private volatile PartyClient.Position position;
    private volatile Map<UUID,BlockingTransport.Pose> blocks=Map.of();private volatile Map<UUID,PartyClient.Teammate> teammates=Map.of();
    private final Map<String,Stamp> seen=new HashMap<>();private final StringBuilder fragments=new StringBuilder();
    private final RealtimeConnections connections=new RealtimeConnections();
    private final String clientId=UUID.randomUUID().toString();private String token;
    private WebSocket socket;private CompletableFuture<WebSocket> connecting,sending;private DatagramSocket udp;private UdpPacket cipher;
    private String grantId="",peerCookie="";private long[] outgoing={0,0,0},incoming={0,0,0};
    private View sent;private Block sentBlock;private long scope,acknowledged,rosterSequence,blockSequence,positionSequence;
    private long expires,started=clock(),nextView,nextBlock,nextPosition,nextProbe,lastWs=clock(),nextUdpProbe;private boolean lastRoute;private int stopCopies;
    private int udpPositionInterval=200,wssPositionInterval=500;
    private long udpProbesSent,udpReplies;private long lastUdpReply=-1;
    private String closeReason="Client disconnected";
    public RealtimeClient(String base,PresenceAuthClient.Session session,boolean forceWss){
        this(base,session,forceWss,com.customcosmetics.config.ConfigManager.getSyncP2pPort());
    }
    RealtimeClient(String base,PresenceAuthClient.Session session,boolean forceWss,int port){
        this(base,session,forceWss,()->new DatagramSocket(port),port);
    }
    RealtimeClient(String base,PresenceAuthClient.Session session,boolean forceWss,DatagramFactory datagrams){
        this(base,session,forceWss,datagrams,0);
    }
    private RealtimeClient(String base,PresenceAuthClient.Session session,boolean forceWss,DatagramFactory datagrams,int requestedUdpPort){
        if(requestedUdpPort<0||requestedUdpPort>65535)throw new IllegalArgumentException("Invalid local UDP port");
        if(session==null||!session.baseUrl().equals(base))throw new IllegalArgumentException("Realtime requires an authenticated service session");
        this.forceWss=forceWss;this.datagrams=datagrams;this.requestedUdpPort=requestedUdpPort;token=session.token();self=session.uuid();
        peers=new PartyPeerClient((bytes,address)->{if(udp==null)throw new java.io.IOException("UDP unavailable");udp.send(new DatagramPacket(bytes,bytes.length,address));},m->acceptRecord(m,0,RealtimeConnections.Path.P2P));
        peers.skins(com.customcosmetics.features.skins.LocalSkins.exchange());
        connecting=http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8)).subprotocols("customcosmetics.realtime.v1").buildAsync(endpoint(base),this);
        connecting.whenComplete((ws,error)->{if(error!=null)fail("WSS connection failed ("+error.getClass().getSimpleName()+")");else if(closed)ws.abort();});
        worker.scheduleWithFixedDelay(()->{try{pump();}catch(Exception invalid){fail("Realtime worker failed ("+invalid.getClass().getSimpleName()+")");}},0,10,TimeUnit.MILLISECONDS);
    }
    static URI endpoint(String base){String uri=WebSocketPresenceClient.endpoint(base).toString();return URI.create(uri.substring(0,uri.length()-"/actions/ws".length())+"/realtime/ws");}
    private static long clock(){return System.nanoTime()/1000000;}
    public synchronized void update(View view,BlockHand hand,String itemId,PartyClient.Position position){
        if(!sameScope(view,desired)){blocks=Map.of();teammates=Map.of();peers.close();connections.clear();}
        else if(!view.players().equals(desired.players())){var kept=new HashMap<>(blocks);kept.keySet().retainAll(view.players());blocks=Map.copyOf(kept);connections.retainBlocking(view.players());}
        desired=view;block=new Block(hand,hand==BlockHand.NONE?"":itemId);this.position=position;
    }
    static boolean sameScope(View a,View b){return a.world().equals(b.world())&&a.roster().equals(b.roster())&&a.environment()==b.environment()&&a.esp()==b.esp()&&a.p2p()==b.p2p()&&a.audience().equals(b.audience());}
    static int positionInterval(boolean udp,int recommendation){return Math.clamp(recommendation,udp?200:500,1000);}
    static int sampleInterval(boolean direct,boolean udp,int recommendation){return direct?200:positionInterval(udp,recommendation);}
    public synchronized void block(BlockHand hand,String itemId){block=new Block(hand,hand==BlockHand.NONE?"":itemId);}
    public boolean closed(){return closed;}
    public synchronized boolean ready(){return ready&&!closed&&desired.equals(sent)&&acknowledged==scope;}
    public synchronized String status(){return connections.status(ready(),peers.direct(),clock());}
    synchronized boolean usingUdpRelay(){return udpSelected;}
    public synchronized Set<UUID> directPeers(){return peers.direct();}
    public int requestedUdpPort(){return requestedUdpPort;}
    public synchronized List<String> diagnostics(){
        long now=clock();var lines=new ArrayList<String>();
        lines.add("Realtime: "+(closed?closeReason:ready()?"Connected":"Connecting; waiting for authentication or view acknowledgement"));
        lines.add("Relay: "+(udpSelected?"UDP":"TCP/WSS")+"; UDP RTT="+PartyPeerClient.ms(route.udpMs())+"; WSS RTT="+PartyPeerClient.ms(route.wssMs()));
        lines.add("UDP socket="+(udp!=null&&!udp.isClosed()?"open":"unavailable")+"; probes sent/replies="+udpProbesSent+"/"+udpReplies+"; last reply="+PartyPeerClient.age(now,lastUdpReply)+"; return-path cookie="+(!peerCookie.isEmpty()?"received (server verification not yet confirmed)":"not received"));
        lines.add("Local UDP port="+(udp!=null&&!udp.isClosed()?udp.getLocalPort():"unbound")+"; configured="+(requestedUdpPort==0?"automatic":requestedUdpPort));
        if(!udpFailure.isEmpty())lines.add(udpFailure+"; using WSS fallback.");
        lines.add("World="+(desired.world().isEmpty()?"unknown":desired.world().substring(0,Math.min(12,desired.world().length())))+"; environment="+desired.environment()+"; view acknowledged="+ready());
        lines.add("Position audience="+desired.audience().mode()+"; selected recipients="+(desired.audience().members().size()+desired.audience().names().size())+"; receive shared positions="+desired.audience().receive());
        if(forceWss)lines.add("P2P: Disabled by forced WSS transport.");
        else if(!desired.p2p())lines.add("P2P: Disabled by local setting or missing party position sharing.");
        else if(desired.world().isEmpty())lines.add("P2P: Waiting for a valid world from Hypixel Mod API.");
        lines.addAll(peers.diagnostics(now,udpSelected?route.udpMs():route.wssMs()));
        return List.copyOf(lines);
    }
    private synchronized void fail(String reason){if(!closed){closeReason=reason;close();}}
    public Map<UUID,BlockingTransport.Pose> blocks(){return blocks;}
    public Map<UUID,PartyClient.Teammate> teammates(){return teammates;}
    private void send(JsonObject m){sending=socket.sendText(m.toString(),true);}
    private void datagram(int flow,JsonObject m)throws Exception{if(udp!=null){byte[] data=cipher.encode(flow,++outgoing[flow],m.toString().getBytes(StandardCharsets.UTF_8));udp.send(new DatagramPacket(data,data.length,relayAddress));}}
    private void direct(JsonObject m){var ids=new JsonArray();peers.direct().stream().sorted().forEach(id->ids.add(id.toString()));m.add("direct",ids);}
    private void state(String channel,long sequence,JsonObject value)throws Exception{
        var m=new JsonObject();m.addProperty("type","state");m.addProperty("channel",channel);m.addProperty("sequence",sequence);m.addProperty("scope",scope);m.addProperty("world",sent.world());m.add("value",value);
        long now=clock();m.addProperty("sampleTime",now);peers.publish(m,now);
        if(channel.equals("position")){if(now<nextRelayPosition)return;nextRelayPosition=now+positionInterval(udpSelected,udpSelected?udpPositionInterval:wssPositionInterval);}
        if(udpSelected)datagram(1,m);else pendingStates.put(channel,m);
    }
    private synchronized void pump()throws Exception{
        if(closed||socket==null)return;
        long now=clock();blocks=unexpiredBlocks(blocks,now);teammates=unexpiredPositions(teammates,System.currentTimeMillis());
        if(!ready){if(now-started>10000)fail("WSS authentication timed out");return;}
        if(System.currentTimeMillis()>=expires){fail("Realtime authentication lease expired");return;}
        if(now-lastWs>10000){fail("WSS control replies timed out");return;}
        boolean selected=!forceWss&&udp!=null&&route.select(now);udpSelected=selected;
        if(selected!=lastRoute){lastRoute=selected;pendingStates.clear();sentBlock=null;nextBlock=nextPosition=nextRelayPosition=nextProbe=nextUdpProbe=0;}
        if(ready())peers.context(new PartyPeerClient.Context(self,epoch,scope,desired.world(),desired.roster(),desired.p2p()&&!forceWss&&udp!=null,desired.audience(),desired.esp()));
        peers.tick(now,selected?route.udpMs():route.wssMs());
        if(peers.changed())nextPosition=nextRelayPosition=nextUdpProbe=nextProbe=0;
        if(udp!=null&&now>=nextUdpProbe){nextUdpProbe=now+1000;var m=new JsonObject();m.addProperty("type","probe");m.addProperty("ping",now);m.addProperty("active",selected);
            m.addProperty("p2p",ready()&&desired.p2p()&&!forceWss);m.addProperty("cookie",peerCookie);m.addProperty("relayRttMs",selected?route.udpMs():route.wssMs());if(localCandidate!=null)m.add("local",localCandidate);direct(m);datagram(2,m);udpProbesSent++;}
        boolean canSend=sending==null||sending.isDone();
        if(sending!=null&&sending.isCompletedExceptionally()){close();return;}
        if(!sameScope(desired,sent==null?new View("",Set.of(),PartyRoster.EMPTY,0,false):sent)&&!canSend)return;
        if(canSend&&(!desired.equals(sent)||now>=nextView)){
            if(sent==null||!sameScope(desired,sent)){scope++;seen.clear();pendingStates.clear();nextRelayPosition=0;}
            sent=desired;nextView=now+10000;var m=new JsonObject();m.addProperty("type","view");m.addProperty("scope",scope);m.addProperty("world",sent.world());m.addProperty("rosterSequence",++rosterSequence);
            m.addProperty("p2p",sent.p2p()&&!forceWss);
            var players=new JsonArray();sent.players().stream().sorted().limit(2048).forEach(id->players.add(id.toString()));m.add("players",players);
            var roster=new JsonObject();var members=new JsonArray();sent.roster().members().stream().sorted().forEach(id->members.add(id.toString()));roster.add("members",members);
            var names=new JsonArray();sent.roster().names().stream().sorted().forEach(names::add);roster.add("names",names);roster.addProperty("environment",sent.environment());roster.addProperty("esp",sent.esp());roster.add("sharing",sent.audience().json());m.add("roster",roster);
            send(m);canSend=false;
        }
        if(canSend&&now>=nextProbe){nextProbe=now+1000;var m=new JsonObject();m.addProperty("type","ping");m.addProperty("ping",now);m.addProperty("fallback",!selected);direct(m);send(m);canSend=false;}
        if(acknowledged!=scope)return;
        var value=block;
        if(!value.equals(sentBlock)||now>=nextBlock&&(value.hand()!=BlockHand.NONE||stopCopies>0)){
            if(!value.equals(sentBlock))stopCopies=value.hand()==BlockHand.NONE&&(selected||peers.size()>0)?3:0;
            var m=new JsonObject();m.addProperty("hand",value.hand().wire);m.addProperty("itemId",value.itemId());state("blocking",++blockSequence,m);sentBlock=value;nextBlock=now+100;if(stopCopies>0)stopCopies--;
        }
        var pos=position;if(sent.audience().publishes(sent.roster(),sent.esp())&&!sent.world().isEmpty()&&pos!=null&&now>=nextPosition&&PartyClient.validPosition(pos)){
            nextPosition=now+sampleInterval(peers.sendingDirect(),selected,selected?udpPositionInterval:wssPositionInterval);var m=new JsonObject();m.addProperty("x",pos.x());m.addProperty("y",pos.y());m.addProperty("z",pos.z());m.addProperty("width",pos.width());m.addProperty("height",pos.height());state("position",++positionSequence,m);
        }
        if(canSend&&!selected&&!pendingStates.isEmpty()){String channel=pendingStates.containsKey("blocking")?"blocking":"position";send(pendingStates.remove(channel));}
    }
    private static Map<UUID,BlockingTransport.Pose> unexpiredBlocks(Map<UUID,BlockingTransport.Pose> source,long now){var result=new HashMap<>(source);result.values().removeIf(p->p.expiresAt()<=now);return Map.copyOf(result);}
    private static Map<UUID,PartyClient.Teammate> unexpiredPositions(Map<UUID,PartyClient.Teammate> source,long now){var result=new HashMap<>(source);result.values().removeIf(p->p.expiresAt()<=now);return Map.copyOf(result);}
    @Override public void onOpen(WebSocket ws){synchronized(this){socket=ws;if(closed){ws.abort();return;}var m=new JsonObject();m.addProperty("type","hello");m.addProperty("client",clientId);m.addProperty("token",token);send(m);token=null;}ws.request(1);}
    @Override public CompletionStage<?> onText(WebSocket ws,CharSequence part,boolean last){
        if(closed)return null;if(fragments.length()+part.length()>262144){close();return null;}fragments.append(part);
        if(last){String message=fragments.toString();fragments.setLength(0);try{worker.execute(()->{try{receive(JsonParser.parseString(message).getAsJsonObject(),false);}catch(Exception invalid){close();}});}catch(RejectedExecutionException ignored){}}
        ws.request(1);return null;
    }
    private synchronized void receive(JsonObject m,boolean datagram)throws Exception{
        if(closed)return;String type=m.get("type").getAsString();long now=clock();if(!datagram)lastWs=now;
        if(type.equals("hello")&&!datagram){if(ready||m.get("version").getAsInt()!=1)throw new IllegalArgumentException();expires=m.get("expiresAt").getAsLong();epoch=m.get("epoch").getAsLong();ready=true;return;}
        if(!ready)return;
        if(type.equals("view")&&!datagram){acknowledged=m.get("scope").getAsLong();nextUdpProbe=0;return;}
        if(type.equals("pong")&&!datagram){route.wss(now-m.get("ping").getAsLong());if(m.has("positionIntervalMs"))wssPositionInterval=positionInterval(false,m.get("positionIntervalMs").getAsInt());return;}
        if(type.equals("probe")&&datagram){udpReplies++;lastUdpReply=now;route.udp(now,now-m.get("ping").getAsLong(),m.get("healthy").getAsBoolean());if(m.has("positionIntervalMs"))udpPositionInterval=positionInterval(true,m.get("positionIntervalMs").getAsInt());
            if(m.has("cookie")){String cookie=m.get("cookie").getAsString();if(cookie.matches("[a-f0-9]{32}")&&!cookie.equals(peerCookie)){peerCookie=cookie;nextUdpProbe=0;}}
            peers.permit(m.getAsJsonArray("peerIds"),now);return;}
        if(type.equals("peer-list")&&datagram){if(ready()&&m.get("scope").getAsLong()==scope)peers.permit(m.getAsJsonArray("ids"),now);return;}
        if(type.equals("peer")&&datagram){try{peers.offer(m,now);}catch(Exception invalid){/* Keep relaying if a direct offer is unusable. */}return;}
        if(type.equals("udp")&&!datagram){if(!forceWss)try{install(m.getAsJsonObject("grant"));}catch(java.io.IOException unavailable){if(udp!=null)udp.close();udp=null;udpFailure="UDP setup failed"+(requestedUdpPort==0?"":" for local port "+requestedUdpPort)+" ("+unavailable.getClass().getSimpleName()+")";}return;}
        if(type.equals("record"))acceptRecord(m,Math.max(0,(datagram?route.udpMs():route.wssMs())/2),datagram?RealtimeConnections.Path.UDP:RealtimeConnections.Path.TCP);
    }
    private void acceptRecord(JsonObject m,double latency,RealtimeConnections.Path path)throws Exception{
        long now=clock();if(!ready()||m.get("scope").getAsLong()!=scope||!m.get("world").getAsString().equals(desired.world()))return;
        UUID id=UUID.fromString(m.get("uuid").getAsString());String channel=m.get("channel").getAsString(),name=m.get("name").getAsString();
        if(!name.matches("[A-Za-z0-9_]{3,16}")||!(channel.equals("blocking")&&desired.players().contains(id)||channel.equals("position")&&desired.audience().accepts(id,name,desired.roster(),desired.esp())))return;
        var stamp=new Stamp(m.get("epoch").getAsLong(),m.get("sequence").getAsLong());String key=channel+id;var previous=seen.get(key);
        if(stamp.epoch()<1||stamp.sequence()<1||previous!=null&&(stamp.epoch()<previous.epoch()||stamp.epoch()==previous.epoch()&&stamp.sequence()<=previous.sequence()))return;
        seen.put(key,stamp);if(seen.size()>4096){close();return;}
        long maximum=channel.equals("blocking")?1250:3000;
        long remaining=Math.min(maximum,m.get("expiresAt").getAsLong()-m.get("serverTime").getAsLong()-(long)latency);
        var value=m.getAsJsonObject("value");
        if(channel.equals("blocking")){
            var result=new HashMap<>(blocks);BlockHand hand=BlockHand.fromWire(value.get("hand").getAsString());String item=value.get("itemId").getAsString();
            if(hand==BlockHand.NONE||remaining<=0){result.remove(id);connections.remove(id,channel);}
            else if(item.matches("(minecraft:[a-z0-9_./-]{1,128}|skyblock:[A-Z0-9_]{1,128})")){result.put(id,new BlockingTransport.Pose(hand,now+remaining,item));connections.received(id,channel,path,now+remaining);}
            blocks=Map.copyOf(result);
        }else{
            var result=new HashMap<>(teammates);var pos=new PartyClient.Position(value.get("x").getAsDouble(),value.get("y").getAsDouble(),value.get("z").getAsDouble(),value.get("width").getAsDouble(),value.get("height").getAsDouble());
            if(!PartyClient.validPosition(pos))return;if(remaining<=0){result.remove(id);connections.remove(id,channel);}else{
                result.put(id,new PartyClient.Teammate(id,name,pos,System.currentTimeMillis()+remaining,
                    m.has("sampleTime")?m.get("sampleTime").getAsLong():m.get("expiresAt").getAsLong()-3000,now,stamp.epoch(),m.has("actorScope")?m.get("actorScope").getAsLong():1));
                connections.received(id,channel,path,now+remaining);
            }teammates=Map.copyOf(result);
        }
    }
    private void install(JsonObject grant)throws Exception{
        String id=grant.get("id").getAsString();if(id.equals(grantId))return;
        if(grant.get("version").getAsInt()!=3||!id.matches("[a-f0-9]{32}")||!grant.get("key").getAsString().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid realtime grant");
        String host=SyncEndpoint.normalizeHost(grant.get("host").getAsString());int port=grant.get("port").getAsInt();if(host==null||host.isBlank()||port<1||port>65535)throw new IllegalArgumentException("Invalid realtime address");
        peers.close();peerCookie="";if(udp!=null)udp.close();relayAddress=new InetSocketAddress(InetAddress.getByName(host),port);var socket=datagrams.open();
        try{
            socket.connect(relayAddress);socket.setSoTimeout(1000);
            localCandidate=new JsonObject();localCandidate.addProperty("host",socket.getLocalAddress().getHostAddress());localCandidate.addProperty("port",socket.getLocalPort());socket.disconnect();
        }catch(Exception failure){socket.close();throw failure;}
        udpFailure="";
        cipher=new UdpPacket(HexFormat.of().parseHex(id),HexFormat.of().parseHex(grant.get("key").getAsString()),true,3);grantId=id;outgoing=new long[3];incoming=new long[3];udp=socket;nextUdpProbe=0;
        var reader=new Thread(()->readUdp(socket),"CustomCosmetics-Realtime-UDP");reader.setDaemon(true);reader.start();
    }
    private void readUdp(DatagramSocket source){
        while(!closed&&!source.isClosed()){
            try{byte[] data=new byte[1201];var packet=new DatagramPacket(data,data.length);source.receive(packet);if(packet.getLength()>1200)continue;
                synchronized(this){if(source!=udp)return;byte[] bytes=Arrays.copyOf(data,packet.getLength());var address=(InetSocketAddress)packet.getSocketAddress();
                    if(peers.receive(bytes,address,clock()))continue;if(!address.equals(relayAddress))continue;
                    var decoded=cipher.decode(bytes);if(decoded.flow()==2&&decoded.sequence()<=incoming[2])continue;incoming[decoded.flow()]=Math.max(incoming[decoded.flow()],decoded.sequence());receive(JsonParser.parseString(new String(decoded.body(),StandardCharsets.UTF_8)).getAsJsonObject(),true);}
            }catch(SocketTimeoutException|PortUnreachableException unavailable){}catch(SocketException stopped){return;}catch(Exception malformed){/* Ignore unauthenticated or malformed datagrams. */}
        }
    }
    @Override public CompletionStage<?> onPing(WebSocket ws,java.nio.ByteBuffer data){ws.request(1);return ws.sendPong(data);}
    @Override public CompletionStage<?> onPong(WebSocket ws,java.nio.ByteBuffer data){ws.request(1);return null;}
    @Override public CompletionStage<?> onClose(WebSocket ws,int code,String reason){fail("WSS closed (code "+code+")");return null;}
    @Override public void onError(WebSocket ws,Throwable error){fail("WSS error ("+error.getClass().getSimpleName()+")");}
    @Override public synchronized void close(){if(closed)return;closed=true;ready=false;token=null;peers.close();pendingStates.clear();blocks=Map.of();teammates=Map.of();if(udp!=null)udp.close();if(socket!=null)socket.abort();if(connecting!=null)connecting.cancel(true);worker.shutdownNow();http.shutdownNow();RemoteAccess.release(this);}
}
