package com.customcosmetics.config;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SwordBlockingListsTest {
    @TempDir Path directory;
    private final Map<Field,Object> original=new HashMap<>();
    @BeforeEach void snapshot()throws Exception{for(var field:ConfigManager.class.getFields())if(Modifier.isStatic(field.getModifiers()))original.put(field,field.get(null));}
    @AfterEach void restore()throws Exception{ConfigManager.awaitPendingSaves();for(var e:original.entrySet())e.getKey().set(null,e.getValue());}
    private Path main(){return directory.resolve("config.json");}
    private Path lists(){return SwordBlockingLists.path(main());}
    private void legacy()throws Exception{
        Files.writeString(main(),"{\"settings\":{\"swordBlockingListMode\":\"blacklist\",\"swordBlockingListsApplyToOthers\":true,\"swordBlockingBlacklist\":[\"skyblock:ASPECT_OF_THE_VOID\"],\"swordBlockingWhitelist\":[\"minecraft:diamond_sword\"],\"swordBlockingEnabled\":true,\"swordBlockingStyle\":\"jigsaw\",\"swordBlockingSyncEnabled\":false}}");
    }
    private static SwordBlockingLists.Data independent(){return new SwordBlockingLists.Data("whitelist",false,List.of("minecraft:wooden_sword"),List.of("skyblock:HYPERION"));}
    @Test void legacyListsMigrateToSiblingAndDisappearFromOnlyTheMainSave()throws Exception{
        legacy();ConfigManager.load(main());assertTrue(Files.isRegularFile(lists()));
        var migrated=SwordBlockingLists.read(lists());assertEquals("blacklist",migrated.mode());assertTrue(migrated.applyToOthers());assertEquals(List.of("skyblock:ASPECT_OF_THE_VOID"),migrated.blacklist());
        ConfigManager.save(main());var settings=JsonParser.parseString(Files.readString(main())).getAsJsonObject().getAsJsonObject("settings");
        for(String name:List.of("swordBlockingListMode","swordBlockingBlacklist","swordBlockingWhitelist","swordBlockingListsApplyToOthers"))assertFalse(settings.has(name),name);
        assertTrue(settings.get("swordBlockingEnabled").getAsBoolean());assertEquals("jigsaw",settings.get("swordBlockingStyle").getAsString());assertFalse(settings.get("swordBlockingSyncEnabled").getAsBoolean());
    }
    @Test void validIndependentFileWinsAndAlsoLoadsWithoutAMainFile()throws Exception{
        legacy();SwordBlockingLists.write(lists(),independent());ConfigManager.load(main());
        assertEquals("whitelist",ConfigManager.swordBlockingListMode);assertArrayEquals(new String[]{"skyblock:HYPERION"},ConfigManager.swordBlockingWhitelist);assertFalse(ConfigManager.swordBlockingListsApplyToOthers);
        Files.delete(main());ConfigManager.swordBlockingWhitelist=new String[0];ConfigManager.load(main());assertArrayEquals(new String[]{"skyblock:HYPERION"},ConfigManager.swordBlockingWhitelist);
    }
    @Test void damagedBytesRemainOnLoadAndAreBackedUpBeforeRecoveryWithoutBlockingOtherSettings()throws Exception{
        legacy();byte[] damaged="{ damaged list bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(lists(),damaged);
        ConfigManager.load(main());assertArrayEquals(damaged,Files.readAllBytes(lists()));assertEquals("blacklist",ConfigManager.swordBlockingListMode);
        ConfigManager.nickTweaksNickChanger="Saved safely";ConfigManager.save(main());
        try(var files=Files.list(directory)){var backups=files.filter(p->p.getFileName().toString().startsWith("sword-blocking-lists.invalid-")).toList();assertEquals(1,backups.size());assertArrayEquals(damaged,Files.readAllBytes(backups.getFirst()));}
        assertEquals("blacklist",SwordBlockingLists.read(lists()).mode());
        assertEquals("Saved safely",JsonParser.parseString(Files.readString(main())).getAsJsonObject().getAsJsonObject("settings").get("nickTweaksNickChanger").getAsString());
    }
    @Test void unsafeListDestinationLeavesTheOnlyLegacyMainCopyIntact()throws Exception{
        legacy();byte[] previous=Files.readAllBytes(main());Files.createDirectory(lists());ConfigManager.load(main());ConfigManager.save(main());assertArrayEquals(previous,Files.readAllBytes(main()));assertTrue(Files.isDirectory(lists()));
    }
    @Test void queuedSaveCapturesBothListsBeforeSubsequentUnsavedArrayEdits()throws Exception{
        String[] items={"skyblock:HYPERION"};ConfigManager.swordBlockingWhitelist=items;ConfigManager.swordBlockingListMode="whitelist";ConfigManager.queueSave(main());
        items[0]="skyblock:ASPECT_OF_THE_VOID";ConfigManager.swordBlockingListMode="off";ConfigManager.awaitPendingSaves();
        var data=SwordBlockingLists.read(lists());assertEquals("whitelist",data.mode());assertEquals(List.of("skyblock:HYPERION"),data.whitelist());
    }
    @Test void futureOrMalformedSchemasDoNotSilentlyNormalizeAwayData()throws Exception{
        for(String body:List.of("[]","{\"version\":2}","{\"version\":1,\"mode\":\"off\",\"applyToOthers\":false,\"blacklist\":[true],\"whitelist\":[]}","{\"version\":1,\"mode\":\"off\",\"applyToOthers\":false,\"blacklist\":[\"invalid:item name\"],\"whitelist\":[]}")){
            Files.writeString(lists(),body);assertThrows(java.io.IOException.class,()->SwordBlockingLists.read(lists()));assertEquals(body,Files.readString(lists()));
        }
    }
}
