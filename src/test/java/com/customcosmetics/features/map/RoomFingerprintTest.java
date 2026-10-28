package com.customcosmetics.features.map;
import org.junit.jupiter.api.Test;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
class RoomFingerprintTest {
    @Test void allRotationsRetainExactHashesWhileSamplingTheWorldOnce(){
        var origin=new MapEdits.Position(100,0,-200);var reads=new java.util.concurrent.atomic.AtomicInteger();
        Function<MapEdits.Position,String> room=p->p.x()<origin.x()&&p.z()>origin.z()&&p.y()<100?"minecraft:gold_block":"minecraft:stone";
        String[] hashes=RoomFingerprint.rotations(origin,p->{reads.incrementAndGet();return room.apply(p);});
        assertEquals(819,reads.get());for(int turns=0;turns<4;turns++)assertEquals(RoomFingerprint.compute(origin,turns,room),hashes[turns]);
        assertNotEquals(hashes[0],hashes[1]);
    }
    @Test void unloadedOrEmptyRotationsNeverInventARecognizedRoom(){
        var origin=new MapEdits.Position(0,0,0);var reads=new java.util.concurrent.atomic.AtomicInteger();
        assertArrayEquals(new String[]{"","","",""},RoomFingerprint.rotations(origin,p->{reads.incrementAndGet();return null;}));assertEquals(1,reads.get());
        assertArrayEquals(new String[]{"","","",""},RoomFingerprint.rotations(origin,p->"minecraft:air"));
    }
    @Test void rotatedTranslatedRoomsMatchCanonicalSignature(){var zero=new MapEdits.Position(0,0,0);Function<MapEdits.Position,String> room=p->p.x()==-8&&p.z()==8&&p.y()<100?"minecraft:gold_block":"minecraft:stone";String expected=RoomFingerprint.compute(zero,0,room);var origin=new MapEdits.Position(100,0,-200);for(int rotation=0;rotation<4;rotation++){int turns=rotation;assertEquals(expected,RoomFingerprint.compute(origin,turns,p->room.apply(p.subtract(origin).rotate(-turns))));}assertNotEquals(expected,RoomFingerprint.compute(zero,0,p->"minecraft:stone"));}
    @Test void unloadedAndMostlyAirRoomsCannotBeRecognized(){var origin=new MapEdits.Position(0,0,0);assertEquals("",RoomFingerprint.compute(origin,0,p->null));assertEquals("",RoomFingerprint.compute(origin,0,p->"minecraft:air"));assertEquals("",RoomFingerprint.compute(origin,0,p->p.y()==51&&p.x()==0?"minecraft:stone":"minecraft:air"));}
}
