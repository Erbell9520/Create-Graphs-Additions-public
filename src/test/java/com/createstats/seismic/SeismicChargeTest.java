package com.createstats.seismic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicChargeTest {
    private static final Object[][] TABLE = {
            {32, 62_500, 13, "12.2 s", "3.1 s", "0.8 s"},
            {48, 210_938, 42, "41.2 s", "10.3 s", "2.6 s"},
            {64, 500_000, 100, "97.7 s", "24.4 s", "6.1 s"},
    };

    @Test
    @DisplayName("the burst, the drop height and the charge times reproduce the design's table")
    void theDesignTable() {
        for (final Object[] row : TABLE) {
            final int r = (int) row[0];
            assertEquals(row[1], SeismicCharge.burstFor(r), "burst at radius " + r);
            assertEquals(row[2], percent(SeismicCharge.heightFor(r)), "drop height at radius " + r);
            assertEquals(row[3], printed(r, 256), "charge at 256 FE/t, radius " + r);
            assertEquals(row[4], printed(r, 1024), "charge at 1,024 FE/t, radius " + r);
            assertEquals(row[5], printed(r, 4096), "charge at 4,096 FE/t, radius " + r);
        }
    }

    @Test
    @DisplayName("the winch never takes more than 4,096 FE a tick, nor more than the room to the tick")
    void theWinchIsCapped() {
        assertEquals(4096, SeismicCharge.winchTake(64, 0, 100_000, 0));
        assertEquals(96, SeismicCharge.winchTake(64, 0, 100_000, 4000), "what this tick already took counts");
        assertEquals(0, SeismicCharge.winchTake(64, 0, 100_000, 4096));
        assertEquals(256, SeismicCharge.winchTake(64, 0, 256, 0), "a thin supply is taken whole");
        assertEquals(6, SeismicCharge.winchTake(32, 62_494, 4096, 0), "stops at the tick for the set radius");
        assertEquals(0, SeismicCharge.winchTake(32, 62_500, 4096, 0));
        assertEquals(0, SeismicCharge.winchTake(32, 100_000, 4096, 0), "a weight above the tick is never lowered");

        assertEquals(16, SeismicCharge.ticksToCharge(0, 32, 4096));
        assertEquals(Integer.MAX_VALUE, SeismicCharge.ticksToCharge(0, 32, 0));
        assertEquals(0, SeismicCharge.ticksToCharge(62_500, 32, 512));
    }

    @Test
    @DisplayName("height is the charge over the full blow, and the reach is the burst law turned round")
    void heightAndReach() {
        assertEquals(0.0, SeismicCharge.heightOf(0));
        assertEquals(0.5, SeismicCharge.heightOf(250_000), 1e-12);
        assertEquals(1.0, SeismicCharge.heightOf(500_000));
        assertEquals(1.0, SeismicCharge.heightOf(999_999), "clamped at the crown");
        for (int r = 1; r <= 64; r++) {
            final long burst = Math.round(500_000.0 * Math.pow(r / 64.0, 3));
            assertEquals(r, SeismicCharge.reachOf(burst), "the burst for radius " + r + " reaches radius " + r);
        }
        assertEquals(0, SeismicCharge.reachOf(0));
        assertEquals(64, SeismicCharge.reachOf(500_000));
        for (int r = SeismicCharge.MIN_RADIUS; r <= SeismicCharge.MAX_RADIUS; r += SeismicCharge.RADIUS_STEP) {
            assertEquals(r, SeismicCharge.reachOf(SeismicCharge.burstFor(r)), "the tick for " + r + " reaches it");

            final long short1 = (long) Math.floor(500_000.0 * Math.pow(r / 64.0, 3) - 1.0);
            assertEquals(r - 1, SeismicCharge.reachOf(short1), "a whole FE short of the law for " + r
                    + " reaches a block less");
        }
    }

    @Test
    @DisplayName("a drop before the tick surveys what it reaches, never more than set; a tap maps nothing")
    void earlyDrops() {
        assertEquals(48, SeismicCharge.surveyRadius(48, SeismicCharge.burstFor(48)));
        assertEquals(48, SeismicCharge.surveyRadius(48, 500_000), "a harder blow still maps the set radius");
        assertEquals(32, SeismicCharge.surveyRadius(64, SeismicCharge.burstFor(32)));
        assertEquals(0, SeismicCharge.surveyRadius(64, 100), "below the least reach a blow is a tap");

        assertEquals(16, SeismicCharge.surveyRadius(64, SeismicCharge.burstFor(SeismicCharge.MIN_RADIUS) / 8));
        assertTrue(SeismicCharge.ready(32, 62_500));
        assertFalse(SeismicCharge.ready(32, 62_499));
    }

    @Test
    @DisplayName("the radius snaps to 32, 48 or 64, and a rig saved at the cut radius 16 reads back as 32")
    void radiusNotches() {
        assertEquals(32, SeismicCharge.MIN_RADIUS, "radius 16 was cut after 1.14.118");
        assertEquals(32, SeismicCharge.clampRadius(0));
        assertEquals(32, SeismicCharge.clampRadius(16), "a 1.14.118 rig saved at radius 16");
        assertEquals(32, SeismicCharge.clampRadius(47));
        assertEquals(48, SeismicCharge.clampRadius(48));
        assertEquals(48, SeismicCharge.clampRadius(63));
        assertEquals(64, SeismicCharge.clampRadius(500));
    }

    @Test
    @DisplayName("a weight falls the whole mast in about a third of a second, faster from lower, accelerating")
    void theFall() {
        assertEquals(7, SeismicCharge.fallTicks(1.0));
        assertEquals(2, SeismicCharge.fallTicks(SeismicCharge.heightFor(32)), "a radius-32 drop takes two ticks");
        assertEquals(5, SeismicCharge.fallTicks(SeismicCharge.heightFor(48)), "a radius-48 drop five");
        assertEquals(0, SeismicCharge.fallTicks(0.0));
        for (double h = 0.05; h < 1.0; h += 0.05) {
            assertTrue(SeismicCharge.fallTicks(h) <= SeismicCharge.fallTicks(h + 0.05));
        }
        assertEquals(0.0, SeismicCharge.fallen(0, 7));
        assertEquals(1.0, SeismicCharge.fallen(7, 7));
        assertTrue(SeismicCharge.fallen(3.5, 7) < 0.5, "a falling body covers less than half its drop in half its time");
    }

    @Test
    @DisplayName("the weight springs back off the plate in two shrinking hops under the fall's own gravity, then lies still")
    void theBounce() {
        final double e = SeismicCharge.RESTITUTION;
        final int ticks = 7;
        assertEquals(0.0, SeismicCharge.bounce(0.0, 1.0, ticks), "on the plate the moment it lands");
        assertEquals(e * e, SeismicCharge.bounce(e * ticks, 1.0, ticks), 1e-12, "the first hop tops out at e squared");
        assertEquals(0.0, SeismicCharge.bounce(2 * e * ticks, 1.0, ticks), 1e-12, "and comes down after 2eT");
        assertEquals(Math.pow(e, 4), SeismicCharge.bounce(2 * e * ticks + e * e * ticks, 1.0, ticks), 1e-12,
                "the second hop tops out at e to the fourth");
        assertEquals(0.0, SeismicCharge.bounce(SeismicCharge.bounceTicks(ticks) + 0.01, 1.0, ticks), "then still");
        assertEquals(0.0, SeismicCharge.bounce(-1.0, 1.0, ticks), "nothing before it lands");
        assertEquals(0.0, SeismicCharge.bounce(1.0, 0.0, 0), "an empty clank does not bounce");
        for (double t = 0.0; t < SeismicCharge.bounceTicks(ticks); t += 0.05) {
            final double b = SeismicCharge.bounce(t, 1.0, ticks);
            assertTrue(b >= -1e-12 && b <= e * e + 1e-12, "never into the plate nor above the first hop: " + b);
        }

        final double px = SeismicGeometry.LIFT * SeismicCharge.bounce(e * ticks, 1.0, ticks);
        assertTrue(px > 0.9 && px < 1.4, "first hop " + px + " px");

        final int lowTicks = SeismicCharge.fallTicks(0.25);
        assertTrue(SeismicCharge.bounceTicks(lowTicks) < SeismicCharge.bounceTicks(ticks));
        assertEquals(e * e * 0.25, SeismicCharge.bounce(e * lowTicks, 0.25, lowTicks), 1e-12);
    }

    private static int percent(double fraction) {
        return new BigDecimal(fraction * 100.0).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static String printed(int radius, int rate) {
        final double seconds = SeismicCharge.secondsToCharge(radius, rate);
        if (seconds < 0.05) {
            return SeismicCharge.ticksToCharge(0, radius, rate) + " tick";
        }
        return new BigDecimal(seconds).setScale(1, RoundingMode.HALF_UP).toPlainString() + " s";
    }
}
