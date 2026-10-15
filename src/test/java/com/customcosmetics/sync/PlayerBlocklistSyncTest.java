// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.config.*;
import com.customcosmetics.features.accessories.AccessoriesSettings;
import com.google.gson.*;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerBlocklistSyncTest {
    private static final UUID A=UUID.fromString("12345678-1234-4234-8234-123456789abc");
    private Object snapshot;private boolean offline;private List<PlayerBlocklist.Entry> nicknames,accessories;private Field cache;
    @BeforeEach void isolate()throws Exception{
        cache=PresenceSync.class.getDeclaredField("remoteSnapshot");cache.setAccessible(true);snapshot=cache.get(null);
        offline=ConfigManager.offlineMode;ConfigManager.offlineMode=false;nicknames=ConfigManager.nickTweaksBlockedPlayers;accessories=ConfigManager.accessoriesBlockedPlayers;
        ConfigManager.nickTweaksBlockedPlayers=List.of();ConfigManager.accessoriesBlockedPlayers=List.of();
        var root=JsonParser.parseString("{\"authVersion\":2,\"users\":{\""+A+"\":{\"name\":\"Alice\",\"features\":{\"nickTweaks\":{\"enabled\":true,\"nickChanger\":\"&d暮冬\"},\"smolPeople\":{\"enabled\":true}}}}}").getAsJsonObject();
        root.getAsJsonObject("users").getAsJsonObject(A.toString()).getAsJsonObject("features").add("accessories",AccessoriesSettings.NONE.with("wings",AccessoriesSettings.WINGS.enabled(true)).json());
        var apply=PresenceSync.class.getDeclaredMethod("applyPayload",String.class,boolean.class,boolean.class);apply.setAccessible(true);apply.invoke(null,root.toString(),false,true);
    }
    @AfterEach void restore()throws Exception{cache.set(null,snapshot);ConfigManager.offlineMode=offline;ConfigManager.nickTweaksBlockedPlayers=nicknames;ConfigManager.accessoriesBlockedPlayers=accessories;}
    @Test void cachedPlayerSearchExposesVisibleNicknameAndAccountEvenWhenBlocked(){
        ConfigManager.nickTweaksBlockedPlayers=List.of(new PlayerBlocklist.Entry(A,"Alice"));
        assertEquals(List.of(new PresenceSync.SyncedPlayer(A,"Alice","暮冬")),PresenceSync.syncedPlayers());
        ConfigManager.offlineMode=true;assertTrue(PresenceSync.syncedPlayers().isEmpty());
    }
    @Test void accessoryAndNicknameBlocksAreIndependentAndNeverDeleteCachedAppearance(){
        assertTrue(PresenceSync.getRemoteAccessories(A).wings().enabled());
        ConfigManager.nickTweaksBlockedPlayers=List.of(new PlayerBlocklist.Entry(A,"Alice"));assertTrue(PresenceSync.getRemoteAccessories(A).wings().enabled());
        ConfigManager.nickTweaksBlockedPlayers=List.of();ConfigManager.accessoriesBlockedPlayers=List.of(new PlayerBlocklist.Entry(A,"Alice"));
        assertEquals(AccessoriesSettings.NONE,PresenceSync.getRemoteAccessories(A));assertNotNull(PresenceSync.getChromaProfileByName("Alice"));assertEquals(1,PresenceSync.syncedPlayers().size());
        ConfigManager.accessoriesBlockedPlayers=List.of();assertTrue(PresenceSync.getRemoteAccessories(A).wings().enabled());
    }
    @Test void localBlocklistsAreNotPartOfTheUploadState(){
        var type=Arrays.stream(PresenceSync.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("LocalUserState")).findFirst().orElseThrow();
        for(var field:type.getRecordComponents())assertFalse(field.getName().toLowerCase(Locale.ROOT).contains("blocked"));
    }
}
