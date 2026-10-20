package com.createstats.chunkloader.core;

import com.createstats.power.FeStationFeed;
import com.createstats.power.PduReason;

public record CrownBoard(int legA, int legB, boolean liveA, boolean liveB, int shareA, int shareB, int carried,
                         int cost, PduReason reason, boolean covers, int shortIn, int banks) {
    public static final CrownBoard NONE = new CrownBoard(-1, -1, false, false, 0, 0, 0, 0, PduReason.RUNNING, false, -1,
            0);

    // short forecast within a minute refuses a pick, further off is only a warning. the banks are yours to spend
    public static final int SUPPLY_HOLD_TICKS = 1200;

    private static final int PACKED = 12;

    public CrownBoard {
        reason = reason == null ? PduReason.RUNNING : reason;
    }

    public static CrownBoard of(FeStationFeed[] feeds, long now, boolean wiredA, boolean wiredB) {
        int legA = -1;
        int legB = -1;
        boolean liveA = false;
        boolean liveB = false;
        int shareA = 0;
        int shareB = 0;
        long carried = 0;
        int cost = 0;
        PduReason reason = PduReason.RUNNING;
        int shortIn = -1;
        long banks = 0;
        boolean any = false;
        for (FeStationFeed f : feeds) {
            if (f == null || !f.fresh(now)) {
                continue;
            }
            any = true;
            if (f.legA() >= 0 && legA < 0) {
                legA = f.legA();
                liveA = f.liveA();
                shareA = Math.max(0, f.shareA());
            }
            if (f.legB() >= 0 && legB < 0) {
                legB = f.legB();
                liveB = f.liveB();
                shareB = Math.max(0, f.shareB());
            }
            carried += Math.max(0, f.carried());
            cost = Math.max(cost, f.cost());
            reason = worse(reason, f.reason());
            if (f.shortIn() >= 0 && (shortIn < 0 || f.shortIn() < shortIn)) {
                shortIn = f.shortIn();
            }
            banks += Math.max(0, -f.banks());
        }
        if (!any) {
            return NONE;
        }
        final boolean covers = (!wiredA || legA >= 0) && (!wiredB || legB >= 0) && (wiredA || wiredB);
        return new CrownBoard(legA, legB, liveA, liveB, shareA, shareB, (int) Math.min(Integer.MAX_VALUE, carried),
                cost, reason, covers, shortIn, (int) Math.min(Integer.MAX_VALUE, banks));
    }

    public boolean present() {
        return legA >= 0 || legB >= 0;
    }

    public long pickCarry() {
        return covers ? carried : CrownPicks.NO_BOARD;
    }

    public int pickShortIn() {
        return covers ? shortIn : -1;
    }

    public boolean onBanks() {
        return banks > 0;
    }

    public int fed() {
        return shareA + shareB;
    }

    private static PduReason worse(PduReason a, PduReason b) {
        return rank(b) > rank(a) ? b : a;
    }

    private static int rank(PduReason r) {
        if (r == PduReason.BREAKER_OPEN || r == PduReason.LOCKED_OFF) {
            return 3;
        }
        if (r.isShed()) {
            return 2;
        }
        return r == PduReason.WIRE_SHORT ? 1 : 0;
    }

    public int[] pack() {
        return new int[] {legA, legB, liveA ? 1 : 0, liveB ? 1 : 0, shareA, shareB, carried, cost, reason.ordinal(),
                covers ? 1 : 0, shortIn, banks};
    }

    public static CrownBoard unpack(int[] a) {
        if (a == null || a.length < PACKED) {
            return NONE;
        }
        return new CrownBoard(a[0], a[1], a[2] != 0, a[3] != 0, a[4], a[5], a[6], a[7], PduReason.byOrdinal(a[8]),
                a[9] != 0, a[10], a[11]);
    }

    public String discrete() {
        return legA + "," + legB + "," + liveA + "," + liveB + "," + reason + "," + covers + "," + onBanks() + ","
                + (shortIn >= 0 && shortIn < SUPPLY_HOLD_TICKS);
    }
}
