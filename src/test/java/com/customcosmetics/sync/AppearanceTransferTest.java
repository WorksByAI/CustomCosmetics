package com.customcosmetics.sync;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AppearanceTransferTest {
    @Test void boundedChunksRecoverFromLossAndRefreshAnUnchangedDocumentEveryFiveSeconds(){
        var features=new JsonObject();var parts=new JsonObject();var random=new Random(891);
        for(int i=0;i<100;i++){byte[] bytes=new byte[80];random.nextBytes(bytes);parts.addProperty("part"+i,Base64.getEncoder().encodeToString(bytes));}features.add("accessories",parts);
        var document=AppearanceTransfer.encode(features);assertTrue(document.size()>640);
        var received=new ArrayList<JsonObject>();var a=new AppearanceTransfer(ignore->{});var b=new AppearanceTransfer(received::add);
        var ab=new ArrayList<JsonObject>();var ba=new ArrayList<JsonObject>();int chunks=0;
        for(long now=0;now<11000;now+=20){
            a.tick(now,document,m->{ab.add(m.deepCopy());return true;});b.tick(now,null,m->{ba.add(m.deepCopy());return true;});
            Collections.reverse(ab);
            for(var packet:ab){if(packet.get("op").getAsString().equals("chunk")&&chunks++%7==0)continue;b.receive(packet,now,null);}ab.clear();
            for(var packet:ba)a.receive(packet,now,document);ba.clear();
        }
        assertTrue(received.size()>=2,"Unchanged documents must refresh freshness rather than expire after the first transfer");assertEquals(features,received.getLast());
    }
    @Test void authorityLocalFeaturesAndOversizedOrNonFiniteDocumentsAreRejected(){
        for(String key:List.of("rankOverrides","capes","skin","mapEditor","itemCustomization","blocking")){
            var document=new JsonObject();document.add(key,new JsonObject());assertThrows(IllegalArgumentException.class,()->AppearanceTransfer.encode(document));
        }
        var invalid=new JsonObject();invalid.addProperty("creatureModel","x".repeat(2049));assertThrows(IllegalArgumentException.class,()->AppearanceTransfer.encode(invalid));
        invalid.addProperty("creatureModel",Double.NaN);assertThrows(IllegalArgumentException.class,()->AppearanceTransfer.encode(invalid));
        var received=new ArrayList<JsonObject>();var transfer=new AppearanceTransfer(received::add);var offer=new JsonObject();offer.addProperty("op","offer");offer.addProperty("size",AppearanceTransfer.MAX_COMPRESSED+1);transfer.receive(offer,0,null);assertTrue(received.isEmpty());
    }
    @Test void peerCannotDeclareAnAdministratorOrChooseAnotherIdentity(){
        var features=new JsonObject();var nick=new JsonObject();nick.addProperty("enabled",true);nick.addProperty("nickChanger","x".repeat(80));var rank=new JsonObject();rank.addProperty("enabled",true);rank.addProperty("type","admin");rank.addProperty("prefix","[ADMIN]");rank.addProperty("plusColor","dark_blue");nick.add("rank",rank);features.add("nickTweaks",nick);
        var id=UUID.randomUUID();var peer=PresenceSync.decodePeerAppearance(id,"Alice",features);
        assertEquals(id,peer.uuid());assertEquals("Alice",peer.user().name());assertEquals(30,peer.user().nickChanger().length());assertEquals("mvp_plus",peer.rank().type());assertFalse(peer.rank().prefix().contains("ADMIN"));
        assertThrows(IllegalArgumentException.class,()->PresenceSync.decodePeerAppearance(id,"[ADMIN] Bob",features));
    }
    @Test void fallbackRequestsAreBoundedAndContainOnlyCompletedPeerIdentities(){
        var peers=new HashSet<UUID>();for(int i=0;i<30;i++)peers.add(new UUID(0,i+1));
        String url=PresenceSync.peerFetchUrl("https://example.test/users.json",peers);
        assertEquals(16,url.substring(url.indexOf('=')+1).split(",").length);assertEquals("x",PresenceSync.peerFetchUrl("x",Set.of()));
    }
}
