package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapacitorDischargeTest {
    @Test
    @DisplayName("the ramp's allowances sum to the capacity exactly, over sixteen seconds")
    void rampIntegratesToCapacity() {
        long sum = 0;
        for (int t = 0; t < CapacitorDischarge.BURST_TICKS; t++) {
            sum += CapacitorDischarge.allowanceAt(t);
        }
        assertEquals(CapacitorDischarge.CAPACITY, sum);
        assertEquals(10_485_760, CapacitorDischarge.CAPACITY, "65,536 falling to zero over sixteen seconds");
        assertEquals(320, CapacitorDischarge.BURST_TICKS);
    }

    @Test
    @DisplayName("it starts within a quarter percent of the peak and falls every tick, never below zero")
    void rampFallsFromThePeak() {
        final int first = CapacitorDischarge.allowanceAt(0);
        assertTrue(first <= CapacitorDischarge.PEAK_FE_PER_TICK, "never above the headline rate");
        assertTrue(first >= CapacitorDischarge.PEAK_FE_PER_TICK * 0.9975, "first tick " + first);
        int prev = first;
        for (int t = 1; t < CapacitorDischarge.BURST_TICKS; t++) {
            final int a = CapacitorDischarge.allowanceAt(t);
            assertTrue(a <= prev, "tick " + t + " rose from " + prev + " to " + a);
            assertTrue(a > 0, "tick " + t + " is inside the burst and must still give something");
            prev = a;
        }
        assertEquals(0, CapacitorDischarge.allowanceAt(-1));
        assertEquals(0, CapacitorDischarge.allowanceAt(CapacitorDischarge.BURST_TICKS));
    }

    @Test
    @DisplayName("halfway through, a quarter of the charge is left: the square law the jars drain by")
    void squareLaw() {
        final long left = CapacitorDischarge.CAPACITY - CapacitorDischarge.drainedAfter(CapacitorDischarge.BURST_TICKS / 2);
        assertEquals(CapacitorDischarge.CAPACITY / 4, left);
    }

    @Test
    @DisplayName("a full refill takes 3,414 ticks at 3,072 FE/t, and the whine falls an octave, 1.0 to 0.5")
    void chargeAndPitch() {
        assertEquals(3_072, CapacitorDischarge.CHARGE_FE_PER_TICK);
        assertEquals(3_414, CapacitorDischarge.ticksToFull(0));
        assertEquals(0, CapacitorDischarge.ticksToFull(CapacitorDischarge.CAPACITY));
        assertEquals(1, CapacitorDischarge.ticksToFull(CapacitorDischarge.CAPACITY - 1));
        assertEquals(1.0f, CapacitorDischarge.whinePitch(1f), 1e-6, "never above the file's own pitch");
        assertEquals(0.5f, CapacitorDischarge.whinePitch(0f), 1e-6);
    }
}
