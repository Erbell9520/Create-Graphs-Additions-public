package com.createstats.chunkloader.map;

import com.createstats.chunkloader.core.CrownBoard;
import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownLaw;
import com.createstats.chunkloader.core.CrownRegion;
import com.createstats.chunkloader.core.CrownState;
import com.createstats.power.PduReason;

final class CrownMapFixtures {
    static final CrownLaw LAW = CrownLaw.DEFAULT;
    static final int[][] PICKS = {{9, -24}, {10, -24}, {9, -23}, {8, -24}, {9, -25}, {10, -23}, {8, -23}, {10, -25},
            {8, -25}, {11, -24}, {11, -23}, {7, -24}, {11, -25}, {7, -23}, {7, -25}, {12, -24}, {12, -23}, {12, -25},
            {6, -24}, {9, -26}, {10, -26}, {8, -26}, {9, -22}, {10, -22}, {8, -22}};
    static final int ELSEWHERE = 19;
    static final int[] LEGENDS = {36, 24, 30, 24};

    private CrownMapFixtures() {
    }

    static long[] picks(int n) {
        final long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = CrownRegion.pack(PICKS[i][0], PICKS[i][1]);
        }
        return out;
    }

    static CrownState state(int held, int picked, CrownHold.Phase phase, long stored, int intake, int ownerTotal,
                            CrownState.Reason reason) {
        return new CrownState(held, picked, held, held >= 32 ? -1 : (1 << held) - 1, phase, 1f, stored,
                LAW.storeCapacity(picked), LAW.totalDraw(held), LAW.totalDraw(picked), intake, LAW.nextPrice(picked),
                ownerTotal, LAW.maxPerStation(), LAW.maxPerPlayer(), 3, 65_536, 65_536, 0, LAW.lightLevel(held), reason,
                0L, false, 0, 0, 0, "", 0, -1);
    }

    static CrownState fed(int n, int ownerTotal) {
        return state(n, n, CrownHold.Phase.FED, LAW.storeCapacity(n), LAW.totalDraw(n), ownerTotal,
                CrownState.Reason.NONE);
    }

    static CrownBoard board(int legA, int legB, boolean liveA, boolean liveB, int carried, int banks, int shortIn) {
        return new CrownBoard(legA, legB, liveA, liveB, 65_536, 65_536, carried, 0, PduReason.RUNNING, true, shortIn,
                banks);
    }

    static CrownBoard studs12() {
        return board(0, 1, true, true, 131_072, 0, -1);
    }

    static CrownMapTooltip.Crown crown(CrownState s, CrownBoard b, int picks, boolean owner) {
        return new CrownMapTooltip.Crown(9, -24, picks(picks), s, b, LAW, owner, true, "Bolat");
    }
}
