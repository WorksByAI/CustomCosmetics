// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;
import com.customcosmetics.features.party.*;
import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Independent WSS party relay; no messages are sent to Minecraft's chat connection. */
public final class PartyClient implements WebSocket.Listener,AutoCloseable{
    public record Scope(PartyRoster roster,String world,int environment,boolean esp,boolean chat){}
    public record Position(double x,double y,double z,double width,double height){}
    public record Teammate(UUID uuid,String name,Position position,long expiresAt,long sampledAt,long receivedAt,long epoch,long scope){
        public Teammate(UUID uuid,String name,Position position,long expiresAt){this(uuid,name,position,expiresAt,System.nanoTime()/1000000,System.nanoTime()/1000000,0,0);}
    }
    public record Chat(UUID uuid,String name,String text,int delivered){}
    private record Pending(Scope scope,String text){}
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"CustomCosmetics-Party");t.setDaemon(true);return t;});
    private final HttpClient http=WebSocketPresenceClient.createHttpClient();
    private final Consumer<Map<UUID,Teammate>> positions;private final Consumer<Chat> chats;private final Consumer<String> errors;
    private final StringBuilder fragments=new StringBuilder();private final Set<String> seen=new LinkedHashSet<>();
    private final ArrayBlockingQueue<Pending> outgoing=new ArrayBlockingQueue<>(4);
    private volatile Scope desired=new Scope(PartyRoster.EMPTY,"",0,false,false);private volatile Position position;
    private volatile WebSocket socket;private volatile boolean closed,ready;private volatile long expires;
    private volatile CompletableFuture<WebSocket> connecting;private CompletableFuture<WebSocket> sending;
    private Scope sent;private long revision,nextRoster,nextPosition,started=System.currentTimeMillis();
    private Scope acknowledgedScope;
    public PartyClient(String base,PresenceAuthClient.Session identity,Consumer<Map<UUID,Teammate>> positions,Consumer<Chat> chats,Consumer<String> errors){
        if(identity==null||!identity.baseUrl().equals(base))throw new IllegalArgumentException("Party requires an authenticated service session");
        this.positions=positions;this.chats=chats;this.errors=errors;
        token=identity.token();
        connecting=http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8)).subprotocols("customcosmetics.party.v1").buildAsync(endpoint(base),this);
        connecting.whenComplete((ws,error)->{if(error!=null)close();else if(closed)ws.abort();});
        // The token is sent once over the authenticated service connection, never in a URL.
        worker.scheduleWithFixedDelay(()->{try{pump();}catch(RuntimeException invalid){close();}},0,25,TimeUnit.MILLISECONDS);
    }
    private String token;
    static URI endpoint(String base){String endpoint=WebSocketPresenceClient.endpoint(base).toString();return URI.create(endpoint.substring(0,endpoint.length()-"/actions/ws".length())+"/party/ws");}
    public synchronized void update(Scope scope,Position position){
        // Invalidate the previous world's markers before the next network pump runs.
        if(!scope.equals(desired))positions.accept(Map.of());
        desired=scope;this.position=position;
    }
    public boolean ready(){return ready&&!closed;}
    public synchronized boolean sharingReady(){return ready()&&!desired.roster().empty()&&desired.equals(acknowledgedScope);}
    public boolean closed(){return closed;}
    public boolean chat(String text){return ready()&&desired.chat()&&PartyChatRules.valid(text)&&outgoing.offer(new Pending(desired,text));}
    private void send(JsonObject body){sending=socket.sendText(body.toString(),true);}
    private synchronized void pump(){
        if(closed||socket==null)return;
        if(sending!=null&&!sending.isDone())return;
        if(sending!=null&&sending.isCompletedExceptionally()){close();return;}
        if(!ready){if(System.currentTimeMillis()-started>=10000)close();return;}
        long now=System.currentTimeMillis();if(now>=expires){close();return;}
        var scope=desired;
        if(!scope.equals(sent)||now>=nextRoster){
            if(!scope.equals(sent))positions.accept(Map.of());sent=scope;nextRoster=now+10000;revision++;
            var json=new JsonObject();json.addProperty("type","roster");json.addProperty("revision",revision);json.addProperty("world",scope.world());json.addProperty("environment",scope.environment());json.addProperty("esp",scope.esp());json.addProperty("chat",scope.chat());
            var members=new JsonArray();scope.roster().members().stream().sorted().forEach(id->members.add(id.toString()));json.add("members",members);
            var names=new JsonArray();scope.roster().names().stream().sorted().forEach(names::add);json.add("names",names);send(json);return;
        }
        var pending=outgoing.poll();
        if(pending!=null){
            if(!pending.scope().equals(scope)){errors.accept("Party changed; message was not sent.");return;}
            var json=new JsonObject();json.addProperty("type","chat");json.addProperty("revision",revision);json.addProperty("text",pending.text());send(json);return;
        }
        var pos=position;if(scope.esp()&&!scope.roster().empty()&&!scope.world().isEmpty()&&pos!=null&&now>=nextPosition){
            nextPosition=now+500;var json=new JsonObject();json.addProperty("type","position");json.addProperty("revision",revision);
            json.addProperty("x",pos.x());json.addProperty("y",pos.y());json.addProperty("z",pos.z());json.addProperty("width",pos.width());json.addProperty("height",pos.height());send(json);
        }
    }
    @Override public void onOpen(WebSocket ws){socket=ws;if(closed){ws.abort();return;}
        try{worker.execute(()->{if(closed)return;var hello=new JsonObject();hello.addProperty("type","hello");hello.addProperty("token",token);send(hello);token=null;});ws.request(1);}
        catch(RejectedExecutionException stopped){ws.abort();}}
    @Override public CompletionStage<?> onText(WebSocket ws,CharSequence part,boolean last){
        if(closed)return null;if(fragments.length()+part.length()>262144){close();return null;}fragments.append(part);
        if(last){String full=fragments.toString();fragments.setLength(0);try{worker.execute(()->{if(!closed)try{receive(JsonParser.parseString(full).getAsJsonObject());}catch(RuntimeException invalid){close();}});}catch(RejectedExecutionException ignored){}}
        ws.request(1);return null;
    }
    private synchronized void receive(JsonObject json){
        if(closed)return;
        String type=json.get("type").getAsString();
        if(type.equals("hello")){if(ready||json.get("version").getAsInt()!=1)throw new IllegalArgumentException();expires=json.get("expiresAt").getAsLong();ready=true;return;}
        if(!ready)throw new IllegalArgumentException();
        if(type.equals("error")){errors.accept(switch(json.get("code").getAsString()){case "chat_rate_limited"->"Party IRC: wait one second between messages.";case "party_changed"->"Party changed; wait for the member list to refresh.";default->"Party IRC: message was rejected.";});return;}
        if(!desired.equals(sent)||json.get("revision").getAsLong()!=revision)return;
        if(type.equals("roster")){acknowledgedScope=sent;return;}
        if(type.equals("positions")){positions.accept(parsePositions(json,desired.roster(),System.currentTimeMillis()));return;}
        if(type.equals("chat")){
            String id=json.get("id").getAsString();if(seen.contains(id))return;seen.add(id);if(seen.size()>256)seen.remove(seen.iterator().next());
            UUID uuid=UUID.fromString(json.get("uuid").getAsString());String name=json.get("name").getAsString(),text=json.get("text").getAsString();
            if(!name.matches("[A-Za-z0-9_]{3,16}")||!PartyChatRules.valid(text)||!desired.roster().contains(uuid,name))return;
            chats.accept(new Chat(uuid,name,text,json.has("delivered")?json.get("delivered").getAsInt():-1));return;
        }
        throw new IllegalArgumentException();
    }
    static Map<UUID,Teammate> parsePositions(JsonObject json,PartyRoster roster,long now){
        if(json.get("ttlMs").getAsInt()!=3000||json.getAsJsonObject("users").size()>1000)throw new IllegalArgumentException("Invalid party positions");
        var result=new HashMap<UUID,Teammate>();for(var entry:json.getAsJsonObject("users").entrySet()){
            UUID uuid=UUID.fromString(entry.getKey());var data=entry.getValue().getAsJsonObject();String name=data.get("name").getAsString();
            var pos=new Position(data.get("x").getAsDouble(),data.get("y").getAsDouble(),data.get("z").getAsDouble(),data.get("width").getAsDouble(),data.get("height").getAsDouble());
            if(!validPosition(pos)||!name.matches("[A-Za-z0-9_]{3,16}"))throw new IllegalArgumentException("Invalid party player");
            if(roster.contains(uuid,name))result.put(uuid,new Teammate(uuid,name,pos,now+3000));
        }return Map.copyOf(result);
    }
    public static boolean validPosition(Position p){return Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z())&&Double.isFinite(p.width())&&Double.isFinite(p.height())&&Math.abs(p.x())<=30000000&&Math.abs(p.z())<=30000000&&Math.abs(p.y())<=4096&&p.width()>=.1&&p.width()<=8&&p.height()>=.1&&p.height()<=8;}
    @Override public CompletionStage<?> onPing(WebSocket ws,java.nio.ByteBuffer bytes){ws.request(1);return ws.sendPong(bytes);}
    @Override public CompletionStage<?> onPong(WebSocket ws,java.nio.ByteBuffer bytes){ws.request(1);return null;}
    @Override public CompletionStage<?> onClose(WebSocket ws,int status,String reason){close();return null;}
    @Override public void onError(WebSocket ws,Throwable error){close();}
    @Override public synchronized void close(){if(closed)return;closed=true;ready=false;token=null;RemoteAccess.release(this);var pending=connecting;if(pending!=null)pending.cancel(true);if(socket!=null)socket.abort();worker.shutdownNow();http.shutdownNow();outgoing.clear();positions.accept(Map.of());}
}
