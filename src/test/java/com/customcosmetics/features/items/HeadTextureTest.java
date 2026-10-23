// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HeadTextureTest {
    private static final String HASH="8b115cdc45fd84f21fba7ac0f2bc77bc3b6302be670842f6e11f66eb557f13ee";
    @TempDir Path directory;
    @Test void textureHashUrlAndBase64ReferToTheSameSkin(){
        assertEquals(HASH,HeadTexture.normalize(" "+HASH.toUpperCase(Locale.ROOT)+" "));
        assertEquals(HASH,HeadTexture.normalize("http://textures.minecraft.net/texture/"+HASH));
        assertEquals(HASH,HeadTexture.normalize(HeadTexture.property(HASH)));
        for(String value:List.of("https://example.test/texture/"+HASH,"https://textures.minecraft.net.evil.test/texture/"+HASH,
                "file:///skin.png","https://textures.minecraft.net/texture/../file","not a skin","a".repeat(8193)))
            assertThrows(IllegalArgumentException.class,()->HeadTexture.normalize(value));
    }
    @Test void onlineCatalogReadsSignedApiSkinObjectsAndSkipsNonHeadAndMalformedEntries(){
        String json="{\"success\":true,\"items\":["+
            "{\"id\":\"DIAMOND_NECRON_HEAD\",\"name\":\"§6Diamond Necron Head\",\"material\":\"SKULL_ITEM\",\"skin\":{\"value\":\""+HeadTexture.property(HASH)+"\",\"signature\":\"unused\"}},"+
            "{\"id\":\"GOLD_NECRON_HEAD\",\"name\":\"Golden Necron Head\",\"material\":\"PLAYER_HEAD\",\"skin\":\""+HASH+"\"},"+
            "{\"id\":\"SWORD\",\"name\":\"Sword\",\"material\":\"IRON_SWORD\",\"skin\":\""+HASH+"\"},"+
            "{\"id\":\"BROKEN\",\"name\":\"Broken\",\"material\":\"PLAYER_HEAD\",\"skin\":\"invalid\"}]}";
        var entries=HeadSkinCatalog.parse(json);
        assertEquals(List.of("DIAMOND_NECRON_HEAD","GOLD_NECRON_HEAD"),entries.stream().map(HeadSkinCatalog.Entry::id).toList());
        assertEquals("Diamond Necron Head",entries.getFirst().name());
        assertEquals(HASH,entries.getFirst().texture());
        assertThrows(IllegalArgumentException.class,()->HeadSkinCatalog.parse("{\"success\":false,\"items\":[]}"));
    }
    @Test void savedSkinIsPerItemAndSurvivesMissingCatalogAndReset()throws Exception{
        String a="12345678-1234-1234-1234-123456789abc",b="12345678-1234-1234-1234-123456789abd";
        Path path=directory.resolve("items.json");var store=new ItemAppearanceStore(path);
        store.put(a,new ItemAppearanceStore.Appearance("My helmet","","",null,HeadTexture.property(HASH)));
        store.put(b,new ItemAppearanceStore.Appearance("Other item","minecraft:stick"));
        assertTrue(store.hasHeadTextures());store.save();
        var loaded=new ItemAppearanceStore(path);loaded.load();
        assertEquals(HASH,loaded.get(a).headTexture());assertEquals("",loaded.get(b).headTexture());
        assertTrue(loaded.hasHeadTextures());assertFalse(loaded.get(a).empty());
        loaded.put(a,new ItemAppearanceStore.Appearance("",""));
        assertFalse(loaded.hasHeadTextures());assertEquals("minecraft:stick",loaded.get(b).model());
    }
}
