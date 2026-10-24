// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ItemAppearanceStoreTest {
    @Test void backgroundSavesCoalesceAndOnlyWriteImmutableRequestedSnapshots()throws Exception{
        var tasks=new java.util.ArrayDeque<Runnable>();var path=directory.resolve("async.json");
        var store=new ItemAppearanceStore(path,tasks::add);
        store.put(A,new ItemAppearanceStore.Appearance("first",""));var completion=store.requestSave();
        store.put(A,new ItemAppearanceStore.Appearance("second",""));assertSame(completion,store.requestSave());
        assertEquals(1,tasks.size());assertFalse(Files.exists(path));assertFalse(completion.isDone());
        // An edit after the last requested snapshot must remain dirty, even when the earlier write finishes.
        store.put(B,new ItemAppearanceStore.Appearance("not queued yet",""));tasks.removeFirst().run();completion.join();
        var restored=new ItemAppearanceStore(path);restored.load();assertEquals("second",restored.get(A).name());assertTrue(restored.get(B).empty());assertTrue(store.dirty());
        var finalSave=store.requestSave();tasks.removeFirst().run();finalSave.join();assertFalse(store.dirty());
        restored.load();assertEquals("not queued yet",restored.get(B).name());
    }
    @Test void failedBackgroundWriteKeepsChangesForRetryAndDurableSaveFlushes()throws Exception{
        var tasks=new java.util.ArrayDeque<Runnable>();var blocked=directory.resolve("blocked");Files.writeString(blocked,"file");
        var path=blocked.resolve("items.json");var store=new ItemAppearanceStore(path,tasks::add);
        store.put(A,new ItemAppearanceStore.Appearance("retained",""));var failed=store.requestSave();tasks.removeFirst().run();
        assertThrows(java.util.concurrent.CompletionException.class,failed::join);assertTrue(store.dirty());assertEquals("retained",store.get(A).name());
        Files.delete(blocked);var retry=store.requestSave();tasks.removeFirst().run();retry.join();store.save();
        assertFalse(store.dirty());var restored=new ItemAppearanceStore(path);restored.load();assertEquals("retained",restored.get(A).name());
    }
    @Test void reloadWaitsForTheLatestQueuedWrite()throws Exception{
        var tasks=new java.util.ArrayDeque<Runnable>();var store=new ItemAppearanceStore(directory.resolve("reload.json"),tasks::add);
        store.put(A,new ItemAppearanceStore.Appearance("first",""));store.requestSave();
        var started=new java.util.concurrent.CountDownLatch(1);
        var reload=java.util.concurrent.CompletableFuture.runAsync(()->{
            started.countDown();try{store.load();}catch(java.io.IOException error){throw new java.util.concurrent.CompletionException(error);}
        });
        assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS));assertFalse(reload.isDone());
        store.put(A,new ItemAppearanceStore.Appearance("latest",""));store.requestSave();tasks.removeFirst().run();
        reload.get(5,java.util.concurrent.TimeUnit.SECONDS);assertEquals("latest",store.get(A).name());assertFalse(store.dirty());
    }
    @Test void overrideFastPathsTrackReplacementsRemovalAndReload()throws Exception{
        var path=directory.resolve("fast-path.json");var store=new ItemAppearanceStore(path);
        assertFalse(store.hasModels());assertFalse(store.hasNames());
        store.put(A,new ItemAppearanceStore.Appearance("name",""));assertTrue(store.hasNames());assertFalse(store.hasModels());
        store.put(A,new ItemAppearanceStore.Appearance("","minecraft:stick"));assertFalse(store.hasNames());assertTrue(store.hasModels());
        store.put(B,new ItemAppearanceStore.Appearance("other",""));store.save();var restored=new ItemAppearanceStore(path);restored.load();assertTrue(restored.hasModels());assertTrue(restored.hasNames());
        restored.put(A,new ItemAppearanceStore.Appearance("",""));assertFalse(restored.hasModels());assertTrue(restored.hasNames());restored.put(B,new ItemAppearanceStore.Appearance("",""));assertFalse(restored.hasNames());
    }
    private static final String A="12345678-1234-1234-1234-123456789abc",B="12345678-1234-1234-1234-123456789abd";
    @TempDir Path directory;
    @Test void savesAndReloadsSeparatePhysicalItemsAndEnabledState() throws Exception {
        var path=directory.resolve("items.json");var original=new ItemAppearanceStore(path);
        original.put(A,new ItemAppearanceStore.Appearance("&dMy Hyperion","minecraft:diamond_sword"));
        original.put(B,new ItemAppearanceStore.Appearance("Other Hyperion",""));original.enabled(false);original.save();
        var loaded=new ItemAppearanceStore(path);loaded.load();
        assertEquals(original.get(A),loaded.get(A));assertEquals(original.get(B),loaded.get(B));assertFalse(loaded.enabled());assertFalse(loaded.dirty());
        assertFalse(Files.readString(path).contains("sync")||Files.readString(path).contains("token"));
    }
    @Test void armorModelAndDyeRemainPerItemAndLegacyFilesStillLoad() throws Exception {
        var path=directory.resolve("armor.json");var store=new ItemAppearanceStore(path);
        store.put(A,new ItemAppearanceStore.Appearance("","","minecraft:leather",0x57A2E6));
        store.put(B,new ItemAppearanceStore.Appearance("Old name",""));
        assertTrue(store.hasArmorModels());assertTrue(store.hasArmorColors());store.save();
        var restored=new ItemAppearanceStore(path);restored.load();
        assertEquals("minecraft:leather",restored.get(A).armorModel());assertEquals(0x57A2E6,restored.get(A).armorColor());
        assertNull(restored.get(B).armorColor());assertEquals("",restored.get(B).armorModel());
        restored.put(A,new ItemAppearanceStore.Appearance("",""));
        assertFalse(restored.hasArmorModels());assertFalse(restored.hasArmorColors());
        assertEquals("Old name",restored.get(B).name());
    }
    @Test void shorthandModelIdsUseTheSameDefaultNamespaceAsThePreview() throws Exception {
        var path=directory.resolve("shorthand.json");var store=new ItemAppearanceStore(path);
        store.put(A,new ItemAppearanceStore.Appearance("Manual model"," golden_axe "," leather ",0x57A2E6));
        assertEquals("minecraft:golden_axe",store.get(A).model());
        assertEquals("minecraft:leather",store.get(A).armorModel());
        store.save();var restored=new ItemAppearanceStore(path);restored.load();
        assertEquals(store.get(A),restored.get(A));
        assertEquals("custom:weapons/axe",new ItemAppearanceStore.Appearance(""," custom:weapons/axe ").model());
    }
    @Test void normalizedItemUuidRejectsMalformedOrOtherIdentityFormats(){
        assertEquals(A,ItemAppearanceStore.key("12345678123412341234123456789ABC"));
        for(String value:new String[]{"", "HYPERION","minecraft:blaze_rod","1-2-3-4-5","../file",""})assertEquals("",ItemAppearanceStore.key(value));
        assertEquals("",ItemAppearanceStore.key(null));
    }
    @Test void resetOnlyRemovesTheSelectedItem() throws Exception {
        var store=new ItemAppearanceStore(directory.resolve("items.json"));
        store.put(A,new ItemAppearanceStore.Appearance("One",""));store.put(B,new ItemAppearanceStore.Appearance("Two",""));
        store.put(A,new ItemAppearanceStore.Appearance("",""));store.save();
        assertTrue(store.get(A).empty());assertEquals("Two",store.get(B).name());assertEquals(1,store.size());
    }
    @Test void invalidEntriesDoNotPreventLoadingValidEntries() throws Exception {
        var path=directory.resolve("items.json");
        Files.writeString(path,"{\"version\":1,\"items\":{\""+A+"\":{\"name\":\"Kept\",\"model\":\"minecraft:diamond_sword\"},\""+B+"\":{\"model\":\"https://example.test\"}}}");
        var store=new ItemAppearanceStore(path);store.load();assertEquals(1,store.size());assertEquals("Kept",store.get(A).name());
    }
    @Test void malformedFilesDoNotOverwriteCurrentMemoryOrDisk() throws Exception {
        var path=directory.resolve("items.json");var store=new ItemAppearanceStore(path);
        store.put(A,new ItemAppearanceStore.Appearance("Kept",""));store.save();Files.writeString(path,"not json");
        assertThrows(java.io.IOException.class,store::load);assertEquals("Kept",store.get(A).name());
        store.save();assertEquals("not json",Files.readString(path));
    }
    @Test void atomicSaveLeavesNoTemporaryFiles() throws Exception {
        var store=new ItemAppearanceStore(directory.resolve("items.json"));store.put(A,new ItemAppearanceStore.Appearance("😀中文 &dName","custom:item/model"));store.save();
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
        var loaded=new ItemAppearanceStore(directory.resolve("items.json"));loaded.load();assertEquals(store.get(A),loaded.get(A));
    }
    @Test void validationRejectsUnboundedNamesControlCharactersAndNonModelIdentifiers(){
        assertThrows(IllegalArgumentException.class,()->new ItemAppearanceStore.Appearance("a".repeat(257),""));
        assertThrows(IllegalArgumentException.class,()->new ItemAppearanceStore.Appearance("hello\ncommand",""));
        for(String id:new String[]{"https://model.test","minecraft:UPPERCASE","minecraft:model?param","../model"})
            assertThrows(IllegalArgumentException.class,()->new ItemAppearanceStore.Appearance("Name",id));
        assertDoesNotThrow(()->new ItemAppearanceStore.Appearance("&d"+"x".repeat(254),"hypixel:weapons/hyperion"));
        assertThrows(IllegalArgumentException.class,()->new ItemAppearanceStore.Appearance("","","../leather",null));
        assertThrows(IllegalArgumentException.class,()->new ItemAppearanceStore.Appearance("","","minecraft:leather",-1));
        assertThrows(IllegalArgumentException.class,()->new ItemAppearanceStore.Appearance("","","minecraft:leather",0x1000000));
    }
    @Test void aMissingFileDoesNotCreateOrChangeOtherConfiguration() throws Exception {
        var path=directory.resolve("items.json");var store=new ItemAppearanceStore(path);store.load();store.save();
        assertTrue(store.enabled());assertEquals(0,store.size());assertFalse(Files.exists(path));
    }
    @Test void changingOneItemDoesNotChangeAnotherOrExceedTheEntryLimit(){
        var store=new ItemAppearanceStore(directory.resolve("items.json"));
        for(int i=0;i<ItemAppearanceStore.MAX_ENTRIES;i++)store.put(new java.util.UUID(0,i).toString(),new ItemAppearanceStore.Appearance("Item "+i,""));
        assertThrows(IllegalStateException.class,()->store.put(A,new ItemAppearanceStore.Appearance("Overflow","")));
        store.put(new java.util.UUID(0,0).toString(),new ItemAppearanceStore.Appearance("Edited",""));assertEquals(ItemAppearanceStore.MAX_ENTRIES,store.size());
    }
}
