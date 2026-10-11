package com.customcosmetics.features.blocking;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockHandTest {
    @Test void onlyHeldSwordsProducePosesAndMainHandWins() {
        assertEquals(BlockHand.MAIN, choose(true, true));
        assertEquals(BlockHand.MAIN, choose(true, false));
        assertEquals(BlockHand.OFF, choose(false, true));
        assertEquals(BlockHand.NONE, choose(false, false));
    }
    @Test void releaseMenusFocusDeathSpectatorsAndOtherUseStopThePose() {
        boolean[] active = {true, true, false, true, true, false, true, false};
        for (int i = 0; i < active.length; i++) {
            boolean[] flags = active.clone(); flags[i] = !flags[i];
            assertEquals(BlockHand.NONE, BlockHand.choose(flags[0], flags[1], flags[2], flags[3],
                flags[4], flags[5], flags[6], flags[7], true, true), "Eligibility flag " + i);
        }
    }
    @Test void wireValuesHaveNoArbitraryActionSupport() {
        for (BlockHand hand : BlockHand.values()) assertEquals(hand, BlockHand.fromWire(hand.wire));
        assertEquals(BlockHand.NONE, BlockHand.fromWire("attack"));
    }
    private static BlockHand choose(boolean main, boolean off) {
        return BlockHand.choose(true, true, false, true, true, false, true, false, main, off);
    }
}
