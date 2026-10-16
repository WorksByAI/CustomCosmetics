package com.customcosmetics.features.accessories;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AccessoryOffsetTest {
    @Test void offsetsAreFiniteBoundedAndBackwardCompatible(){assertEquals(new AccessoryOffset(1,0,-1),new AccessoryOffset(4,Double.NaN,-9));assertEquals(AccessoryOffset.ZERO,AccessoriesSettings.WINGS.offset());var old=AccessoriesSettings.parse(JsonParser.parseString("{\"wings\":{\"enabled\":true}}"));assertEquals(AccessoryOffset.ZERO,old.wings().offset());}
    @Test void everyPartEditAndWireRoundTripPreserveOffset(){var offset=new AccessoryOffset(.125,-.25,.5);var p=AccessoriesSettings.WINGS.enabled(true).offset(offset).scale(1.25).color(0x334455).accent(0x665544).speed(.5).side("left").style("aether");assertEquals(offset,p.offset());var outfit=AccessoriesSettings.NONE.with("wings",p);assertEquals(offset,AccessoriesSettings.parse(outfit.json()).wings().offset());assertEquals(offset,p.enabled(false).offset());}
}
