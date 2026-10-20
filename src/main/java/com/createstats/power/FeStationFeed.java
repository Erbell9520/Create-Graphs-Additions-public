package com.createstats.power;

public record FeStationFeed(long board, int legA, int legB, boolean liveA, boolean liveB, int shareA, int shareB,
                            int carried, int cost, PduReason reason, int shortIn, int banks, long expires) {
    public static final int HOLD_TICKS = 10;

    public FeStationFeed {
        reason = reason == null ? PduReason.RUNNING : reason;
    }

    public boolean fresh(long now) {
        return now <= expires;
    }

    public int fed() {
        return FeRates.plus(Math.max(0, shareA), Math.max(0, shareB));
    }

    public boolean feedsStud(int stud) {
        return (stud == 1 ? legB : legA) >= 0;
    }

    public boolean carries(long draw) {
        return draw <= carried;
    }
}
