// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NicknameSegmentsTest {
    @Test void eachSegmentStartsAndEndsItsOwnGradientWithoutBlendingAcrossTheBoundary() {
        var segments=List.of(new NicknameSegments.Segment(1,4,0xED69E8,0x916BE8),new NicknameSegments.Segment(5,6,0x43D6EC,0x549FF5));
        var paints=NicknameSegments.paints(6,0,0,segments);
        assertEquals(0xED69E8,paints.get(0).color(0));assertEquals(0x916BE8,paints.get(3).color(0));
        assertEquals(0x43D6EC,paints.get(4).color(0));assertEquals(0x549FF5,paints.get(5).color(0));
        assertSame(paints.get(0).gradient(),paints.get(3).gradient());assertSame(paints.get(4).gradient(),paints.get(5).gradient());
        assertNotSame(paints.get(3).gradient(),paints.get(4).gradient());
        assertEquals(0,paints.get(4).from());assertEquals(1,paints.get(5).to());
        assertEquals(paints.get(2).color(0),paints.get(2).color(123456));
    }
    @Test void shorterNamesClipRangesWhileSingleCharactersAndGapsRemainDefined() {
        var segments=List.of(new NicknameSegments.Segment(2,20,0xFF0000,0x0000FF),new NicknameSegments.Segment(30,35,0,0));
        var paints=NicknameSegments.paints(4,0x00FF00,0xFFFFFF,segments);
        assertEquals(0x00FF00,paints.getFirst().color(0));assertEquals(0xFF0000,paints.get(1).color(0));assertEquals(0x0000FF,paints.getLast().color(0));
        assertEquals(0xFF0000,NicknameSegments.paints(2,0,0,segments).getLast().color(0));
        assertEquals(0x123456,NicknameSegments.paints(1,0x123456,0xFFFFFF,List.of()).getFirst().color(0));
        assertTrue(NicknameSegments.paints(0,0,0,segments).isEmpty());
    }
    @Test void validationRejectsOverlapAndInvalidRangesAndDoesNotMutateTheDraft() {
        var a=new NicknameSegments.Segment(1,3,0xFF123456,-1);var b=new NicknameSegments.Segment(4,6,1,2);
        assertEquals(0x123456,a.start());assertEquals(0xFFFFFF,a.end());
        var input=new ArrayList<>(List.of(b,a));var sorted=NicknameSegments.validate(input);
        assertEquals(List.of(a,b),sorted);assertEquals(List.of(b,a),input);assertThrows(UnsupportedOperationException.class,()->sorted.add(a));
        assertThrows(IllegalArgumentException.class,()->NicknameSegments.validate(List.of(a,new NicknameSegments.Segment(3,4,0,0))));
        assertThrows(IllegalArgumentException.class,()->new NicknameSegments.Segment(0,2,0,0));
        assertThrows(IllegalArgumentException.class,()->new NicknameSegments.Segment(4,3,0,0));
        assertThrows(IllegalArgumentException.class,()->new NicknameSegments.Segment(1,4097,0,0));
        assertThrows(IllegalArgumentException.class,()->NicknameSegments.validate(Collections.nCopies(17,a)));
        assertEquals(List.of(a,b),NicknameSegments.normalize(Arrays.asList(b,null,a,new NicknameSegments.Segment(2,4,0,0))));
    }
    @Test void wireDataRoundTripsAndRejectsFractionalStringOverlappingAndMalformedEntries() {
        var expected=List.of(new NicknameSegments.Segment(1,2,0x123ABC,0x000001),new NicknameSegments.Segment(3,6,0x123456,0x654321));
        assertEquals(expected,NicknameSegments.fromJson(NicknameSegments.toJson(expected)));
        var json=JsonParser.parseString("""
            [{"from":3,"to":6,"start":"#123456","end":"#654321"},
             {"from":1,"to":2,"start":"#123abc","end":"#000001"},
             {"from":2,"to":3,"start":"#000000","end":"#FFFFFF"},
             {"from":1.5,"to":2,"start":"#000000","end":"#FFFFFF"},
             {"from":"7","to":8,"start":"#000000","end":"#FFFFFF"},
             {"from":7,"to":8,"start":"red","end":"#FFFFFF"},null,{},
             {"from":7,"to":4097,"start":"#000000","end":"#FFFFFF"}]
            """);
        assertEquals(expected,NicknameSegments.fromJson(json));assertTrue(NicknameSegments.fromJson(JsonParser.parseString("{}" )).isEmpty());
        var many=new com.google.gson.JsonArray();for(int i=1;i<=20;i++)many.add(NicknameSegments.toJson(List.of(new NicknameSegments.Segment(i,i,0,0))).get(0));
        assertEquals(16,NicknameSegments.fromJson(many).size());
    }
}
