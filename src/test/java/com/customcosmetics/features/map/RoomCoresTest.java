package com.customcosmetics.features.map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RoomCoresTest {
    @Test void columnMatchesLegacySkipAndBedrockRules(){
        String expected="0".repeat(40)+"1"+"0".repeat(28)+"77"+"0".repeat(55);
        assertEquals(expected.hashCode(),RoomCores.hash(y->switch(y){case 100->1;case 71->5;case 70->54;case 69->146;case 68,67->7;case 12->42;default->0;}));
    }
    @Test void directionTracksAllQuarterTurns(){for(int turn=0;turn<4;turn++){final int t=turn;assertEquals(turn,RoomCores.direction(distance->{int[] hashes={9,9,9,9};if(distance==3)hashes[t]=-123;return hashes;}));}}
    @Test void unknownLegacyBlocksAreNeverTreatedAsAir(){assertThrows(IllegalArgumentException.class,()->RoomCores.hash(y->y==100?-1:0));assertEquals(0,RoomCores.direction(d->new int[]{7,7,7,7}));}
}
