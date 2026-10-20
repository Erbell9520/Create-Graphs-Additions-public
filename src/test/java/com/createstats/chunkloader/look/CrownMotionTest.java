package com.createstats.chunkloader.look;

import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownPlan;
import com.createstats.chunkloader.core.CrownState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownMotionTest {
    static CrownState state(int held, int form, CrownHold.Phase phase, double store, boolean feed, int heartbeat) {
        final int mask = held >= 31 ? -1 : (1 << held) - 1;
        final int draw = held <= 0 ? 0 : (int) Math.round(1024 * (Math.pow(1.1, held) - 1) / 0.1);
        return new CrownState(held, Math.max(1, held), form, mask, phase, (float) store, 0L, 0L, draw, draw,
                feed ? draw : 0, 0, held, 25, 50, 3, 65_536, 65_536, heartbeat, 0, CrownState.Reason.NONE, 0L, false,
                0, 0, 0, "", 0, 0);
    }

    static CrownState fed(int n) {
        return state(n, n, n > 0 ? CrownHold.Phase.FED : CrownHold.Phase.DARK, 1.0, n > 0, 0);
    }

    private static int settle(CrownMotion m, CrownState s, int limit) {
        int ticks = 0;
        while (ticks < limit) {
            tickChecked(m, s);
            ticks++;
            if (m.kind() == CrownMotion.Kind.NONE && m.shown() == s.form()
                    && (s.form() != CrownGeometry.MAX || m.ultimate())) {
                return ticks;
            }
        }
        return ticks;
    }

    static void tickChecked(CrownMotion m, CrownState s) {
        final int before = reach(m);
        m.tick(s);
        final int span = Math.max(Math.max(before, reach(m)), s.form());
        final CrownPose pose = new CrownPose();
        for (float partial : new float[] {0.5f, 1.0f}) {
            m.pose(partial, pose);
            final MeshProbe probe = MeshProbe.of(pose);
            final String out = probe.outside(CrownPlan.cellsFor(Math.max(1, span)));
            assertNull(out, "a frame leaves the cells claimed for " + span + " (" + m.kind() + " age " + m.stepAge()
                    + "): " + out);
            assertTrue(probe.minX >= -16 - 1e-4 && probe.maxX <= 32 + 1e-4 && probe.maxY <= 48.0
                    && probe.minZ >= -16 - 1e-4 && probe.maxZ <= 32 + 1e-4,
                    "a frame passes the 3 x 3 x 3: y up to " + probe.maxY);
        }
    }

    private static int reach(CrownMotion m) {
        return m.kind() == CrownMotion.Kind.UP ? m.shown() + 1 : m.shown();
    }

    @Test
    @DisplayName("a crown seen for the first time shows its state at once: 25 is the ULTIMATE form, no growth replay")
    void noReplayOnLoad() {
        final CrownMotion m = new CrownMotion();
        m.tick(fed(25));
        assertEquals(25, m.shown());
        assertEquals(CrownMotion.Kind.NONE, m.kind());
        assertTrue(m.ultimate());
        final CrownPose p = m.current();
        assertEquals(1.0, p.yokeSwing, 1e-12);
        assertEquals(1.0, p.capOpen, 1e-12, "fed at 25: the claw is open on the lit heart");
        assertEquals(1.0, p.sigilAlpha, 1e-12);
        for (int k = 0; k < 25; k++) {
            assertEquals(1.0, p.mount[k], 1e-12);
            assertEquals(CrownPose.INK_ROSE, p.ink[k]);
        }
        final CrownMotion seven = new CrownMotion();
        seven.tick(fed(7));
        assertEquals(7, seven.shown());
        assertEquals(CrownMotion.Kind.NONE, seven.kind());
        final CrownMotion dark = new CrownMotion();
        dark.tick(state(0, 0, CrownHold.Phase.DARK, 0, false, 0));
        assertEquals(0, dark.shown());
        assertEquals(CrownPose.PILOT_RED, dark.current().pilot, "dark: the pilot is red (blinking)");
    }

    @Test
    @DisplayName("each chunk is its own move: a sector is thrown out, overshoots the rim and latches")
    void eachChunkIsItsOwnMove() {
        final CrownMotion m = new CrownMotion();
        m.tick(fed(7));
        final CrownState nine = fed(9);
        double peak8 = 0;
        boolean ninthBeforeEighth = false;
        for (int i = 0; i < 4 * CrownMotion.UP_SECTOR; i++) {
            tickChecked(m, nine);
            final CrownPose p = m.current();
            peak8 = Math.max(peak8, p.mount[7]);
            if (p.mount[8] > 0 && p.mount[7] < 1.0 - 1e-9) {
                ninthBeforeEighth = true;
            }
        }
        assertTrue(peak8 > 1.03, "no overshoot past the rim: " + peak8);
        assertFalse(ninthBeforeEighth, "the ninth sector moved before the eighth had latched: two at once");
        assertEquals(9, m.shown());
        assertEquals(1.0, m.current().mount[7], 1e-12);
        assertEquals(1.0, m.current().mount[8], 1e-12);
    }

    @Test
    @DisplayName("at 6 a ring telescopes up: the stem overshoots and settles, the horns grow a knob")
    void aRingRises() {
        final CrownMotion m = new CrownMotion();
        m.tick(fed(5));
        final double horn0 = m.current().hornTop;
        double stemPeak = 0;
        final CrownState six = fed(6);
        final int ticks = settle(m, six, 100);
        assertEquals(CrownMotion.UP_TIER, ticks, "a new ring is its own, longer move");
        final CrownMotion again = new CrownMotion();
        again.tick(fed(5));
        for (int i = 0; i < CrownMotion.UP_TIER; i++) {
            again.tick(six);
            stemPeak = Math.max(stemPeak, again.current().stemTop);
        }
        final double stemEnd = CrownGeometry.stemTopFor(2, CrownGeometry.ring0YFor(6));
        assertTrue(stemPeak > stemEnd + 0.1, "the stem should overshoot as it telescopes: " + stemPeak);
        assertEquals(stemEnd, again.current().stemTop, 1e-9);
        assertEquals(1.0, again.current().tierUp[1], 1e-9);
        assertTrue(again.current().hornTop > horn0 + 9, "the horns grew to the second ring's knob");
    }

    @Test
    @DisplayName("going down is quicker than going up and never throws a part past where it goes")
    void goingDownIsQuicker() {
        final CrownMotion up = new CrownMotion();
        up.tick(fed(5));
        final int upTicks = settle(up, fed(9), 400);
        final CrownMotion down = new CrownMotion();
        down.tick(fed(9));
        int downTicks = 0;
        final CrownState five = fed(5);
        while (downTicks < 400 && !(down.shown() == 5 && down.kind() == CrownMotion.Kind.NONE)) {
            tickChecked(down, five);
            downTicks++;
            for (double mount : down.current().mount) {
                assertTrue(mount <= 1.0 + 1e-9, "a sector overshot on its way home");
            }
        }
        assertTrue(downTicks < upTicks, "down " + downTicks + " ticks, up " + upTicks);
    }

    @Test
    @DisplayName("1 to 25 and back: every step plays, the ULTIMATE closes and opens, and no frame leaves the cells")
    void theWholeTransformation() {
        final CrownMotion m = new CrownMotion();
        m.tick(fed(1));
        int ticks = settle(m, fed(25), 2000);
        assertTrue(m.ultimate(), "25 closes the crown");
        assertTrue(ticks >= 24 * CrownMotion.UP_SECTOR + CrownMotion.ULTIMATE_CLOSE, "24 steps and the close");
        final CrownPose p = m.current();
        assertEquals(1.0, p.yokeExt, 1e-12);
        assertEquals(1.0, p.capOpen, 1e-9);
        ticks = 0;
        final CrownState one = fed(1);
        while (ticks < 2000 && !(m.shown() == 1 && m.kind() == CrownMotion.Kind.NONE)) {
            tickChecked(m, one);
            ticks++;
        }
        assertEquals(1, m.shown());
        assertFalse(m.ultimate());
        assertEquals(0.0, m.current().yokeExt, 1e-12);
        assertEquals(1, count(m.current()));
    }

    @Test
    @DisplayName("the ULTIMATE: yoke over and locked, claw open, a ring of light for 12 ticks, the sigil")
    void theUltimate() {
        final CrownMotion m = new CrownMotion();
        m.tick(fed(24));
        final CrownState full = fed(25);
        int shockTicks = 0;
        double maxShock = 0;
        boolean closeSeen = false;
        int guard = 0;
        while (!(m.ultimate() && m.kind() == CrownMotion.Kind.NONE) && guard++ < 200) {
            tickChecked(m, full);
            closeSeen |= m.kind() == CrownMotion.Kind.ULTIMATE;
            if (m.current().shockAlpha > 0) {
                shockTicks++;
                maxShock = Math.max(maxShock, m.current().shockRadius);
            }
        }
        assertTrue(closeSeen, "the 25th sector's latch is followed by the close, its own move");
        assertTrue(m.ultimate());
        assertTrue(shockTicks >= 11 && shockTicks <= 12, "the ring of light runs 12 ticks: " + shockTicks);
        assertTrue(maxShock > CrownGeometry.RING_R[4] + 20, "it is thrown out past the crown: " + maxShock);
        for (int i = 0; i < CrownMotion.SIGIL_FADE; i++) {
            m.tick(full);
        }
        assertEquals(1.0, m.current().sigilAlpha, 1e-9);
        assertTrue(m.current().arch > 0.7, "sparks run from the yoke into the heart");
    }

    @Test
    @DisplayName("riding through: the whole crown turns warning orange and sags together, the feed sparks stop")
    void ridingThroughSagsTogether() {
        final CrownMotion m = new CrownMotion();
        m.tick(fed(25));
        for (int i = 0; i < 10; i++) {
            m.tick(fed(25));
        }
        final double fullSpeed = m.current().spin;
        double previousGlow = 2;
        for (int f = 0; f < 100; f++) {
            final double store = 1.0 - (f + 1) / 100.0;
            final CrownState riding = state(25, 25, CrownHold.Phase.RIDING, store, false, 0);
            m.tick(riding);
            final CrownPose p = m.current();
            for (int k = 0; k < 25; k++) {
                assertEquals(CrownPose.INK_WARN, p.ink[k], "every sector turns orange in the same tick");
            }
            final double g0 = p.glow[0];
            for (int k = 1; k < 25; k++) {
                assertEquals(g0, p.glow[k], 1e-12, "they sag together, not one by one");
            }
            assertFalse(p.feed, "nothing arrives: the horn sparks stop");
            assertEquals(CrownPose.PILOT_WARN, p.pilot);
            assertTrue(p.needleWarn);
            assertEquals(25, m.shown(), "every chunk is still held: nothing goes out one by one");
            if (f == CrownMotion.CLAW_SHUT) {
                assertEquals(0.0, p.capOpen, 1e-12, "the claw shuts on the heart in six ticks");
            }
            if (f > 30 && f % 10 == 0) {
                assertTrue(g0 <= previousGlow + 0.25, "the sag follows the store down");
                previousGlow = g0;
            }
        }
        final double lowSpeed = m.current().spin;
        assertTrue(lowSpeed < 0.45 * fullSpeed && lowSpeed > 0.25 * fullSpeed,
                "the spin runs down with the store toward 30% of working speed: " + lowSpeed + " of " + fullSpeed);
    }

    @Test
    @DisplayName("the blackout, in order: one hard flash, every sector cold at once, home with a bounce, the yoke"
            + " folds, the rings sink top first, the horns fold, the iris shuts")
    void theBlackoutOrder() {
        final CrownMotion m = new CrownMotion();
        m.tick(fed(25));
        for (int f = 0; f < 100; f++) {
            m.tick(state(25, 25, CrownHold.Phase.RIDING, 1.0 - (f + 1) / 100.0, false, 0));
        }
        final CrownState dark = state(0, 0, CrownHold.Phase.DARK, 0.0, false, 0);
        m.tick(dark);
        CrownPose p = m.current();
        assertEquals(CrownMotion.Kind.COLLAPSE, m.kind());
        assertEquals(1.0, p.flash, 1e-12, "a hard flash at every pod, in the tick the chunks go");
        for (int k = 0; k < 25; k++) {
            assertEquals(CrownPose.INK_OFF, p.ink[k], "every sector goes cold together");
        }
        m.tick(dark);
        assertEquals(0.0, m.current().flash, 1e-12, "one tick, then a hard cut");
        m.tick(dark);
        assertEquals(0.0, m.current().flash, 1e-12);
        int yokeFolded = -1;
        int topRingSinks = -1;
        int firstRingAfterTop = -1;
        int hornsFold = -1;
        int irisShuts = -1;
        double low = 1.0;
        boolean bounced = false;
        for (int age = 3; age <= CrownMotion.COLLAPSE + 2; age++) {
            tickChecked(m, dark);
            p = m.current();

            low = Math.min(low, p.mount[0]);
            if (low < 0.15 && p.mount[0] > low + 0.05) {
                bounced = true;
            }
            if (yokeFolded < 0 && p.yokeExt < 0.01) {
                yokeFolded = age;
            }
            if (topRingSinks < 0 && p.tierUp[4] < 0.99) {
                topRingSinks = age;
            }
            if (firstRingAfterTop < 0 && p.tierUp[1] < 0.99) {
                firstRingAfterTop = age;
            }
            if (hornsFold < 0 && p.hornTop < CrownGeometry.hornTopFor(25) - 0.5) {
                hornsFold = age;
            }
            if (irisShuts < 0 && p.hatch < 0.5) {
                irisShuts = age;
            }
        }
        assertTrue(bounced, "the sectors slide home and bounce");
        assertEquals(0.0, m.current().mount[0], 1e-12, "and come to rest home");
        assertTrue(yokeFolded > 0 && topRingSinks > yokeFolded, "the yoke folds before the rings sink");
        assertTrue(firstRingAfterTop > topRingSinks, "the rings sink top first");
        assertTrue(hornsFold > 0 && irisShuts > hornsFold, "the horns fold, then the iris shuts");
        assertEquals(0, m.shown());
        assertEquals(CrownMotion.Kind.NONE, m.kind());
        int red = 0;
        for (int i = 0; i < 40; i++) {
            m.tick(dark);
            red += m.current().pilot == CrownPose.PILOT_RED ? 1 : 0;
        }
        assertTrue(red >= 15 && red <= 25, "the pilot blinks red: " + red + " of 40 ticks");
    }

    @Test
    @DisplayName("a comeback rebuilds sector by sector as the station takes its chunks back")
    void aComebackRebuilds() {
        final CrownMotion m = new CrownMotion();
        m.tick(state(0, 0, CrownHold.Phase.DARK, 0.0, false, 0));
        for (int held = 1; held <= 12; held++) {
            final CrownState s = state(held, held, CrownHold.Phase.FED, 0.2, true, 0);
            for (int i = 0; i < 10; i++) {
                tickChecked(m, s);
                assertTrue(m.shown() <= held, "the crown runs ahead of the chunks it holds");
            }
        }
        settle(m, state(12, 12, CrownHold.Phase.FED, 0.2, true, 0), 200);
        assertEquals(12, m.shown());
        assertEquals(1.0, m.current().hatch, 1e-12, "the iris opened with the first chunk");
    }

    @Test
    @DisplayName("held but not ticking is an ember; ticking ripens it to rose")
    void emberThenRose() {
        final CrownMotion m = new CrownMotion();
        m.tick(fed(3));
        final CrownState loading = new CrownState(4, 4, 4, 0b0111, CrownHold.Phase.FED, 1f, 0L, 0L, 4_752, 4_752,
                4_752, 0, 4, 25, 50, 1, 65_536, 0, 0, 0, CrownState.Reason.NONE, 0L, false, 0, 0, 0, "", 0, 0);
        for (int i = 0; i < 40; i++) {
            m.tick(loading);
        }
        assertEquals(CrownMotion.EMBER, m.current().glow[3], 1e-9, "the fourth chunk is loading: an ember");
        assertEquals(1.0, m.current().glow[0], 1e-9);
        final CrownState ticking = fed(4);
        for (int i = 0; i < 40; i++) {
            m.tick(ticking);
        }
        assertEquals(1.0, m.current().glow[3], 1e-9, "it ripens once its chunk ticks");
    }

    @Test
    @DisplayName("a heartbeat change sends one pulse up the stem")
    void theHeartbeat() {
        final CrownMotion m = new CrownMotion();
        m.tick(state(5, 5, CrownHold.Phase.FED, 1, true, 3));
        m.tick(state(5, 5, CrownHold.Phase.FED, 1, true, 3));
        assertTrue(m.current().pulse < 0, "no pulse without a refresh");
        int pulsed = 0;
        for (int i = 0; i < 30; i++) {
            m.tick(state(5, 5, CrownHold.Phase.FED, 1, true, 4));
            pulsed += m.current().pulse >= 0 ? 1 : 0;
        }
        assertEquals(CrownMotion.PULSE + 1, pulsed, "one pulse, climbing for " + CrownMotion.PULSE + " ticks");
    }

    @Test
    @DisplayName("something in the way: the crown keeps its smaller form, the nixies keep the count held")
    void somethingInTheWay() {
        final CrownMotion m = new CrownMotion();
        final CrownState blocked = new CrownState(9, 9, 5, 0x1FF, CrownHold.Phase.FED, 1f, 0L, 0L, 13_905, 13_905,
                13_905, 0, 9, 25, 50, 1, 65_536, 0, 0, 0, CrownState.Reason.NONE, 0L, true, 0, 1, 0,
                "minecraft:stone", 0, 0);
        m.tick(blocked);
        for (int i = 0; i < 20; i++) {
            m.tick(blocked);
        }
        assertEquals(5, m.shown(), "the form is what has room");
        assertEquals(9, m.current().count, "the nixies are the chunks held");
        assertTrue(m.current().blocked);
        assertEquals(1, m.current().blockUp);
    }

    @Test
    @DisplayName("a renderer that loses sight of the crown for long shows the state instead of replaying it")
    void catchUp() {
        final CrownMotion m = new CrownMotion();
        m.advanceTo(100, fed(1));
        m.advanceTo(100 + CrownMotion.CATCH_UP + 50, fed(20));
        assertEquals(20, m.shown(), "snapped, no long replay");
        assertEquals(CrownMotion.Kind.NONE, m.kind());
        m.advanceTo(100 + CrownMotion.CATCH_UP + 51, fed(21));
        assertEquals(CrownMotion.Kind.UP, m.kind(), "a change seen live animates");
        assertTrue(m.advanced());
        m.advanceTo(100 + CrownMotion.CATCH_UP + 51, fed(21));
        assertFalse(m.advanced(), "a second frame in the same tick moves nothing");
    }

    private static int count(CrownPose p) {
        int c = 0;
        for (double v : p.mount) {
            c += v >= 1.0 ? 1 : 0;
        }
        return c;
    }
}
