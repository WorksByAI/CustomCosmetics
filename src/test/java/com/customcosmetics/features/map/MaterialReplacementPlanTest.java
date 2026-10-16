package com.customcosmetics.features.map;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MaterialReplacementPlanTest {
    private static MapEdits.Position p(int x){return new MapEdits.Position(x,66,0);}
    @Test void matchesOriginalTypesAndPreservesOtherEditsAndOrigins(){
        var existing=new MapEdits.Edit(p(0),"minecraft:gold_block",false);var ghost=new MapEdits.Edit(p(2),"minecraft:quartz_block",true);var before=Map.of(p(0),existing,p(2),ghost);
        var plan=new MaterialReplacementPlan(before,"minecraft:quartz_block","minecraft:dark_oak_planks");plan.accept(p(0),"minecraft:quartz_block");plan.accept(p(1),"minecraft:quartz_block");plan.accept(p(2),"minecraft:air");plan.accept(p(3),"minecraft:quartz_stairs");plan.accept(p(0),"minecraft:quartz_block");
        assertEquals(2,plan.changed());assertEquals(2,plan.matched());assertEquals(3,plan.result().size());assertEquals(ghost,plan.result().get(p(2)));assertFalse(plan.result().get(p(1)).originallyAir());assertEquals(existing,before.get(p(0)));assertThrows(UnsupportedOperationException.class,()->plan.result().clear());
    }
    @Test void wholeBatchUsesOneUndoAndNoOpDoesNotCreateEdits(){
        var data=new MapEdits();data.put(new MapEdits.Edit(p(0),"minecraft:gold_block",false));var before=data.snapshot();var plan=new MaterialReplacementPlan(before,"minecraft:quartz_block","minecraft:dark_oak_planks");for(int x=0;x<40;x++)plan.accept(p(x),"minecraft:quartz_block");data.replace(plan.result());assertEquals(40,data.snapshot().size());data.undo();assertEquals(before,data.snapshot());
        var same=new MaterialReplacementPlan(Map.of(p(0),new MapEdits.Edit(p(0),"minecraft:dark_oak_planks",true)),"minecraft:quartz_block","minecraft:dark_oak_planks");same.accept(p(0),"minecraft:quartz_block");assertEquals(0,same.changed());assertTrue(same.result().get(p(0)).originallyAir());
    }
    @Test void oversizedReplacementCannotAlterExistingDataOrUndo(){
        var data=new MapEdits();var before=data.snapshot();var plan=new MaterialReplacementPlan(before,"minecraft:quartz_block","minecraft:dark_oak_planks");for(int x=0;x<MapEdits.LIMIT;x++)plan.accept(p(x),"minecraft:quartz_block");assertThrows(IllegalArgumentException.class,()->plan.accept(p(MapEdits.LIMIT),"minecraft:quartz_block"));assertEquals(before,data.snapshot());assertFalse(data.undo());
    }
    @Test void identicalSourceAndTargetDoNotFillTheEditLimit(){var plan=new MaterialReplacementPlan(Map.of(),"minecraft:stone","minecraft:stone");for(int x=0;x<MapEdits.LIMIT+100;x++)plan.accept(p(x),"minecraft:stone");assertEquals(0,plan.changed());assertTrue(plan.result().isEmpty());assertEquals(MapEdits.LIMIT+100,plan.matched());}
    @Test void airCannotBeTheSourceAndSelectionCornersAreInclusive(){
        assertThrows(IllegalArgumentException.class,()->new MaterialReplacementPlan(Map.of(),"minecraft:air","minecraft:stone"));assertThrows(IllegalArgumentException.class,()->new MaterialReplacementPlan(Map.of(),"minecraft:quartz_pillar[axis=x]","minecraft:stone"));
        var box=MaterialReplacementPlan.Bounds.between(new MapEdits.Position(2,70,3),new MapEdits.Position(-2,60,-3));assertEquals(new MapEdits.Position(-2,60,-3),box.min());assertTrue(box.column(2,-3));assertTrue(box.column(-2,3));assertFalse(box.column(3,0));
    }
    @Test void preflightRoomChangesDoNotModifyTheSavedLibrary(){
        var rooms=new RoomLayouts();var frame=new RoomLayouts.Frame("floor:F7","Boss",p(0),0);rooms.change(frame,p(0),"minecraft:gold_block");var candidate=rooms.copy();candidate.changeAll(frame,Map.of(p(0),Optional.of("minecraft:dark_oak_planks"),p(1),Optional.of("minecraft:dark_oak_planks")));
        assertEquals(1,rooms.edits(frame.key()).size());assertEquals("minecraft:gold_block",rooms.edits(frame.key()).get(frame.relative(p(0))));assertEquals(2,candidate.edits(frame.key()).size());
    }
}
