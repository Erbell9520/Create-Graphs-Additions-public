package com.createstats.contactor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactorMotionTest {
    private static double flight(boolean energised) {
        final ContactorMotion.Armature a = new ContactorMotion.Armature();
        a.place(!energised);
        double t = 0.0;
        while (t < 1.0) {
            final double struck = a.advance(1.0 / 60.0, energised);
            t += 1.0 / 60.0;
            if (struck > 0.0 && a.lastStop() == (energised ? 1 : -1)) {
                return t;
            }
        }
        return Double.MAX_VALUE;
    }

    @Test
    @DisplayName("pull-in takes about a tenth of a second and drop-out about the same, both inside the dwell")
    void flightTimes() {
        final double in = flight(true);
        final double out = flight(false);
        assertTrue(in > 0.07 && in < 0.13, "pull-in took " + in);
        assertTrue(out > 0.07 && out < 0.13, "drop-out took " + out);

        assertTrue(in < ContactorLogic.DWELL_TICKS / 20.0 && out < ContactorLogic.DWELL_TICKS / 20.0);
    }

    @Test
    @DisplayName("the magnet creeps then slams: the second half of the stroke takes far less than the first")
    void pullInAccelerates() {
        final ContactorMotion.Armature a = new ContactorMotion.Armature();
        a.place(false);
        double t = 0.0;
        double half = -1.0;
        while (a.fraction() < 1.0 && t < 1.0) {
            a.advance(0.001, true);
            t += 0.001;
            if (half < 0.0 && a.fraction() >= 0.5) {
                half = t;
            }
        }
        assertTrue(half > 0.0);
        assertTrue(t - half < half * 0.4, "first half " + half + " s, second " + (t - half) + " s");
    }

    @Test
    @DisplayName("an armature resting on a stop with its force into it stays put and logs no strikes")
    void restingIsQuiet() {
        final ContactorMotion.Armature a = new ContactorMotion.Armature();
        a.place(true);
        for (int i = 0; i < 100; i++) {
            assertEquals(0.0, a.advance(1.0 / 60.0, true), "a strike logged while resting on the pole faces");
        }
        assertTrue(a.settled());
        a.place(false);
        for (int i = 0; i < 100; i++) {
            assertEquals(0.0, a.advance(1.0 / 60.0, false));
        }
        assertEquals(0.0, a.fraction());
    }

    @Test
    @DisplayName("dropping out, it strikes the backstop and rebounds visibly before it settles")
    void dropOutRebounds() {
        final ContactorMotion.Armature a = new ContactorMotion.Armature();
        a.place(true);
        boolean struck = false;
        double peak = 0.0;
        for (int i = 0; i < 400; i++) {
            final double hit = a.advance(0.001, false);
            if (hit > 0.0) {
                struck = true;
                assertEquals(-1, a.lastStop());
            }
            if (struck) {
                peak = Math.max(peak, a.fraction());
            }
        }
        assertTrue(struck);
        assertTrue(peak * ContactorGeometry.STROKE > 0.08, "rebound of " + peak * ContactorGeometry.STROKE + " px");
        assertTrue(peak < 0.3, "a rebound, not a second pull-in");
        assertTrue(a.settled());
    }

    @Test
    @DisplayName("a coil dropping out mid-flight turns the armature round from where it is")
    void reversalMidFlight() {
        final ContactorMotion.Armature a = new ContactorMotion.Armature();
        a.place(false);
        for (int i = 0; i < 70; i++) {
            a.advance(0.001, true);
        }
        final double mid = a.fraction();
        assertTrue(mid > 0.05 && mid < 1.0, "mid flight at " + mid);
        a.advance(0.002, false);
        assertTrue(a.fraction() <= mid + 0.05, "no jump to a stop");
        for (int i = 0; i < 400; i++) {
            a.advance(0.001, false);
        }
        assertEquals(0.0, a.fraction());
    }

    @Test
    @DisplayName("a frame hitch cannot throw an armature through itself")
    void hitchIsClamped() {
        final ContactorMotion.Armature a = new ContactorMotion.Armature();
        a.place(false);
        a.advance(5.0, true);

        assertTrue(a.fraction() > 0.0 && a.fraction() < 1.0,
                "a five second frame advanced only " + ContactorMotion.MAX_FRAME + " s: at " + a.fraction());
    }

    @Test
    @DisplayName("the middle pole rides the bar's centre: half the stroke on one armature, all of it on both")
    void poleDrives() {
        final double s = ContactorGeometry.STROKE;
        assertEquals(s / 2.0, ContactorMotion.poleDrive(ContactorLogic.MIDDLE, s, 0.0), 1e-9);
        assertEquals(s, ContactorMotion.poleDrive(ContactorLogic.MIDDLE, s, s), 1e-9);
        assertEquals(s, ContactorMotion.poleDrive(ContactorLogic.LEFT, s, 0.0), 1e-9);
        assertEquals(0.0, ContactorMotion.poleDrive(ContactorLogic.RIGHT, s, 0.0), 1e-9);
    }

    @Test
    @DisplayName("on screen a bridge is made exactly when the truth table says, in both forms")
    void madeMatchesTheTruthTable() {
        final double s = ContactorGeometry.STROKE;
        for (boolean nc : new boolean[] {false, true}) {
            for (boolean l : new boolean[] {false, true}) {
                for (boolean r : new boolean[] {false, true}) {
                    for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
                        final double drive = ContactorMotion.poleDrive(pole, l ? s : 0.0, r ? s : 0.0);
                        assertEquals(ContactorLogic.poleClosed(pole, l, r, nc), ContactorMotion.made(drive, nc),
                                "pole " + pole + " left " + l + " right " + r + " nc " + nc);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("a bridge is stopped by its contacts: the rest of the stroke is wipe, and the middle hangs clear on one coil")
    void bridgesStopOnTheirContacts() {
        final double s = ContactorGeometry.STROKE;
        assertEquals(ContactorGeometry.NO_GAP, ContactorMotion.bridgeDrop(s, false), 1e-9);
        assertEquals(0.0, ContactorMotion.bridgeDrop(ContactorGeometry.NC_HOLD, true), 1e-9);
        assertEquals(s - ContactorGeometry.NC_HOLD, ContactorMotion.bridgeDrop(s, true), 1e-9);

        assertTrue(ContactorGeometry.NO_GAP - ContactorMotion.bridgeDrop(s / 2.0, false) >= 0.4);
        assertTrue(ContactorGeometry.NC_HOLD - s / 2.0 >= 0.2);
    }

    @Test
    @DisplayName("the bar tilts with its left end down when the left armature is in, level when both match")
    void tilt() {
        final double s = ContactorGeometry.STROKE;
        assertTrue(ContactorMotion.tiltDegrees(s, 0.0) > 15.0);
        assertEquals(-ContactorMotion.tiltDegrees(s, 0.0), ContactorMotion.tiltDegrees(0.0, s), 1e-9);
        assertEquals(0.0, ContactorMotion.tiltDegrees(s, s), 1e-9);
        assertEquals(0.0, ContactorMotion.tiltDegrees(0.0, 0.0), 1e-9);
    }

    @Test
    @DisplayName("a landing bounces three times inside its window, never higher than the cap, and stops")
    void bounce() {
        final double amp = ContactorMotion.bounceAmplitude(1_000.0);
        assertEquals(ContactorMotion.MAX_BOUNCE, amp, 1e-9);
        assertEquals(0.0, ContactorMotion.bounce(ContactorMotion.BOUNCE_SECONDS, amp));
        assertEquals(0.0, ContactorMotion.bounce(-0.001, amp));
        int lifts = 0;
        boolean up = false;
        for (double t = 0.0; t < ContactorMotion.BOUNCE_SECONDS; t += 0.0005) {
            final double b = ContactorMotion.bounce(t, amp);
            assertTrue(b >= 0.0 && b <= amp + 1e-9);
            if (b > 0.01 && !up) {
                lifts++;
                up = true;
            } else if (b < 0.005) {
                up = false;
            }
        }
        assertEquals(3, lifts);
    }

    @Test
    @DisplayName("a flag turns from I to O and back, with a settle, in about a tenth of a second")
    void flags() {
        assertEquals(180.0, ContactorMotion.flagTurn(0.0, true), 1e-9);
        assertEquals(0.0, ContactorMotion.flagTurn(ContactorMotion.FLIP_SECONDS, true), 1e-9);
        assertEquals(0.0, ContactorMotion.flagTurn(0.0, false), 1e-9);
        assertEquals(180.0, ContactorMotion.flagTurn(1.0, false), 1e-9);
        final double mid = ContactorMotion.flagTurn(ContactorMotion.FLIP_SECONDS / 2.0, false);
        assertTrue(mid > 60.0 && mid < 200.0, "half way through the turn: " + mid);
    }

    @Test
    @DisplayName("a pilot strikes past its steady glow and settles; a dead one fades rather than blinks")
    void pilots() {
        assertEquals(0.0, ContactorMotion.pilotGlow(0.0, true), 1e-9);
        assertTrue(ContactorMotion.pilotGlow(ContactorMotion.STRIKE_SECONDS, true) > 1.2);
        assertEquals(1.0, ContactorMotion.pilotGlow(1.0, true), 1e-9);
        assertEquals(1.0, ContactorMotion.pilotGlow(0.0, false), 1e-9);
        assertTrue(ContactorMotion.pilotGlow(0.05, false) > 0.3);
        assertTrue(ContactorMotion.pilotGlow(0.4, false) < 0.01);
    }

    @Test
    @DisplayName("an arc is brightest at the break, climbs into its chute and is gone in a few frames")
    void arcs() {
        assertEquals(1.0, ContactorMotion.arcGlow(0.0), 1e-9);
        assertEquals(0.0, ContactorMotion.arcGlow(ContactorMotion.ARC_SECONDS), 1e-9);
        assertEquals(0.0, ContactorMotion.arcRise(0.0), 1e-9);
        assertEquals(ContactorMotion.ARC_RISE, ContactorMotion.arcRise(1.0), 1e-9);
        assertTrue(ContactorMotion.arcRise(0.05) > 0.0 && ContactorMotion.arcRise(0.05) < ContactorMotion.ARC_RISE);
    }

    private static int run(ContactorMotion.Unit unit, long[] clock, double seconds, boolean l, boolean r, boolean lc,
                           boolean rc, boolean nc, int[] loads) {
        int broke = 0;
        for (double t = 0.0; t < seconds; t += 1.0 / 60.0) {
            clock[0] += 16_666_667L;
            broke |= unit.advance(clock[0], l, r, lc, rc, nc, loads);
        }
        return broke;
    }

    @Test
    @DisplayName("a unit placed at rest shows the blockstate's poles at once, with no animation and no arc")
    void aUnitStartsWhereTheBlockIs() {
        final ContactorMotion.Unit unit = new ContactorMotion.Unit();
        assertEquals(0, unit.advance(0L, true, false, true, false, false, new int[] {500, 0, 0}));
        assertTrue(unit.made(ContactorLogic.LEFT));
        assertFalse(unit.made(ContactorLogic.MIDDLE));
        assertEquals(0.0, unit.flagTurn(ContactorLogic.LEFT), 1e-9);
    }

    @Test
    @DisplayName("pulling the left armature in makes the left pole on screen, and dropping it out under load breaks it with an arc")
    void aUnitFollowsAndArcs() {
        final ContactorMotion.Unit unit = new ContactorMotion.Unit();
        final long[] clock = {0L};
        final int[] loads = {2_000, 0, 0};
        unit.advance(clock[0], false, false, false, false, false, loads);
        assertEquals(0, run(unit, clock, 0.3, true, false, true, false, false, loads));
        assertTrue(unit.made(ContactorLogic.LEFT));
        assertFalse(unit.made(ContactorLogic.MIDDLE), "one coil never makes the middle");
        final int broke = run(unit, clock, 0.3, false, false, false, false, false, loads);
        assertEquals(1 << ContactorLogic.LEFT, broke);
        assertFalse(unit.made(ContactorLogic.LEFT));

        run(unit, clock, 0.3, true, true, true, true, false, new int[3]);
        assertTrue(unit.made(ContactorLogic.MIDDLE));
        assertEquals(0, run(unit, clock, 0.3, false, false, false, false, false, new int[3]));
    }

    @Test
    @DisplayName("a form change flips every pole with the armatures standing")
    void aFormChangeFlipsThePoles() {
        final ContactorMotion.Unit unit = new ContactorMotion.Unit();
        final long[] clock = {0L};
        unit.advance(clock[0], false, false, false, false, false, new int[3]);
        run(unit, clock, 0.05, false, false, false, false, true, new int[3]);
        for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
            assertTrue(unit.made(pole), "normally closed and dead: pole " + pole + " made");
        }
    }

    @Test
    @DisplayName("a contactor not drawn for a while is placed where it now is rather than animating stale moves")
    void aLongGapReplaces() {
        final ContactorMotion.Unit unit = new ContactorMotion.Unit();
        unit.advance(0L, false, false, false, false, false, new int[] {800, 800, 800});

        assertEquals(0, unit.advance(2_000_000_000L, true, false, true, false, false, new int[] {800, 800, 800}));
        assertTrue(unit.made(ContactorLogic.LEFT));
        assertEquals(1.0, unit.left().fraction(), 1e-9);
    }
}
