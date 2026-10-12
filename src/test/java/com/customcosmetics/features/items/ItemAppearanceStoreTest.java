// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ItemAppearanceStoreTest {
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
