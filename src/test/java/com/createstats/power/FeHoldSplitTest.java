package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeHoldSplitTest {
    @Test
    @DisplayName("each peer is asked for its own critical share and the board keeps the rest (KI-64)")
    void sharesFollowDeclaredCritical() {
        final int[] shares = FeSupplyOutlook.splitHold(1_200_000, 400, new int[] {100, 100});
        assertArrayEquals(new int[] {300_000, 300_000, 600_000}, shares);

        final int oldAsked = 1_200_000 * 2;
        assertTrue(Arrays.stream(shares).limit(2).sum() < oldAsked);
    }

    @Test
    @DisplayName("the shares always sum to the shortfall exactly, rounding included")
    void sharesSumExactly() {
        final int[] shares = FeSupplyOutlook.splitHold(1_000_001, 300, new int[] {100, 100});
        assertEquals(1_000_001, Arrays.stream(shares).sum());
    }

    @Test
    @DisplayName("a board with no peers holds the whole shortfall itself (KI-87)")
    void noPeersMeansItIsAllTheBoards() {
        assertArrayEquals(new int[] {750_000}, FeSupplyOutlook.splitHold(750_000, 250, new int[0]));
        assertArrayEquals(new int[] {750_000}, FeSupplyOutlook.splitHold(750_000, 250, null));
    }

    @Test
    @DisplayName("a peer with no critical load is asked for nothing: nothing on it has to survive the night")
    void aPeerWithNoCriticalHoldsNothing() {
        assertArrayEquals(new int[] {0, 500_000}, FeSupplyOutlook.splitHold(500_000, 200, new int[] {0}));
    }

    @Test
    @DisplayName("peers' declared critical is capped at what is left of the board's, in order")
    void peerCriticalIsCapped() {
        final int[] shares = FeSupplyOutlook.splitHold(800_000, 400, new int[] {300, 300});
        assertArrayEquals(new int[] {600_000, 200_000, 0}, shares);
    }

    @Test
    @DisplayName("nothing short means nothing held by anyone: silence is no hold")
    void nothingShortHoldsNothing() {
        assertArrayEquals(new int[] {0, 0, 0}, FeSupplyOutlook.splitHold(0, 400, new int[] {100, 100}));
        assertArrayEquals(new int[] {0, 0}, FeSupplyOutlook.splitHold(-5, 400, new int[] {100}));
    }

    @Test
    @DisplayName("large figures do not wrap")
    void largeFiguresDoNotWrap() {
        final int[] shares = FeSupplyOutlook.splitHold(Integer.MAX_VALUE, 1_000_000, new int[] {500_000});
        assertTrue(shares[0] > 0 && shares[1] > 0);
        assertEquals(Integer.MAX_VALUE, (long) shares[0] + shares[1]);
    }
}
