package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropLearnerTest {
    private static final class Plant {
        final double lerp;

        double ratio;

        final double thrustPerRpm;
        int commanded;
        int applied;
        long tick;
        long rebuilt = -100;
        double rotation;

        Plant(double lerp, double ratio, double thrustPerRpm) {
            this.lerp = lerp;
            this.ratio = ratio;
            this.thrustPerRpm = thrustPerRpm;
        }

        void step() {
            if (commanded != applied && tick - rebuilt >= 2) {
                applied = commanded;
                rebuilt = tick;
            }
            rotation += lerp * (ratio * applied - rotation);
            tick++;
        }

        double along() {
            return thrustPerRpm * rotation;
        }

        double shaft() {
            return ratio * applied;
        }

        boolean observe(PropLearner l) {
            return l.observe(applied, along(), 0.1 * along(), shaft());
        }
    }

    private static int oldSign(Plant p) {
        return p.along() * p.applied > 0.0 ? 1 : -1;
    }

    @Test
    void aReversalNeverTurnsTheSignRound() {
        reversal(new Plant(0.15, -2.0, 1.0), -1, 2.0);

        double gain = 0.2 * Math.pow(32.0, 1.5);
        reversal(new Plant(0.4 / Math.sqrt(32.0), 1.0, gain), 1, gain);
    }

    private static void reversal(Plant p, int sign, double gain) {
        PropLearner l = new PropLearner();
        p.commanded = 3;
        for (int i = 0; i < 20; i++) {
            p.step();
            p.observe(l);
        }
        assertTrue(l.signSeen, "the creep from a standstill shows the sign");
        assertEquals(sign, l.sign);
        assertEquals(Math.abs(p.ratio), l.ratio, 1.0e-9, "the gearing is read at once");
        p.commanded = 200;
        for (int i = 0; i < 200; i++) {
            p.step();
            p.observe(l);
            assertEquals(sign, l.sign);
            if (l.known()) assertEquals(gain, l.gain, 0.1 * gain, "a gain is only ever taken from a propeller that has caught up");
        }
        assertEquals(gain, l.gain, 0.02 * gain);
        int oldFlips = 0;
        p.commanded = -30;
        for (int i = 0; i < 200; i++) {
            p.step();
            p.observe(l);
            if (oldSign(p) != sign) oldFlips++;
            assertEquals(sign, l.sign, "the sign held through the reversal, tick " + i);
            if (l.known()) assertEquals(gain, l.gain, 0.1 * gain);
        }
        assertTrue(oldFlips > PropLearner.SIGN_READINGS,
                "the plant must set the trap: the old rule read " + oldFlips + " ticks of this reversal as a flipped gearbox");
    }

    @Test
    void brakingFromFullCommandsOneWayUntilThePropellerTurns() {
        int newFlips = brake(false);
        int oldFlips = brake(true);
        assertEquals(0, newFlips, "the command changed sign while the propeller was turning round");
        assertTrue(oldFlips >= 3, "the old rule should have chattered here, and did " + oldFlips + " times");
    }

    private static int brake(boolean oldRule) {
        Plant p = new Plant(0.15, 1.0, 1.0);
        PropLearner l = new PropLearner();
        int sign = 1;
        p.commanded = 3;
        for (int i = 0; i < 20; i++) {
            p.step();
            p.observe(l);
        }
        p.commanded = 256;
        for (int i = 0; i < 80; i++) {
            p.step();
            p.observe(l);
        }
        int flips = 0;
        int last = 0;
        for (int i = 0; i < 80; i++) {
            if (oldRule) sign = oldSign(p);
            else sign = l.sign;

            int rpm = (int) Math.round(-120.0 / sign);
            if (last != 0 && Integer.signum(rpm) != Integer.signum(last)) flips++;
            last = rpm;
            p.commanded = rpm;
            p.step();
            p.observe(l);
        }
        assertTrue(p.along() < 0.0 || oldRule, "the propeller turned round and pushes back");
        return flips;
    }

    @Test
    void aSlowBearingIsMeasuredOnlyOnceItHasCaughtUp() {
        double gain = 0.2 * Math.pow(32.0, 1.5);
        Plant p = new Plant(0.4 / Math.sqrt(32.0), 1.0, gain);
        PropLearner l = new PropLearner();
        p.commanded = 100;
        for (int i = 0; i < 200; i++) {
            p.step();
            p.observe(l);
            if (l.known()) assertTrue(l.gain > 0.9 * gain, "measured at " + l.gain + " of " + gain + " while spinning up, tick " + i);
        }
        assertEquals(gain, l.gain, 0.02 * gain);
        assertEquals(0.1 * gain, l.flow, 0.002 * gain, "airflow per governor RPM, from the same steady readings");
    }

    @Test
    void aGearboxRebuiltInFlightIsPickedUp() {
        Plant p = new Plant(0.15, 1.0, 1.0);
        PropLearner l = new PropLearner();
        p.commanded = 100;
        for (int i = 0; i < 100; i++) {
            p.step();
            p.observe(l);
        }
        assertEquals(1, l.sign);
        p.ratio = -1.0;
        int t = 0;
        while (l.sign == 1 && t++ < 200) {
            p.step();
            p.observe(l);
        }
        assertEquals(-1, l.sign, "a propeller that settled pushing the other way turns the sign round");
        assertTrue(t < 80, "took " + t + " ticks");
    }

    @Test
    void aPropellerStillUnwindingAnOldSpinIsNotReadBackwards() {
        for (double lerp : new double[] {0.15, 0.4 / Math.sqrt(32.0)}) {
            Plant p = new Plant(lerp, 1.0, 1.0);
            p.rotation = -100.0;
            p.applied = -100;
            PropLearner l = new PropLearner();
            p.commanded = 3;
            for (int i = 0; i < 200; i++) {
                p.step();
                p.observe(l);
                if (l.signSeen) assertEquals(1, l.sign, "read backwards at tick " + i + ", lerp " + lerp);
            }
            assertTrue(l.signSeen);
        }
    }

    @Test
    void aCrawlTeachesNothing() {
        PropLearner l = new PropLearner();
        for (int i = 0; i < 50; i++) assertFalse(l.observe(1, 0.5, 0.05, 1.0));
        assertFalse(l.signSeen);
        assertFalse(l.known());
        assertTrue(Double.isNaN(l.ratio));
    }

    @Test
    void aSaveComesBackWithItsSign() {
        PropLearner l = PropLearner.restored(-1, 2.5, Double.NaN, 1.5);
        assertTrue(l.signSeen);
        assertEquals(-1, l.sign);
        assertEquals(2.5, l.gain, 1.0e-12);
        assertTrue(Double.isNaN(l.flow));
        assertEquals(1.5, l.ratio, 1.0e-12);
        assertFalse(PropLearner.restored(1, Double.NaN, Double.NaN, Double.NaN).known());
    }
}
