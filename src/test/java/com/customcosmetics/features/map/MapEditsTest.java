package com.customcosmetics.features.map;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MapEditsTest {
    @Test void minedRealBlocksNeverBecomeLegitGhosts(){var real=new MapEdits.Edit(new MapEdits.Position(1,2,3),"minecraft:stone",false);assertFalse(real.ghost(true));assertFalse(real.ghost(false));assertTrue(new MapEdits.Edit(real.position(),real.material(),true).ghost(true));assertFalse(new MapEdits.Edit(real.position(),"minecraft:air",true).ghost(true));}
    @Test void roomPresetsRotateAroundAnchorAndUndoIsAtomic(){var data=new MapEdits();var origin=new MapEdits.Position(100,0,200);var edit=new MapEdits.Edit(new MapEdits.Position(101,70,202),"minecraft:gold_block",false);data.put(edit);var preset=data.preset("Test room",origin,"hash");assertEquals(new MapEdits.Position(-2,70,1),preset.edits().getFirst().position().rotate(1));assertEquals(new MapEdits.Position(8,70,21),preset.place(new MapEdits.Position(10,0,20),1).values().iterator().next().position());data.clear();assertTrue(data.undo());assertEquals(Map.of(edit.position(),edit),data.snapshot());assertThrows(UnsupportedOperationException.class,()->data.snapshot().clear());}
    @Test void invalidPresetsAndUnboundedEditsAreRejected(){assertThrows(IllegalArgumentException.class,()->new MapEdits.Preset("../bad/name",List.of(),""));assertThrows(IllegalArgumentException.class,()->new MapEdits.Edit(new MapEdits.Position(0,0,0),"../asset",false));assertEquals(new MapEdits.Position(1,2,3),new MapEdits.Position(1,2,3).rotate(4));}
    @Test void materialUvRemapPreservesFractionalSlabTextureCoordinates(){assertEquals(.675f,MaterialModel.remap(.25f,.1f,.3f,.6f,.7f),1e-6);assertEquals(.6f,MaterialModel.remap(.1f,.1f,.3f,.6f,.7f),1e-6);}
}
