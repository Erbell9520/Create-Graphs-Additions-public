package com.createstats.stormcoil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilMotionTest {
    @Test
    @DisplayName("no replay on load: the first aim snaps")
    void firstAimSnaps() {
        final CoilMotion m = new CoilMotion();
        m.aim(4);
        assertFalse(m.moving());
        assertEquals(4.0, m.height(0f));
        assertEquals(4.0, m.height(0.5f));
    }

    @Test
    @DisplayName("1 to 4 and back is smooth, one move each way, never past 4 or below 1, and arrives")
    void smoothBothWays() {
        final CoilMotion m = new CoilMotion();
        m.aim(1);
        m.aim(4);
        double last = m.height(0f);
        int ticks = 0;
        while (m.moving()) {
            for (float p = 0.25f; p <= 1.0f; p += 0.25f) {
                final double h = m.height(p);
                assertTrue(h >= 1.0 && h <= 4.0, "out of range: " + h);
                assertTrue(Math.abs(h - last) <= 0.15, "a jump of " + (h - last) + " in a quarter tick");
                last = h;
            }
            m.tick(true);
            ticks++;
        }
        assertEquals(CoilMotion.BASE_TICKS + 3 * CoilMotion.TICKS_PER_STAGE, ticks, "one move, not three steps");
        assertEquals(4.0, m.height(0f));
        assertTrue(m.settle(0.5f) != 0.0, "it settles after arriving");
        m.aim(1);
        while (m.moving()) {
            final double h = m.height(0.5f);
            assertTrue(h >= 1.0 && h <= 4.0);
            m.tick(true);
        }
        assertEquals(1.0, m.height(0f));
    }

    @Test
    @DisplayName("a move between middle stages overshoots a little and settles back")
    void overshoots() {
        final CoilMotion m = new CoilMotion();
        m.aim(1);
        m.aim(3);
        double peak = 0;
        while (m.moving()) {
            peak = Math.max(peak, m.height(0f));
            m.tick(true);
        }
        assertTrue(peak > 3.0 && peak < 3.25, "peak " + peak);
        assertEquals(3.0, m.height(0f));
    }

    @Test
    @DisplayName("the light comes up and goes down over its own ticks")
    void glow() {
        final CoilMotion m = new CoilMotion();
        m.aim(1);
        for (int i = 0; i < CoilMotion.GLOW_TICKS; i++) {
            m.tick(true);
        }
        assertEquals(1.0, m.glow(1f), 1e-9);
        m.tick(false);
        assertTrue(m.glow(1f) < 1.0);
    }

    @Test
    @DisplayName("it rises only into free cells and names what stopped it")
    void fit() {
        assertEquals(new CoilFit.Fit(4, 0, 0), CoilFit.fit(4, k -> true, true));
        assertEquals(new CoilFit.Fit(2, 3, 2), CoilFit.fit(4, k -> k != 2, true));
        assertEquals(new CoilFit.Fit(1, 2, 1), CoilFit.fit(3, k -> k != 1, true));
        assertEquals(new CoilFit.Fit(3, 4, CoilFit.RODS), CoilFit.fit(4, k -> true, false));
        assertEquals(new CoilFit.Fit(3, 0, 0), CoilFit.fit(3, k -> true, false), "rods only matter at stage 4");
        assertEquals(4, CoilFit.fit(12, k -> true, true).stage(), "never past 4");
        assertEquals(1, CoilFit.fit(-3, k -> false, false).stage());
    }

    @Test
    @DisplayName("the gap spins up in half a second, runs down in about three, ticks over when ready, stops when flat")
    void gapSpeed() {
        final CoilMotion m = new CoilMotion();
        m.aim(1);
        assertEquals(0.0, m.gapSpeed(), "a new coil's wheel stands");
        m.gap(CoilMotion.Gap.RUN);
        int up = 0;
        while (m.gapSpeed() < 1.0) {
            m.tick(true);
            up++;
        }
        assertEquals(CoilMotion.GAP_UP_TICKS, up, "spin up");
        assertTrue(up * 50 <= 600, "about half a second: " + up + " ticks");
        m.gap(CoilMotion.Gap.STILL);
        int down = 0;
        double last = m.gapSpeed();
        while (m.gapSpeed() > 0.0) {
            m.tick(false);
            assertTrue(m.gapSpeed() < last, "it slows every tick on the way down");
            last = m.gapSpeed();
            down++;
        }
        assertEquals(CoilMotion.GAP_DOWN_TICKS, down, "run down");
        assertTrue(down * 50 >= 2500 && down * 50 <= 3500, "about three seconds: " + down + " ticks");
        m.gap(CoilMotion.Gap.IDLE);
        for (int i = 0; i < 40; i++) {
            m.tick(true);
        }
        assertEquals(CoilMotion.IDLE_SPEED, m.gapSpeed(), 1e-12, "a ready coil ticks over");
        assertTrue(CoilMotion.IDLE_SPEED > 0 && CoilMotion.IDLE_SPEED < CoilMotion.SPARK_FROM);
    }

    @Test
    @DisplayName("the wheel runs while charging, fighting or winding up, ticks over when ready, stands when flat")
    void gapFor() {
        final long now = 1000;
        assertEquals(CoilMotion.Gap.RUN, CoilMotion.gapFor(true, true, false, now, Long.MIN_VALUE), "charging");
        assertEquals(CoilMotion.Gap.RUN, CoilMotion.gapFor(true, false, true, now, Long.MIN_VALUE), "winding up");
        assertEquals(CoilMotion.Gap.RUN, CoilMotion.gapFor(true, false, false, now, now - 25), "between two volleys");
        assertEquals(CoilMotion.Gap.IDLE, CoilMotion.gapFor(true, false, false, now, now - CoilMotion.FIGHT_HOLD),
                "the fight is over");
        assertEquals(CoilMotion.Gap.IDLE, CoilMotion.gapFor(true, false, false, now, Long.MIN_VALUE), "ready");
        assertEquals(CoilMotion.Gap.STILL, CoilMotion.gapFor(false, false, false, now, Long.MIN_VALUE), "flat");
        assertEquals(CoilMotion.Gap.IDLE, CoilMotion.gapFor(true, false, false, 5, Long.MIN_VALUE),
                "no volley yet is no fight, early in a world too (MIN_VALUE never wraps into a recent volley)");
        assertTrue(CoilMotion.FIGHT_HOLD > CoilLaw.ZAP_INTERVAL);
    }

    @Test
    @DisplayName("the wheel turns with its speed and never jumps; it sparks as a stud passes, only while it runs fast")
    void gapTurnsAndSparks() {
        final CoilMotion m = new CoilMotion();
        m.aim(1);
        m.gap(CoilMotion.Gap.RUN);
        double before = m.gapAngle(1f);
        int sparks = 0;
        for (int i = 0; i < 200; i++) {
            m.tick(true);
            final double now = m.gapAngle(1f);
            final double step = (now - before + 3600.0) % 3600.0;
            assertTrue(step <= CoilMotion.FULL_DEGREES_PER_TICK + 1e-9, "a jump of " + step + " degrees in one tick");
            before = now;
            double brightest = 0;
            for (float p = 0f; p <= 1.0001f; p += 0.05f) {
                brightest = Math.max(brightest, m.gapSpark(Math.min(1f, p)));
            }
            if (i > CoilMotion.GAP_UP_TICKS && brightest > 0.9) {
                sparks++;
            }
        }
        assertTrue(sparks > 80, "flat out, a stud reaches the electrodes every other tick: " + sparks);
        for (double a = 0; a < 360; a += 1.0) {
            assertEquals(0.0, CoilMotion.spark(a, CoilMotion.IDLE_SPEED), "an idling wheel throws no spark at " + a);
            assertEquals(0.0, CoilMotion.spark(a, 0.0));
        }
        assertEquals(1.0, CoilMotion.spark(0.0, 1.0), 1e-12, "a stud at the electrodes, flat out");
        assertEquals(1.0, CoilMotion.spark(360.0 / CoilMotion.STUDS, 1.0), 1e-12, "and the next one");
        assertEquals(0.0, CoilMotion.spark(360.0 / CoilMotion.STUDS / 2, 1.0), 1e-12, "dark between studs");
    }

    @Test
    @DisplayName("a blocked column nudges up toward the blocker every few seconds and settles; never on load, never moving")
    void blockedNudge() {
        final CoilMotion m = new CoilMotion();
        m.aim(2);
        m.blocked(false);
        for (int i = 0; i < 300; i++) {
            m.tick(true);
            assertEquals(0.0, m.nudge(1f), "a free coil never nudges");
        }
        m.blocked(true);
        int firstTry = -1;
        double peak = 0;
        for (int i = 1; i <= 3 * CoilMotion.NUDGE_EVERY; i++) {
            m.tick(true);
            final double n = m.nudge(1f);
            assertTrue(n >= -0.25 * CoilMotion.NUDGE && n <= CoilMotion.NUDGE + 1e-12, "nudge " + n + " at " + i);
            if (n > 1e-6 && firstTry < 0) {
                firstTry = i;
            }
            for (float p = 0f; p <= 1.0001f; p += 0.05f) {
                peak = Math.max(peak, m.nudge(Math.min(1f, p)));
            }
            final int into = i % CoilMotion.NUDGE_EVERY;
            if (into != 0 && into < CoilMotion.NUDGE_EVERY - CoilMotion.NUDGE_TICKS) {
                assertEquals(0.0, n, 1e-12, "between tries it stands still, at " + i);
            }
        }
        assertTrue(firstTry > CoilMotion.NUDGE_EVERY - CoilMotion.NUDGE_TICKS - 1,
                "no replay on load: the first try waits most of a period, came at " + firstTry);
        assertEquals(CoilMotion.NUDGE, peak, 0.01 * CoilMotion.NUDGE, "it reaches the blocker");
        assertEquals(0.0, CoilMotion.nudgeShape(CoilMotion.NUDGE_EVERY), 1e-12, "each try ends where it began");
        m.aim(1);
        while (m.moving()) {
            m.tick(true);
            assertEquals(0.0, m.nudge(0.5f), "never while the column is moving");
        }
    }

    @Test
    @DisplayName("the orb ducks under a ceiling over a few ticks, and a coil loaded under one does not duck again")
    void ducks() {
        final CoilMotion roofed = new CoilMotion();
        roofed.headroom(false);
        assertEquals(1.0, roofed.duck(0f), "loaded under a roof: already ducked, no replay");
        final CoilMotion m = new CoilMotion();
        m.headroom(true);
        m.tick(true);
        assertEquals(0.0, m.duck(1f));
        m.headroom(false);
        int ticks = 0;
        double last = 0;
        while (m.duck(1f) < 1.0) {
            m.tick(true);
            assertTrue(m.duck(1f) > last, "it ducks a little every tick");
            last = m.duck(1f);
            ticks++;
        }
        assertEquals(CoilMotion.DUCK_TICKS, ticks, "ducked over its ticks, never at once");
        m.headroom(true);
        m.tick(true);
        assertTrue(m.duck(1f) < 1.0 && m.duck(1f) > 0.0, "and rises again the same way");
    }

    @Test
    @DisplayName("the LOAD needle snaps on the first reading and sweeps to later ones, never past them")
    void loadEases() {
        final CoilMotion m = new CoilMotion();
        m.load(0.4);
        assertEquals(0.4, m.load(0f), 1e-12, "a coil seen on load is not swept up in front of the player");
        m.load(1.0);
        double last = 0.4;
        for (int i = 0; i < 60; i++) {
            m.tick(true);
            final double now = m.load(1f);
            assertTrue(now >= last && now <= 1.0, "it sweeps up and never past the reading");
            last = now;
        }
        assertEquals(1.0, m.load(1f), 1e-4);
        m.load(-3.0);
        m.tick(true);
        assertTrue(m.load(1f) < 1.0 && m.load(1f) > 0.0, "a nonsense reading clamps to 0 and is swept to");
    }

    @Test
    @DisplayName("the jars' charge snaps on the first reading and eases to later ones")
    void chargeEases() {
        final CoilMotion m = new CoilMotion();
        m.charge(0.75, 0.3);
        assertEquals(0.75, m.charge(0f), 1e-12, "a coil seen on load is not refilled in front of the player");
        assertEquals(0.3, m.bankShown(0f), 1e-12);
        m.charge(0.25, 0.3);
        m.tick(true);
        final double one = m.charge(1f);
        assertTrue(one < 0.75 && one > 0.25, "a volley drains its jar over a few ticks, not at once: " + one);
        for (int i = 0; i < 40; i++) {
            m.tick(true);
        }
        assertEquals(0.25, m.charge(1f), 1e-4);
    }

    @Test
    @DisplayName("lightning: in the stage's radius, and only when closer than a rod")
    void catchRule() {
        assertTrue(CoilCatch.inRadius(CoilStage.ONE, 32L * 32));
        assertFalse(CoilCatch.inRadius(CoilStage.ONE, 32L * 32 + 1));
        assertTrue(CoilCatch.inRadius(CoilStage.FOUR, 128L * 128));
        assertTrue(CoilCatch.beatsRod(100, -1));
        assertTrue(CoilCatch.beatsRod(99, 100));
        assertFalse(CoilCatch.beatsRod(100, 100), "a tie goes to the rod");
    }
}
