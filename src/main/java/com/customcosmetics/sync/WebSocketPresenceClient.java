// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.features.blocking.BlockHand;
import com.customcosmetics.features.blocking.BlockingRules;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.net.ssl.SSLParameters;

/** A separate cosmetic WebSocket, with current-state coalescing and bounded queues. */
public final class WebSocketPresenceClient implements BlockingPresence, WebSocket.Listener {
    public static final int TTL_MS = 1250, MAX_PLAYERS = 2048;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r,"CustomCosmetics-WSS"); thread.setDaemon(true); return thread;
    });
    private final Consumer<Map<UUID, BlockingTransport.Pose>> receiver;
    private final HttpClient http;
    private final PresenceAuthClient.Session session;
    private volatile boolean closed, ready, readOnly;
    private volatile String failure="";
    private volatile WebSocket socket;
    private volatile CompletableFuture<WebSocket> connecting;
    private volatile UdpPresenceClient.Scope desired = new UdpPresenceClient.Scope("",Set.of());
    private volatile UdpPresenceClient.State state = new UdpPresenceClient.State(BlockHand.NONE,"");
    private volatile long acknowledged, expires;
    private volatile Map<UUID, BlockingTransport.Pose> remoteSnapshot = Map.of();
    private UdpPresenceClient.Scope sentScope;
    private UdpPresenceClient.State sentState;
    private long revision, minimumWorldRevision, nextPose;
    private final long started = now();
    private CompletableFuture<WebSocket> sending;
    private final StringBuilder message = new StringBuilder();
    private static long now() { return System.nanoTime()/1_000_000; }

    public WebSocketPresenceClient(String base, PresenceAuthClient.Session session,
            Consumer<Map<UUID, BlockingTransport.Pose>> receiver) {
        this.receiver=receiver;this.session=session;
        if(session!=null&&!session.baseUrl().equals(base))throw new IllegalArgumentException("Session belongs to another service");
        var route=PresenceProxyResolver.webSocketRoute();
        var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER)
            .proxy(new ProxySelector(){
                @Override public List<Proxy> select(URI uri){return List.of(route.proxy()==null?Proxy.NO_PROXY:route.proxy());}
                @Override public void connectFailed(URI uri,SocketAddress address,java.io.IOException error){}
            });
        if(route.proxy()!=null && !com.customcosmetics.config.ConfigManager.syncProxyAuth.isBlank()) {
            String[] credentials=com.customcosmetics.config.ConfigManager.syncProxyAuth.split(":",2);
            if(credentials.length==2)builder.authenticator(new Authenticator(){
                @Override protected PasswordAuthentication getPasswordAuthentication(){
                    return getRequestorType()==RequestorType.PROXY&&getRequestingHost().equalsIgnoreCase(route.host())&&getRequestingPort()==route.port()
                        ? new PasswordAuthentication(credentials[0],credentials[1].toCharArray()):null;
                }
            });
        }
        if(!com.customcosmetics.config.ConfigManager.syncServerSni){var ssl=new SSLParameters();ssl.setServerNames(List.of());builder.sslParameters(ssl);}
        http=builder.build();
        connecting=http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8)).subprotocols("customcosmetics.v1")
            .buildAsync(endpoint(base),this);
        connecting.whenComplete((ws,error)->{if(error!=null){failure="connect:"+error.getClass().getSimpleName();close();}else if(closed)ws.abort();});
        worker.scheduleWithFixedDelay(()->{try{pump();}catch(RuntimeException error){failure="send:"+error.getClass().getSimpleName();close();}},0,25,TimeUnit.MILLISECONDS);
    }
    static URI endpoint(String base) {
        var uri=URI.create(base);
        if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null
                ||uri.getFragment()!=null||!Set.of("","/").contains(uri.getPath()))throw new IllegalArgumentException("Invalid cosmetic endpoint");
        return URI.create((uri.getScheme().equals("https")?"wss":"ws")+"://"+uri.getRawAuthority()+"/actions/ws");
    }
    @Override public void update(String world,Set<UUID> players,BlockHand hand,String itemId){
        if(!world.isEmpty()&&!world.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid world hash");
        desired=new UdpPresenceClient.Scope(world,players.stream().sorted().limit(MAX_PLAYERS).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        setPose(hand,itemId);
    }
    @Override public void setPose(BlockHand hand,String itemId){state=new UdpPresenceClient.State(hand,itemId);}
    @Override public boolean closed(){return closed;}
    String diagnostics(){return "ready="+ready+",closed="+closed+",revision="+revision+",acknowledged="+acknowledged+",failure="+failure;}
    private void pump(){
        if(closed)return;
        if(!ready){if(now()-started>=10_000)close();return;}
        if(socket==null)return;
        if(expires<=System.currentTimeMillis()){close();return;}
        if(sending!=null&&!sending.isDone())return;
        if(sending!=null&&sending.isCompletedExceptionally()){close();return;}
        var scope=desired;
        if(!scope.equals(sentScope)){
            boolean changedWorld=sentScope==null||!sentScope.world().equals(scope.world());
            sentScope=scope;revision++;sentState=null;
            if(changedWorld){minimumWorldRevision=revision;acknowledged=0;publishSnapshot(Map.of());}
            else {
                var retained=new HashMap<UUID,BlockingTransport.Pose>();
                remoteSnapshot.forEach((id,pose)->{if(scope.players().contains(id)&&pose.expiresAt()>now())retained.put(id,pose);});
                publishSnapshot(Map.copyOf(retained));
            }
            var body=new JsonObject();body.addProperty("type","scope");body.addProperty("revision",revision);body.addProperty("world",scope.world());
            var players=new JsonArray();scope.players().stream().sorted().forEach(id->players.add(id.toString()));body.add("players",players);send(body);return;
        }
        var effective=!readOnly&&!scope.world().isEmpty()&&!scope.players().isEmpty()?state:new UdpPresenceClient.State(BlockHand.NONE,"");
        // WebSocket messages arrive in order: the service processes the preceding scope before this pose.
        // Waiting for the scope acknowledgement adds a round trip and can swallow a short block entirely.
        if(!readOnly&&!scope.world().isEmpty()&&(!effective.equals(sentState)||effective.hand()!=BlockHand.NONE&&now()>=nextPose)){
            var body=new JsonObject();body.addProperty("type","pose");body.addProperty("revision",revision);body.addProperty("hand",effective.hand().wire);body.addProperty("itemId",effective.itemId());
            sentState=effective;nextPose=now()+250;send(body);
        }
    }
    private void publishSnapshot(Map<UUID,BlockingTransport.Pose> poses){remoteSnapshot=poses;receiver.accept(poses);}
    private void send(JsonObject body){sending=socket.sendText(body.toString(),true);}
    @Override public void onOpen(WebSocket ws){
        socket=ws;if(closed){ws.abort();return;}
        var hello=new JsonObject();hello.addProperty("type","hello");if(session!=null)hello.addProperty("token",session.token());
        send(hello);ws.request(1);
    }
    @Override public CompletionStage<?> onText(WebSocket ws,CharSequence text,boolean last){
        if(closed)return null;
        if(message.length()+text.length()>1_048_576){close();return null;}
        message.append(text);
        if(last){
            String full=message.toString();message.setLength(0);
            try{worker.execute(()->{if(closed)return;try{receive(JsonParser.parseString(full).getAsJsonObject());}catch(RuntimeException invalid){failure="protocol:"+invalid.getClass().getSimpleName();close();}});}
            catch(RejectedExecutionException ignored){}
        }
        ws.request(1);return null;
    }
    private void receive(JsonObject body){
        String type=body.get("type").getAsString();
        if(type.equals("hello")){
            if(ready||body.get("version").getAsInt()!=1)throw new IllegalArgumentException("Invalid hello");
            readOnly=body.get("readOnly").getAsBoolean();expires=body.get("expiresAt").getAsLong();ready=true;return;
        }
        if(!ready)throw new IllegalArgumentException("Missing hello");
        long receivedRevision=body.get("revision").getAsLong();
        if(receivedRevision<minimumWorldRevision||receivedRevision>revision)return;
        if(type.equals("scope")){acknowledged=Math.max(acknowledged,receivedRevision);return;}
        // Earlier subscriptions in this world are still useful while a new player list is in flight.
        // Filter with the newest interests, and discard every revision belonging to a previous world.
        if(type.equals("poses")){if(sentScope!=null&&acknowledged>=receivedRevision)publishSnapshot(parseSnapshot(body,receivedRevision,sentScope.players(),now()));return;}
        throw new IllegalArgumentException("Unknown message");
    }
    static Map<UUID,BlockingTransport.Pose> parseSnapshot(JsonObject json,long revision,Set<UUID> interests,long time){
        if(!json.get("type").getAsString().equals("poses")||json.get("revision").getAsLong()!=revision||json.get("ttlMs").getAsInt()!=TTL_MS)
            throw new IllegalArgumentException("Invalid snapshot");
        var users=json.getAsJsonObject("users");if(users.size()>MAX_PLAYERS)throw new IllegalArgumentException("Oversized snapshot");
        var result=new HashMap<UUID,BlockingTransport.Pose>();
        for(var entry:users.entrySet()){
            if(!entry.getKey().matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid identity");
            var uuid=UUID.fromString(entry.getKey());var data=entry.getValue().getAsJsonObject();
            var hand=BlockHand.fromWire(data.get("hand").getAsString());String item=data.get("itemId").getAsString();int ttl=data.get("remainingMs").getAsInt();
            if(hand==BlockHand.NONE||item.isEmpty()||!BlockingRules.normalize(item).equals(item)||ttl<1||ttl>TTL_MS)
                throw new IllegalArgumentException("Invalid pose");
            if(interests.contains(uuid))result.put(uuid,new BlockingTransport.Pose(hand,time+ttl,item));
        }
        return Map.copyOf(result);
    }
    @Override public CompletionStage<?> onPing(WebSocket ws,java.nio.ByteBuffer data){ws.request(1);return ws.sendPong(data);}
    @Override public CompletionStage<?> onPong(WebSocket ws,java.nio.ByteBuffer data){ws.request(1);return null;}
    @Override public CompletionStage<?> onClose(WebSocket ws,int code,String reason){failure="close:"+code;close();return null;}
    @Override public void onError(WebSocket ws,Throwable error){failure="transport:"+error.getClass().getSimpleName();close();}
    @Override public void close(){if(closed)return;closed=true;RemoteAccess.release(this);var pending=connecting;if(pending!=null)pending.cancel(true);var ws=socket;if(ws!=null)ws.abort();worker.shutdownNow();http.shutdownNow();remoteSnapshot=Map.of();receiver.accept(Map.of());}
}
