package com.customcosmetics.features.skins;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SkinTransferTest {
    @Test void negotiatedWindowBatchesChunksButLegacyPeersKeepOriginalPacing(){
        var document=new SkinTransfer.Document(new byte[SkinTransfer.CHUNK*64],false);
        for(boolean modern:new boolean[]{false,true}){
            var transfer=new SkinTransfer(d->{});transfer.tick(0,document,false,m->true);
            var clear=new JsonObject();clear.addProperty("op","clear");if(modern)clear.addProperty("window",SkinTransfer.WINDOW);transfer.receive(clear,1,document,false);
            var want=new JsonObject();want.addProperty("op","want");want.addProperty("hash",document.hash());var indices=new JsonArray();for(int i=0;i<(modern?64:8);i++)indices.add(i);want.add("indices",indices);transfer.receive(want,1,document,false);
            var sent=new ArrayList<JsonObject>();transfer.tick(2,document,false,m->{sent.add(m.deepCopy());return true;});
            assertEquals(modern?64:1,sent.stream().filter(m->m.get("op").getAsString().equals("chunk")).count());
        }
    }
    @Test void missingReorderedAndDuplicateChunksRecoverAndHashIsVerified(){
        byte[] bytes=new byte[13800];new Random(123).nextBytes(bytes);var document=new SkinTransfer.Document(bytes,true);var result=new ArrayList<SkinTransfer.Document>();var a=new SkinTransfer(d->{});var b=new SkinTransfer(result::add);var ab=new ArrayList<JsonObject>();var ba=new ArrayList<JsonObject>();
        int dropped=0;for(long now=0;now<12000&&result.isEmpty();now+=20){a.tick(now,document,false,m->{ab.add(m.deepCopy());return true;});b.tick(now,null,true,m->{ba.add(m.deepCopy());return true;});Collections.reverse(ab);for(var m:ab){if(m.get("op").getAsString().equals("chunk")&&dropped++%7==0)continue;b.receive(m,now,null,true);b.receive(m,now,null,true);}ab.clear();for(var m:ba)a.receive(m,now,document,false);ba.clear();}
        assertFalse(result.isEmpty());var restored=result.getLast();assertArrayEquals(bytes,restored.bytes());assertTrue(restored.slim());assertEquals(document.hash(),restored.hash());
    }
    @Test void optOutAndMalformedManifestNeverAllocateOrDeliverASkin(){var result=new ArrayList<SkinTransfer.Document>();var transfer=new SkinTransfer(result::add);var offer=new JsonObject();offer.addProperty("op","offer");offer.addProperty("hash","a".repeat(64));offer.addProperty("size",SkinImages.MAX_BYTES+1);offer.addProperty("slim",false);transfer.receive(offer,0,null,true);offer.addProperty("size",100);transfer.receive(offer,1,null,false);var sent=new ArrayList<JsonObject>();transfer.tick(2,null,false,m->{sent.add(m);return true;});assertTrue(result.isEmpty());assertTrue(sent.stream().noneMatch(m->m.get("op").getAsString().equals("want")));}
    @Test void datagramsStayBelowTheEncryptedTransportLimit(){var local=new SkinTransfer.Document(new byte[2000],false);var transfer=new SkinTransfer(d->{});transfer.tick(0,local,false,m->true);var request=new JsonObject();request.addProperty("op","want");request.addProperty("hash",local.hash());var indices=new JsonArray();indices.add(0);request.add("indices",indices);transfer.receive(request,0,local,false);int[] chunks={0};transfer.tick(1,local,false,m->{m.addProperty("type","skin");m.addProperty("world","a".repeat(64));m.addProperty("scope",Long.MAX_VALUE);assertTrue(m.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length+45<=1200);if(m.get("op").getAsString().equals("chunk"))chunks[0]++;return true;});assertEquals(1,chunks[0]);}
    @Test void contentThatDoesNotMatchTheAnnouncedHashIsDiscarded(){var received=new ArrayList<SkinTransfer.Document>();var transfer=new SkinTransfer(received::add);var manifest=new JsonObject();manifest.addProperty("op","offer");manifest.addProperty("hash","a".repeat(64));manifest.addProperty("size",3);manifest.addProperty("slim",false);transfer.receive(manifest,0,null,true);var chunk=new JsonObject();chunk.addProperty("op","chunk");chunk.addProperty("hash","a".repeat(64));chunk.addProperty("index",0);chunk.addProperty("data",Base64.getEncoder().encodeToString(new byte[3]));transfer.receive(chunk,1,null,true);assertTrue(received.isEmpty());}
}
