// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import com.google.gson.JsonParser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessoriesSettingsTest {
    @Test void renderingReusesImmutableDraftsWithoutChangingSyncNormalization(){
        var self=UUID.randomUUID();var other=UUID.randomUUID();
        var draft=AccessoriesSettings.NONE.with("wings",AccessoriesSettings.WINGS.scale(2).color(0));
        assertSame(draft,AccessoriesSettings.chooseForRendering(self,self,false,draft,AccessoriesSettings.NONE));
        assertSame(draft,AccessoriesSettings.chooseForRendering(self,other,false,AccessoriesSettings.NONE,draft));
        assertFalse(draft.any());assertEquals(AccessoriesSettings.NONE.json(),draft.json());
        assertEquals(AccessoriesSettings.NONE,AccessoriesSettings.choose(self,self,false,draft,null));
        assertSame(AccessoriesSettings.NONE,AccessoriesSettings.chooseForRendering(self,other,true,draft,draft));
        assertSame(AccessoriesSettings.NONE,AccessoriesSettings.chooseForRendering(self,null,false,draft,draft));
    }
    @Test void everyOriginalStyleRoundTripsAndUnknownModelOrSlotCannotReachTheRenderer(){
        assertEquals(17,AccessoryCatalog.slots().size());assertEquals(123,AccessoryCatalog.count());
        var keys=new java.util.HashSet<String>();
        for(var slot:AccessoryCatalog.slots())for(var style:slot.styles()){
            assertTrue(keys.add(slot.id()+":"+style.id()));
            var settings=AccessoriesSettings.NONE.with(slot.id(),AccessoriesSettings.NONE.part(slot.id()).enabled(true).style(style.id()).color(style.color()).accent(style.accent()));
            assertEquals(settings,AccessoriesSettings.parse(settings.json()));assertEquals(style.id(),settings.part(slot.id()).style());
        }
        var hostile=new AccessoriesSettings(AccessoriesSettings.WINGS.enabled(true).style("../../private"),null,null,java.util.Map.of("unknown",AccessoriesSettings.WINGS.enabled(true)));
        assertEquals("feather",hostile.wings().style());assertFalse(hostile.extra().containsKey("unknown"));
        assertThrows(IllegalArgumentException.class,()->hostile.with("unknown",AccessoriesSettings.WINGS));
        var all=AccessoriesSettings.NONE;for(var slot:AccessoryCatalog.slots())all=all.with(slot.id(),all.part(slot.id()).enabled(true));
        assertTrue(all.json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<3000);
    }
    @Test void combinationsColorsAndSizesRoundTripWithoutEnablingOtherParts(){
        var settings=AccessoriesSettings.NONE.with("wings",AccessoriesSettings.WINGS.enabled(true).scale(1.5).color(0x123456).accent(0xABCDEF).speed(.5));
        assertEquals(settings,AccessoriesSettings.parse(settings.json()));assertTrue(settings.any());assertFalse(settings.halo().enabled());assertFalse(settings.ears().enabled());
        var combined=settings.with("halo",AccessoriesSettings.HALO.enabled(true)).with("ears",AccessoriesSettings.EARS.enabled(true));
        assertEquals(combined,AccessoriesSettings.parse(combined.json()));assertEquals("#123456",AccessoriesSettings.hex(combined.wings().color()));
    }
    @Test void onlySelfUsesLocalSettingsAndOfflineAlwaysHidesRemoteParts(){
        var self=UUID.randomUUID();var other=UUID.randomUUID();var local=AccessoriesSettings.NONE.with("ears",AccessoriesSettings.EARS.enabled(true));var remote=AccessoriesSettings.NONE.with("wings",AccessoriesSettings.WINGS.enabled(true));
        assertEquals(local,AccessoriesSettings.choose(self,self,true,local,remote));assertEquals(remote,AccessoriesSettings.choose(self,other,false,local,remote));
        assertEquals(AccessoriesSettings.NONE,AccessoriesSettings.choose(self,other,true,local,remote));assertEquals(AccessoriesSettings.NONE,AccessoriesSettings.choose(self,null,false,local,remote));
    }
    @Test void invalidRemoteValuesAreBoundedAndDisabledDraftsDoNotTriggerSync(){
        var draft=AccessoriesSettings.NONE.with("wings",AccessoriesSettings.WINGS.scale(2).color(0));assertEquals(AccessoriesSettings.NONE.json(),draft.json());
        var parsed=AccessoriesSettings.parse(JsonParser.parseString("{\"wings\":{\"enabled\":true,\"scale\":99,\"color\":\"#0055aa\",\"accent\":123,\"speed\":-9},\"halo\":{\"enabled\":\"true\"}}"));
        assertEquals(2,parsed.wings().scale());assertEquals(0,parsed.wings().speed());assertEquals(0x0055AA,parsed.wings().color());assertEquals(AccessoriesSettings.WINGS.accent(),parsed.wings().accent());assertFalse(parsed.halo().enabled());
        assertEquals(AccessoriesSettings.NONE,AccessoriesSettings.parse(JsonParser.parseString("[]")));
        assertEquals(1,new AccessoriesSettings.Part(true,Double.NaN,0,0,Double.POSITIVE_INFINITY).scale());
    }
}
