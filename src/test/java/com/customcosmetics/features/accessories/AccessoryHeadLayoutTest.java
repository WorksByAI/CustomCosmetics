// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AccessoryHeadLayoutTest {
    @Test void sideEarsStayAtEarHeightAndClearTheHelmet(){
        var settings=AccessoriesSettings.NONE.with("ears",AccessoriesSettings.EARS.enabled(true).style("elf"));
        var bounds=new AccessoryGeometry.Bounds(-2,-3,-1,5,2,1);java.util.function.Function<AccessoryCatalog.Style,AccessoryGeometry.Bounds> geometry=s->bounds;
        assertEquals(.25,AccessoryHeadLayout.earHeight(settings,true,geometry),1e-6);
        var rotated=AccessoryPlacement.rotated(bounds,0,0,.13f);
        assertTrue(AccessoryHeadLayout.earX(settings,true,geometry)+rotated.minX()/16>=.32+AccessoryPlacement.GAP-1e-6);
    }
    private static AccessoryGeometry.Bounds geometry(AccessoryCatalog.Style style){return switch(style.kind()){case "hat"->new AccessoryGeometry.Bounds(-7,-11,-7,7,1,7);case "ears"->new AccessoryGeometry.Bounds(-3,-10.5f,-2,3,1,2);default->new AccessoryGeometry.Bounds(-6,-1,-6,6,2,6);};}
    @Test void enlargedWitchHatLeavesRoomForTheEntireDoubleHalo(){var settings=AccessoriesSettings.NONE.with("hat",AccessoriesSettings.NONE.part("hat").enabled(true).style("witch").scale(2)).with("halo",AccessoriesSettings.HALO.enabled(true).style("double").scale(2));double center=AccessoryHeadLayout.haloHeight(settings,AccessoryHeadLayoutTest::geometry);assertTrue(center-.25>.52+22.0/16);}
    @Test void disabledDraftsDoNotRaiseHaloAndTallEarsAreMeasuredInsteadOfHardcoded(){assertTrue(AccessoryHeadLayout.haloHeight(AccessoriesSettings.NONE,AccessoryHeadLayoutTest::geometry)-2.0/16>.525);var draft=AccessoriesSettings.NONE.with("ears",AccessoriesSettings.EARS.scale(2).style("rabbit"));assertEquals(AccessoryHeadLayout.haloHeight(AccessoriesSettings.NONE,AccessoryHeadLayoutTest::geometry),AccessoryHeadLayout.haloHeight(draft,AccessoryHeadLayoutTest::geometry));var worn=draft.with("ears",draft.ears().enabled(true));assertTrue(AccessoryHeadLayout.haloHeight(worn,AccessoryHeadLayoutTest::geometry)>1.9);}
}
