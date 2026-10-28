package com.customcosmetics.sync;
import com.customcosmetics.config.ConfigManager;
import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PeerAppearanceSnapshotTest {
    private static java.lang.reflect.Field field(String name)throws Exception{var field=PresenceSync.class.getDeclaredField(name);field.setAccessible(true);return field;}
    @Test void directOverlaySurvivesHttpRefreshKeepsAuthorityAndRestoresRelayOnLoss()throws Exception{
        var saved=new HashMap<java.lang.reflect.Field,Object>();for(String name:List.of("remoteSnapshot","relaySnapshot","peerSnapshots","peerFallbackPending")){var field=field(name);saved.put(field,field.get(null));}
        boolean offline=ConfigManager.offlineMode;ConfigManager.offlineMode=false;
        try{
            PresenceSync.peerAppearances(Map.of());var id=UUID.randomUUID();
            var nick=new JsonObject();nick.addProperty("enabled",true);nick.addProperty("nickChanger","Relay");var features=new JsonObject();features.add("nickTweaks",nick);
            var user=new JsonObject();user.addProperty("name","Alice");user.add("features",features);var users=new JsonObject();users.add(id.toString(),user);
            var override=new JsonObject();override.addProperty("name","Alice");override.addProperty("prefix","[ADMIN]");var overrides=new JsonObject();overrides.add(id.toString(),override);
            var root=new JsonObject();root.addProperty("authVersion",2);root.add("users",users);root.add("rankOverrides",overrides);
            var decode=PresenceSync.class.getDeclaredMethod("decodePayload",JsonObject.class);decode.setAccessible(true);
            var publish=PresenceSync.class.getDeclaredMethod("publishSnapshot",decode.getReturnType());publish.setAccessible(true);publish.invoke(null,decode.invoke(null,root));
            var peerFeatures=features.deepCopy();peerFeatures.getAsJsonObject("nickTweaks").addProperty("nickChanger","Direct");
            var peer=PresenceSync.decodePeerAppearance(id,"Alice",peerFeatures);var direct=Map.of(id,peer);PresenceSync.peerAppearances(direct);
            assertEquals("Direct",PresenceSync.getChromaProfileByName("Alice").nickChanger());
            var identity=field("remoteSnapshot").get(null);PresenceSync.peerAppearances(direct);assertSame(identity,field("remoteSnapshot").get(null));
            root.add("users",new JsonObject());root.addProperty("appearanceVersion",1);var omitted=new JsonArray();omitted.add(id.toString());root.add("peerExcluded",omitted);override.addProperty("prefix","[MOD]");
            publish.invoke(null,decode.invoke(null,root));
            assertEquals("Direct",PresenceSync.getChromaProfileByName("Alice").nickChanger());
            assertTrue(PresenceSync.remoteRanks().stream().anyMatch(rank->rank.administrator()&&rank.prefix().equals("[MOD]")));
            var effective=PresenceSync.remoteRanks().stream().filter(rank->rank.uuid().equals(id)).reduce((first,last)->last).orElseThrow();assertTrue(effective.administrator());assertEquals("[MOD]",effective.prefix());
            PresenceSync.peerAppearances(Map.of());assertEquals("Relay",PresenceSync.getChromaProfileByName("Alice").nickChanger());assertEquals(true,field("peerFallbackPending").get(null));
        }finally{for(var entry:saved.entrySet())entry.getKey().set(null,entry.getValue());ConfigManager.offlineMode=offline;}
    }
}
