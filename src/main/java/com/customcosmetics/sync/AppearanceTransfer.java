// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.features.skins.SkinTransfer;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.*;

/** Small appearance documents over the existing reliable, bounded direct-peer chunk protocol. */
public final class AppearanceTransfer {
    public static final int MAX_COMPRESSED=32*1024,MAX_RAW=64*1024;
    private static final Set<String> FEATURES=Set.of("nickTweaks","smolPeople","playerSize","accessories","creatureModel");
    public static final class Document {
        private final SkinTransfer.Document wire;
        private Document(byte[] bytes){wire=new SkinTransfer.Document(bytes,false);}
        public String hash(){return wire.hash();}
        public int size(){return wire.size();}
    }
    private final SkinTransfer transfer;
    public AppearanceTransfer(Consumer<JsonObject> received){
        transfer=new SkinTransfer(document->{received.accept(document==null?null:decode(document.bytes()));});
    }
    public static Document encode(JsonObject features){
        validate(features);byte[] raw=features.toString().getBytes(StandardCharsets.UTF_8);
        if(raw.length>MAX_RAW)throw new IllegalArgumentException("Appearance exceeds document limit");
        try{var output=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(output)){gzip.write(raw);}byte[] bytes=output.toByteArray();if(bytes.length>MAX_COMPRESSED)throw new IllegalArgumentException("Appearance exceeds transfer limit");return new Document(bytes);}
        catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    static JsonObject decode(byte[] compressed){
        if(compressed.length>MAX_COMPRESSED)throw new IllegalArgumentException("Appearance exceeds transfer limit");
        try(var gzip=new GZIPInputStream(new ByteArrayInputStream(compressed))){
            byte[] raw=gzip.readNBytes(MAX_RAW+1);if(raw.length>MAX_RAW)throw new IllegalArgumentException("Appearance expands beyond document limit");
            var features=JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject();validate(features);return features;
        }catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    private static void validate(JsonObject features){
        if(!FEATURES.containsAll(features.keySet()))throw new IllegalArgumentException("Non-appearance field in peer document");
        validateValue(features,0);
    }
    private static void validateValue(JsonElement value,int depth){
        if(depth>16)throw new IllegalArgumentException("Appearance nesting limit");
        if(value.isJsonObject())for(var entry:value.getAsJsonObject().entrySet())validateValue(entry.getValue(),depth+1);
        else if(value.isJsonArray()){if(value.getAsJsonArray().size()>256)throw new IllegalArgumentException("Appearance array limit");for(var item:value.getAsJsonArray())validateValue(item,depth+1);}
        else if(value.isJsonPrimitive()){
            var primitive=value.getAsJsonPrimitive();
            if(primitive.isNumber()&&!Double.isFinite(primitive.getAsDouble()))throw new IllegalArgumentException("Non-finite appearance value");
            if(primitive.isString()&&primitive.getAsString().length()>2048)throw new IllegalArgumentException("Appearance string limit");
        }
    }
    public void tick(long now,Document local,SkinTransfer.Send send){transfer.tick(now,local==null?null:local.wire,true,send);}
    public void receive(JsonObject message,long now,Document local){
        String op=message.get("op").getAsString();
        if(op.equals("offer")&&(message.get("size").getAsInt()<1||message.get("size").getAsInt()>MAX_COMPRESSED))return;
        if(op.equals("chunk")&&message.get("data").getAsString().length()>860)return;
        transfer.receive(message,now,local==null?null:local.wire,true);
    }
    public void suspend(){transfer.suspend();}
}
