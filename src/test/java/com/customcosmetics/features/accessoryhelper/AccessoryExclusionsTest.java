package com.customcosmetics.features.accessoryhelper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AccessoryExclusionsTest {
    @Test void ignoredRepositoryEntriesNeverAppearInAnyMissingFilter(){
        var catalog=AccessoryCatalog.parse("{\"ARTIFACT_OF_SPACE\":{},\"ETERNAL_CRYSTAL\":{},\"SPEED_TALISMAN\":{}}");
        var bag=new AccessoryBagIndex(catalog);bag.update("test",1,1,Set.of());
        var exclusions=AccessoryExclusions.parse("{\"ignored_talisman\":[\"ARTIFACT_OF_SPACE\",\"ETERNAL_CRYSTAL\"]}");
        for(boolean all:new boolean[]{false,true})for(var filter:new MissingAccessories.Filter[]{MissingAccessories.Filter.MISSING,MissingAccessories.Filter.ALL})
            assertEquals(List.of("SPEED_TALISMAN"),MissingAccessories.list(catalog,bag.view("test"),filter,all,exclusions.ignored()).stream().map(AccessoryCatalog.Entry::id).toList());
        assertEquals(AccessoryCatalog.Status.MISSING,catalog.report("SPEED_TALISMAN",Map.of()).status());
        assertNotNull(catalog.get("ETERNAL_CRYSTAL"),"Exclusions belong to the new panel, not the existing hover catalogue");
    }
    @Test void missingOrMalformedRepositoryDataIsNotAnEmptyVerifiedBlacklist(){
        assertThrows(IllegalArgumentException.class,()->AccessoryExclusions.parse("{}"));
        assertThrows(IllegalArgumentException.class,()->AccessoryExclusions.parse("{\"ignored_talisman\":[123]}"));
        var data=AccessoryExclusions.parse("{\"ignored_talisman\":[\"FUTURE_ADMIN_TALISMAN\"]}");assertTrue(data.ready());assertTrue(data.ignored().contains("FUTURE_ADMIN_TALISMAN"));assertThrows(UnsupportedOperationException.class,()->data.ignored().clear());
    }
}
