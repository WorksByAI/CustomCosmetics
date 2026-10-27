// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RestoredUndoTest {
    private static MapEdits.Edit edit(int x) { return new MapEdits.Edit(new MapEdits.Position(x,70,0),"minecraft:gold_block",true); }

    @Test void automaticRoomsDoNotConsumeUndoOrDisappearWhenUndoingUserEdits() {
        var data=new MapEdits();var user=edit(1);var room=edit(10);
        data.put(user);
        data.restore(Map.of(user.position(),user,room.position(),room));
        assertEquals(Set.of(user.position()),data.undoPositions());
        assertTrue(data.undo());assertEquals(Map.of(room.position(),room),data.snapshot());
        assertFalse(data.undo());
        data.remove(room.position());
        var other=edit(20);data.restore(Map.of(other.position(),other));
        assertTrue(data.undo());assertEquals(Map.of(room.position(),room,other.position(),other),data.snapshot());
    }

    @Test void batchUndoPreservesLaterRestorationAndCanBePreparedWithoutMutatingLiveData() {
        var data=new MapEdits();var old=edit(1);data.put(old);
        var replacement=new MapEdits.Edit(old.position(),"minecraft:stone",true);
        data.replace(Map.of(replacement.position(),replacement));
        var room=edit(10);data.restore(Map.of(replacement.position(),replacement,room.position(),room));
        var prepare=data.undoTarget();var target=prepare.get();
        assertEquals(replacement,data.snapshot().get(old.position()));
        data.finishUndo(target);assertEquals(Map.of(old.position(),old,room.position(),room),data.snapshot());
        assertTrue(data.undo());assertEquals(Map.of(room.position(),room),data.snapshot());
    }

    @Test void sparseUndoOnAMillionRestoredBlocksKeepsTheBackingSnapshot() {
        var data=new MapEdits();var user=edit(-1);data.put(user);
        var restored=new HashMap<MapEdits.Position,MapEdits.Edit>();
        for(int i=0;i<1_000_000;i++){var e=edit(i);restored.put(e.position(),e);}
        restored.put(user.position(),user);data.restore(restored);
        var base=data.snapshot();assertTrue(data.undo());
        assertEquals(1_000_000,data.snapshot().size());assertFalse(data.snapshot().containsKey(user.position()));
        assertEquals(Set.of(user.position()),MapEdits.differences(base,data.snapshot()));
        assertEquals(edit(999_999),data.snapshot().get(edit(999_999).position()));
    }
}
