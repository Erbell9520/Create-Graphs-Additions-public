package com.createstats.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeBandSettingsTest {
    private static final int BIG = 500_000_000;
    private static final int WANT = 2_000;
    private static final int FULL = 65_536 * 3;
    private static final int RIDE = FeReserveBand.DEFAULT_RIDE_TICKS;

    private static final FeBandSettings SERVER =
            FeBandSettings.server(FeReserveBand.Band.DEFAULT, FeReserveBand.Minutes.DEFAULT);

    @Test
    void anUntouchedControllerFollowsTheServer() {
        assertEquals(FeBandSettings.Unit.MINUTES, SERVER.unit());
        assertEquals(10, SERVER.floor());
        assertEquals(30, SERVER.top());
        assertEquals(60, SERVER.healthy());
        assertEquals(FeReserveBand.Band.DEFAULT, SERVER.caps());

        assertEquals(FeReserveBand.sized(FeReserveBand.Band.DEFAULT, FeReserveBand.Minutes.DEFAULT, BIG, WANT, FULL,
                RIDE), SERVER.band(BIG, WANT, FULL, RIDE));
    }

    @Test
    void thePlayersCapsAreTheirsToo() {
        final int small = 10_000_000;
        assertEquals(10, SERVER.band(small, 200, FULL, RIDE).floorPct());
        assertEquals(20, SERVER.withCap(0, 20).band(small, 200, FULL, RIDE).floorPct());
    }

    @Test
    void percentIsTheBandItSays() {
        final FeBandSettings pct = new FeBandSettings(FeBandSettings.Unit.PERCENT, 5, 20, 50, 10, 30, 60);
        assertEquals(new FeReserveBand.Band(5, 20, 50), pct.band(BIG, WANT, FULL, RIDE));

        assertEquals(5, pct.band(BIG, 0, FULL, RIDE).floorPct());
        assertEquals(0, SERVER.band(BIG, 0, FULL, RIDE).floorPct());
    }

    @Test
    void anEdgeRaisedPastTheNextPushesItAlong() {
        final FeBandSettings up = SERVER.withEdge(0, 45);
        assertEquals(45, up.floor());
        assertEquals(45, up.top(), "the floor carried the top up with it");
        assertEquals(60, up.healthy());
        final FeBandSettings down = SERVER.withEdge(2, 20);
        assertEquals(20, down.healthy());
        assertEquals(20, down.top(), "healthy carried the top down with it");
        assertEquals(10, down.floor());
        final FeBandSettings caps = SERVER.withCap(1, 5);
        assertEquals(5, caps.capTop());
        assertEquals(5, caps.capFloor(), "a cap lowered past the one under it carries it down");
    }

    @Test
    void everythingIsClampedWhateverAPacketSays() {
        final FeBandSettings wild = new FeBandSettings(null, -5, Integer.MAX_VALUE, 3, -1, 500, 200);
        assertEquals(FeBandSettings.Unit.MINUTES, wild.unit());
        assertEquals(0, wild.floor());
        assertEquals(FeBandSettings.MAX_MINUTES, wild.top());
        assertTrue(wild.healthy() >= wild.top());
        assertEquals(0, wild.capFloor());
        assertEquals(98, wild.capTop());
        assertEquals(100, wild.capHealthy());
        final FeBandSettings pct = new FeBandSettings(FeBandSettings.Unit.PERCENT, 200, 200, 200, 10, 30, 60);
        assertEquals(95, pct.floor());
        assertEquals(98, pct.top());
        assertEquals(100, pct.healthy());
    }

    @Test
    void switchingUnitLeavesTheBandWhereItIs() {
        final FeReserveBand.Band before = SERVER.band(BIG, WANT, FULL, RIDE);
        final FeBandSettings pct = SERVER.withUnit(FeBandSettings.Unit.PERCENT, BIG, WANT, FULL, RIDE,
                FeReserveBand.Minutes.DEFAULT);
        assertEquals(FeBandSettings.Unit.PERCENT, pct.unit());
        assertEquals(before, pct.band(BIG, WANT, FULL, RIDE));
        final FeBandSettings back = pct.withUnit(FeBandSettings.Unit.MINUTES, BIG, WANT, FULL, RIDE,
                FeReserveBand.Minutes.DEFAULT);
        assertEquals(before.floorPct(), back.band(BIG, WANT, FULL, RIDE).floorPct());
        assertEquals(before.ceilingPct(), back.band(BIG, WANT, FULL, RIDE).ceilingPct());

        for (int floor = 1; floor <= 40; floor += 3) {
            final FeBandSettings p = new FeBandSettings(FeBandSettings.Unit.PERCENT, floor, floor + 10, floor + 30,
                    95, 98, 100);
            final FeBandSettings m = p.withUnit(FeBandSettings.Unit.MINUTES, BIG, WANT, FULL, RIDE,
                    FeReserveBand.Minutes.DEFAULT);
            final FeReserveBand.Band b = m.band(BIG, WANT, FULL, RIDE);
            assertEquals(floor, b.floorPct(), "floor " + floor + "% came back as " + b.floorPct() + "%");
            assertEquals(floor + 10, b.ceilingPct(), "top at floor " + floor);
        }
    }

    @Test
    void withNoWantMinutesComeFromTheServer() {
        final FeBandSettings pct = new FeBandSettings(FeBandSettings.Unit.PERCENT, 12, 25, 50, 10, 30, 60);
        final FeBandSettings m = pct.withUnit(FeBandSettings.Unit.MINUTES, BIG, 0, FULL, RIDE,
                new FeReserveBand.Minutes(7, 21, 45));
        assertEquals(7, m.floor());
        assertEquals(21, m.top());
        assertEquals(45, m.healthy());
    }
}
