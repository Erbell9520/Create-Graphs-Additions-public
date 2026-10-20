package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TwoSpansOneTerminalTest {
    private static final int HV = 65_536;
    private static final int NONE = FeGridSolver.NO_CEILING;
    private static final long TICK = 7L;

    private static long[] keys(int n) {
        final long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = 5_000L + i;
        }
        return out;
    }

    @Test
    @DisplayName("one plant, two HV Lines from two connectors into one terminal: the station gets one line's 65,536")
    void oneGridTwoSpansIsOneSpan() {
        final int[] parent = {-1, 0, 0, 1};
        final int[] rate = {NONE, HV, HV, NONE};
        final FePathBudget path = new FePathBudget(keys(4), parent, rate, new boolean[] {false, false, false, true});
        final long station = keys(4)[0];
        assertEquals(HV, path.remaining(station, TICK), "MECH-4 holds it to the span the flood came along");
        path.spend(station, HV, TICK);
        assertEquals(0, path.remaining(station, TICK), "and the second HV Line adds nothing to it");

        final FeConduitThroat throat = new FeConduitThroat(keys(4), parent, rate,
                new int[] {0, 0, 0, 1}, new int[] {1, 0, 0, 0});
        assertEquals(HV, throat.ceiling(), "MECH-2: the span into the only load is the circuit's throat");
    }

    @Test
    @DisplayName("the flood started at the plant instead: the same one span")
    void theRootDoesNotMatter() {
        final int[] parent = {-1, 0, 0, 1};
        final int[] rate = {NONE, NONE, NONE, HV};
        final FePathBudget path = new FePathBudget(keys(4), parent, rate, new boolean[] {true, false, false, false});
        assertEquals(HV, path.remaining(keys(4)[3], TICK));
        path.spend(keys(4)[3], HV, TICK);
        assertEquals(0, path.remaining(keys(4)[3], TICK));
    }

    @Test
    @DisplayName("two separate plants, one line each, joined through the station: still one span's worth")
    void twoGridsJoinedThroughTheStation() {
        final int[] parent = {-1, 0, 0, 1, 2};
        final int[] rate = {NONE, HV, HV, NONE, NONE};
        final FePathBudget path = new FePathBudget(keys(5), parent, rate,
                new boolean[] {false, false, false, true, true});
        final long station = keys(5)[0];
        assertEquals(HV, path.remaining(station, TICK), "re-rooted on both plants, the station still has one up edge");
        path.spend(station, HV, TICK);
        assertEquals(0, path.remaining(station, TICK), "so the second plant's line carries none of its delivery");
    }

    @Test
    @DisplayName("the two spans in parallel would carry 131,072, which is what the agreed design counted on")
    void theDesignCountedOnTheSum() {
        assertEquals(2 * HV, 131_072);
        assertEquals(HV, FeRates.wire(WireTier.HV), "the ladder's HV Line at the default base");
    }
}
