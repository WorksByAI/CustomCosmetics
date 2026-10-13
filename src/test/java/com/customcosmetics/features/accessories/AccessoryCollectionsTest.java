// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AccessoryCollectionsTest {
    @Test void bothCollectionsAreDisjointAndTogetherCoverTheEntireCatalog(){
        assertEquals(96,AccessoryCatalog.count(AccessoryCatalog.Collection.PIXEL));assertEquals(26,AccessoryCatalog.count(AccessoryCatalog.Collection.DETAILED));
        assertEquals(17,AccessoryCatalog.slots(AccessoryCatalog.Collection.PIXEL).size());assertEquals(10,AccessoryCatalog.slots(AccessoryCatalog.Collection.DETAILED).size());
        for(var collection:AccessoryCatalog.Collection.values())for(var slot:AccessoryCatalog.slots(collection)){assertFalse(slot.styles().isEmpty());for(var style:slot.styles())assertEquals(collection,style.collection());}
    }
    @Test void allPreviousPixelStylesKeepTheirIdsShapesVariantsAndColors()throws Exception{
        try(var input=getClass().getResourceAsStream("/accessories/pixel-catalog-v1.json")){
            assertNotNull(input);var root=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var element:root.getAsJsonArray("slots")){var slot=element.getAsJsonObject();for(var value:slot.getAsJsonArray("styles")){var previous=value.getAsJsonObject();var current=AccessoryCatalog.style(slot.get("id").getAsString(),previous.get("id").getAsString());assertEquals(AccessoryCatalog.Collection.PIXEL,current.collection());assertEquals(previous.get("kind").getAsString(),current.kind());assertEquals(previous.get("variant").getAsInt(),current.variant());assertEquals(previous.get("label").getAsString(),current.label());assertEquals(previous.get("color").getAsString(),AccessoriesSettings.hex(current.color()));assertEquals(previous.get("accent").getAsString(),AccessoriesSettings.hex(current.accent()));}}
        }
    }
    @Test void mixedPixelAndDetailedPartsRoundTripWithoutACollectionWideRenderingSwitch(){var value=AccessoriesSettings.NONE.with("wings",AccessoriesSettings.WINGS.enabled(true).style("aether")).with("ears",AccessoriesSettings.EARS.enabled(true).style("cat"));assertEquals(value,AccessoriesSettings.parse(value.json()));assertEquals(AccessoryCatalog.Collection.DETAILED,AccessoryCatalog.style("wings",value.wings().style()).collection());assertEquals(AccessoryCatalog.Collection.PIXEL,AccessoryCatalog.style("ears",value.ears().style()).collection());}
}
