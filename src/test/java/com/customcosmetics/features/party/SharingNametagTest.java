package com.customcosmetics.features.party;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class SharingNametagTest {
    @Test void disabledSharingDoesNotReadTheWorldOrCopyNameComponents(){
        int old=com.customcosmetics.config.ConfigManager.disabledModules;
        try{com.customcosmetics.config.ConfigManager.disabledModules=-1;assertDoesNotThrow(()->SharingNametag.remember(null,null,null));}
        finally{com.customcosmetics.config.ConfigManager.disabledModules=old;}
    }
    @Test void preservesFormattedTextAndCopiesCachedComponents(){
        var cache=new SharingNametag.Cache(2);var id=UUID.randomUUID();var world=new Object();cache.world(world);
        var text=Component.literal("[483] ").withStyle(ChatFormatting.DARK_RED).append(Component.literal("ToryHi ♣").withStyle(ChatFormatting.AQUA));
        cache.put(id,text);assertEquals(text,cache.get(id));text.append(" changed");assertEquals("[483] ToryHi ♣",cache.get(id).getString());
        assertEquals(0xAA0000,cache.get(id).getStyle().getColor().getValue());
        cache.world(world);assertNotNull(cache.get(id));cache.world(new Object());assertNull(cache.get(id));
    }
    @Test void cacheIsBoundedAndResetsOnDisconnect(){
        var cache=new SharingNametag.Cache(2);var first=UUID.randomUUID();var second=UUID.randomUUID();var third=UUID.randomUUID();
        var world=new Object();cache.world(world);cache.put(first,Component.literal("First"));cache.put(second,Component.literal("Second"));cache.get(first);cache.put(third,Component.literal("Third"));
        assertEquals(2,cache.size());assertNull(cache.get(second));assertNotNull(cache.get(first));
        cache.put(UUID.randomUUID(),Component.literal("a".repeat(257)));assertEquals(2,cache.size());cache.world(null);assertEquals(0,cache.size());
        assertEquals("simple",SharingNametag.normalize(null));assertEquals("simple",SharingNametag.normalize("bad"));assertEquals("vanilla",SharingNametag.normalize("vanilla"));
    }
}
