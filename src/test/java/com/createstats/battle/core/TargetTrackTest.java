package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetTrackTest {
    @Test
    void aSteadyTargetsVelocityIsRecoveredExactly() {
        TargetTrack t = new TargetTrack();
        for (long tick = 100; tick <= 110; tick++) {
            double n = tick - 100;
            t.observe(tick, 5.0 + 0.3 * n, 64.0 - 0.1 * n, -7.0 + 0.2 * n);
        }
        assertTrue(t.hasFix());
        assertTrue(t.hasVelocity());
        assertArrayEquals(new double[]{0.3, -0.1, 0.2}, t.velocity(), 1.0E-12);
        assertArrayEquals(new double[]{8.0, 63.0, -5.0}, t.position(), 1.0E-12);
        assertEquals(110L, t.lastSeen());
    }

    @Test
    void aNewRawVelocityWeighsSixtyFivePercentOverAFourTickWindow() {
        TargetTrack t = new TargetTrack();
        t.observe(0, 0.0, 0.0, 0.0);
        assertArrayEquals(new double[]{0, 0, 0}, t.velocity(), 0.0, "zero until two sightings");
        assertFalse(t.hasVelocity());

        t.observe(1, 1.0, 0.0, 0.0);
        assertEquals(1.0, t.velocity()[0], 1.0E-12);

        t.observe(2, 3.0, 0.0, 0.0);
        assertEquals(1.325, t.velocity()[0], 1.0E-12);

        t.observe(3, 6.0, 0.0, 0.0);
        assertEquals(1.76375, t.velocity()[0], 1.0E-12);

        t.observe(4, 10.0, 0.0, 0.0);
        assertEquals(2.2423125, t.velocity()[0], 1.0E-12);

        t.observe(5, 15.0, 0.0, 0.0);
        assertEquals(0.65 * 3.5 + 0.35 * 2.2423125, t.velocity()[0], 1.0E-12);
    }

    @Test
    void aTeleportIsNotMotion() {
        TargetTrack t = new TargetTrack();
        for (int i = 0; i <= 6; i++) {
            t.observe(i, 0.5 * i, 70.0, 0.0);
        }
        assertEquals(0.5, t.velocity()[0], 1.0E-12);
        t.observe(7, 200.0, 70.0, 0.0);
        assertEquals(0.5, t.velocity()[0], 1.0E-12, "the jump is not a velocity");
        assertArrayEquals(new double[]{200, 70, 0}, t.position(), 0.0, "but it is where the target is now");

        t.observe(8, 199.75, 70.0, 0.0);
        assertEquals(-0.25, t.velocity()[0], 1.0E-12);

        t.observe(9, 199.5, 70.0, 0.0);
        assertEquals(-0.25, t.velocity()[0], 1.0E-12);

        TargetTrack fast = new TargetTrack();
        fast.observe(0, 0.0, 0.0, 0.0);
        fast.observe(1, 8.0, 0.0, 0.0);
        assertEquals(8.0, fast.velocity()[0], 1.0E-12);
        TargetTrack jump = new TargetTrack();
        jump.observe(0, 0.0, 0.0, 0.0);
        jump.observe(1, 8.001, 0.0, 0.0);
        assertFalse(jump.hasVelocity());
    }

    @Test
    void aGapOverTenTicksRestartsTheWindow() {
        TargetTrack t = new TargetTrack();
        for (int i = 10; i <= 20; i++) {
            t.observe(i, 0.2 * i, 64.0, 0.0);
        }
        for (int i = 21; i <= 35; i++) {
            t.unseen(i);
        }
        t.observe(36, 34.0, 64.0, 0.0);
        assertArrayEquals(new double[]{0.2, 0, 0}, t.velocity(), 1.0E-12);

        t.observe(37, 34.0, 64.0, 0.4);
        assertArrayEquals(new double[]{0, 0, 0.4}, t.velocity(), 1.0E-12);

        TargetTrack b = new TargetTrack();
        b.observe(0, 0.0, 0.0, 0.0);
        b.observe(1, 1.0, 0.0, 0.0);
        b.observe(11, 21.0, 0.0, 0.0);
        assertEquals(1.65, b.velocity()[0], 1.0E-12);
    }

    @Test
    void contactIsLostAfterTenTicksAndTheTargetExpiresAfterSixtySeconds() {
        TargetTrack t = new TargetTrack();
        t.observe(1000, 0.0, 0.0, 0.0);
        assertFalse(t.lostContact(1010), "10 ticks unseen is still contact: more than MAX_GAP is lost");
        assertTrue(t.lostContact(1011));
        assertFalse(t.expired(2199));
        assertTrue(t.expired(2200), "LOST_CLEAR, 1,200 ticks, is dropped");

        t.observe(1500, 1.0, 0.0, 0.0);
        assertFalse(t.lostContact(1500));
        assertFalse(t.expired(2699));
        assertTrue(t.expired(2700));

        TargetTrack never = new TargetTrack();
        assertFalse(never.lostContact(5000), "nothing called yet, nothing lost");
        assertFalse(never.expired(5000));
        never.unseen(50);
        assertFalse(never.lostContact(60));
        assertTrue(never.lostContact(61));
        assertFalse(never.expired(1249));
        assertTrue(never.expired(1250));
        assertFalse(never.hasFix());
        assertEquals(Long.MIN_VALUE, never.lastSeen());
    }

    @Test
    void predictionDeadReckonsFromTheLastSighting() {
        TargetTrack t = new TargetTrack();
        for (int i = 190; i <= 200; i++) {
            t.observe(i, 0.5 * i, 90.0, -0.25 * i);
        }
        for (int i = 201; i <= 205; i++) {
            t.unseen(i);
        }
        assertArrayEquals(new double[]{100.0 + 7.5, 90.0, -50.0 - 3.75}, t.predict(205, 10.0), 1.0E-9);
        assertArrayEquals(t.position(), t.predict(200, 0.0), 0.0, "no time, no travel");
        assertArrayEquals(new double[]{0, 0, 0}, new TargetTrack().predict(10, 5.0), 0.0, "no fix: zeros");
    }

    @Test
    void aSecondSightingInOneTickMovesThePositionOnly() {
        TargetTrack t = new TargetTrack();
        t.observe(0, 0.0, 0.0, 0.0);
        t.observe(1, 1.0, 0.0, 0.0);
        t.observe(1, 1.5, 0.0, 0.0);
        assertEquals(1.0, t.velocity()[0], 1.0E-12, "the velocity is the first sighting's");
        assertEquals(1.5, t.position()[0], 0.0);

        t.observe(0, 50.0, 0.0, 0.0);
        assertEquals(1.5, t.position()[0], 0.0);
        assertEquals(1L, t.lastSeen());
    }

    @Test
    void resetForgetsEverythingAndTheAccessorsHandOutCopies() {
        TargetTrack t = new TargetTrack();
        t.observe(0, 1.0, 2.0, 3.0);
        t.observe(1, 2.0, 2.0, 3.0);
        t.position()[0] = 99.0;
        t.velocity()[0] = 99.0;
        assertEquals(2.0, t.position()[0], 0.0);
        assertEquals(1.0, t.velocity()[0], 1.0E-12);
        t.reset();
        assertFalse(t.hasFix());
        assertFalse(t.hasVelocity());
        assertArrayEquals(new double[]{0, 0, 0}, t.position(), 0.0);
        assertArrayEquals(new double[]{0, 0, 0}, t.velocity(), 0.0);
        assertEquals(Long.MIN_VALUE, t.lastSeen());
        assertFalse(t.lostContact(100000), "a reset track has no clock running");

        t.observe(50, 0.0, 0.0, 0.0);
        t.observe(51, 0.0, 0.5, 0.0);
        assertArrayEquals(new double[]{0, 0.5, 0}, t.velocity(), 1.0E-12);
    }
}
