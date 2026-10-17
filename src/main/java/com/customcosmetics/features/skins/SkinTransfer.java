// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.skins;
import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import java.security.MessageDigest;
/** Pull-based bounded chunks. The caller must enforce authenticated, established direct peers. */
public final class SkinTransfer {
    public static final int CHUNK=640,WINDOW=64;
    public interface Source {
        Source NONE=new Source(){public Document local(){return null;}public boolean receiving(){return false;}public void received(UUID id,Document skin){}};
        Document local();boolean receiving();void received(UUID id,Document skin);
    }
    public interface Send {boolean send(JsonObject packet);}
    public static final class Document {
        private final byte[] bytes;private final String hash;private final boolean slim;
        public Document(byte[] bytes,boolean slim){if(bytes==null||bytes.length<1||bytes.length>SkinImages.MAX_BYTES)throw new IllegalArgumentException("Invalid skin size");this.bytes=bytes.clone();this.slim=slim;this.hash=SkinTransfer.hash(bytes);}
        public byte[] bytes(){return bytes.clone();}public String hash(){return hash;}public int size(){return bytes.length;}public boolean slim(){return slim;}
        byte[] chunk(int index){return Arrays.copyOfRange(bytes,index*CHUNK,Math.min(bytes.length,(index+1)*CHUNK));}
    }
    private final Consumer<Document> received;private String announced;private long nextOffer,nextRequest,lastProgress;private boolean manifestSeen;private JsonObject reply;
    private String incomingHash;private int size,receivedChunks;private boolean slim;private byte[][] chunks;private long[] requestDue;
    private int remoteWindow=8;
    private Document installed;private final LinkedHashSet<Integer> outgoing=new LinkedHashSet<>();
    public SkinTransfer(Consumer<Document> received){this.received=received;}
    public void tick(long now,Document local,boolean receiving,Send send){
        if(reply!=null&&send.send(reply))reply=null;
        String key=local==null?"":local.hash()+":"+local.slim();
        if(!Objects.equals(key,announced)||now>=nextOffer){var m=new JsonObject();m.addProperty("op",local==null?"clear":"offer");m.addProperty("window",WINDOW);if(local!=null){m.addProperty("hash",local.hash());m.addProperty("size",local.size());m.addProperty("slim",local.slim());}if(send.send(m)){if(!Objects.equals(key,announced)){outgoing.clear();manifestSeen=false;}announced=key;nextOffer=now+(manifestSeen?5000:750);}}
        if(!receiving||chunks!=null&&now-lastProgress>30000)resetIncoming();
        if(chunks!=null&&now>=nextRequest){
            var wanted=new JsonArray();int missing=0;
            for(int i=0;i<chunks.length&&missing<remoteWindow;i++)if(chunks[i]==null){missing++;if(now>=requestDue[i])wanted.add(i);}
            if(!wanted.isEmpty()){var m=new JsonObject();m.addProperty("op","want");m.addProperty("hash",incomingHash);m.add("indices",wanted);if(send.send(m)){for(var index:wanted)requestDue[index.getAsInt()]=now+500;nextRequest=now+500;}}
        }
        if(local==null)outgoing.clear();else if(!outgoing.isEmpty()){
            // Older clients advertise no window and retain the original one-chunk pacing.
            for(int sent=0;sent<(remoteWindow>8?WINDOW:1)&&!outgoing.isEmpty();sent++){
                int index=outgoing.iterator().next();if(index*CHUNK>=local.size()){outgoing.remove(index);continue;}var m=new JsonObject();m.addProperty("op","chunk");m.addProperty("hash",local.hash());m.addProperty("index",index);m.addProperty("data",Base64.getEncoder().encodeToString(local.chunk(index)));if(!send.send(m))break;outgoing.remove(index);
            }
        }
    }
    public void receive(JsonObject m,long now,Document local,boolean receiving){
        String op=m.get("op").getAsString();
        if(op.equals("offer")||op.equals("clear"))remoteWindow=m.has("window")&&integer(m,"window")==WINDOW?WINDOW:8;
        if(op.equals("clear")){resetIncoming();installed=null;if(receiving)received.accept(null);reply=new JsonObject();reply.addProperty("op","cleared");return;}
        if(op.equals("cleared")){if(local==null){manifestSeen=true;nextOffer=now+5000;}return;}
        if(!m.has("hash")||!m.get("hash").getAsString().matches("[a-f0-9]{64}"))return;String hash=m.get("hash").getAsString();
        if((op.equals("ready")||op.equals("decline"))&&local!=null&&local.hash().equals(hash)){manifestSeen=true;nextOffer=now+5000;return;}
        if(op.equals("offer")&&!receiving){reply=ack("decline",hash);return;}
        if(op.equals("offer")&&receiving){
            int length=integer(m,"size");boolean narrow=m.get("slim").getAsBoolean();if(length<1||length>SkinImages.MAX_BYTES)return;
            if(installed!=null&&installed.hash().equals(hash)&&installed.size()==length){if(installed.slim()!=narrow)installed=new Document(installed.bytes(),narrow);received.accept(installed);reply=ack("ready",hash);return;}
            if(hash.equals(incomingHash)&&length==size){slim=narrow;return;}
            incomingHash=hash;size=length;slim=narrow;chunks=new byte[(size+CHUNK-1)/CHUNK][];requestDue=new long[chunks.length];receivedChunks=0;lastProgress=now;nextRequest=0;return;
        }
        if(op.equals("want")&&local!=null&&local.hash().equals(hash)){
            manifestSeen=true;nextOffer=now+5000;
            var indices=m.getAsJsonArray("indices");if(indices.size()>WINDOW)return;for(var v:indices){int index=v.getAsInt();if(v.getAsDouble()!=index||index<0||index>=(local.size()+CHUNK-1)/CHUNK)return;if(outgoing.size()<WINDOW*2)outgoing.add(index);}return;
        }
        if(!op.equals("chunk")||!receiving||chunks==null||!hash.equals(incomingHash))return;
        int index=integer(m,"index");if(index<0||index>=chunks.length||chunks[index]!=null)return;String encoded=m.get("data").getAsString();if(encoded.length()>((CHUNK+2)/3)*4)return;
        var bytes=Base64.getDecoder().decode(encoded);if(bytes.length!=Math.min(CHUNK,size-index*CHUNK))return;chunks[index]=bytes;receivedChunks++;lastProgress=now;nextRequest=Math.min(nextRequest,now+10);
        if(receivedChunks==chunks.length){byte[] joined=new byte[size];for(int i=0;i<chunks.length;i++)System.arraycopy(chunks[i],0,joined,i*CHUNK,chunks[i].length);boolean narrow=slim;String expected=incomingHash;resetIncoming();if(hash(joined).equals(expected)){installed=new Document(joined,narrow);received.accept(installed);reply=ack("ready",expected);}}
    }
    private static int integer(JsonObject m,String key){var value=m.get(key);int n=value.getAsInt();return value.getAsDouble()==n?n:-1;}
    private void resetIncoming(){incomingHash=null;chunks=null;requestDue=null;size=receivedChunks=0;}
    private static JsonObject ack(String type,String hash){var m=new JsonObject();m.addProperty("op",type);m.addProperty("hash",hash);return m;}
    public void suspend(){resetIncoming();outgoing.clear();announced=null;manifestSeen=false;reply=null;remoteWindow=8;}
    public static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
