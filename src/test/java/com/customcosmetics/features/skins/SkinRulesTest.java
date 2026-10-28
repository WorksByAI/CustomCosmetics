package com.customcosmetics.features.skins;
import com.customcosmetics.config.PlayerBlocklist;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkinRulesTest {
    @Test void stableUuidAndOfflineAccountRulesDoNotConfuseRenamedPlayers(){
        var a=UUID.randomUUID();var b=UUID.randomUUID();var blocks=new SkinRules.Blocks();
        var entries=List.of(new PlayerBlocklist.Entry(a,"OldName"),new PlayerBlocklist.Entry(null,"OfflineFriend"));
        assertTrue(blocks.contains(entries,a,"Renamed"));assertFalse(blocks.contains(entries,b,"OldName"));
        assertTrue(blocks.contains(entries,b,"offlinefriend"));assertFalse(blocks.contains(entries,b,"SomeoneElse"));
        assertFalse(blocks.contains(entries,null,null));
    }
    @Test void changingTheImmutableConfigurationInvalidatesTheIndex(){
        var id=UUID.randomUUID();var blocks=new SkinRules.Blocks();var entries=List.of(new PlayerBlocklist.Entry(id,"Alice"));
        for(int i=0;i<10000;i++)assertTrue(blocks.contains(entries,id,"Alice"));
        assertFalse(blocks.contains(List.of(),id,"Alice"));assertFalse(blocks.contains(List.of(new PlayerBlocklist.Entry(null,"Bob")),id,"Alice"));
        assertTrue(blocks.contains(entries,id,"NewName"));
    }
    @Test void modesAreGlobalAndInvalidValuesFallBackToOriginal(){
        for(String value:List.of("original","steve","alex","custom"))assertEquals(value,SkinRules.mode(value));
        assertEquals("original",SkinRules.mode(null));assertEquals("original",SkinRules.mode("https://example.test/skin"));
    }
}
