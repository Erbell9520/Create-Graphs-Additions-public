package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplerBufferTest {
    private static final int CAPACITY = 16384;
    private static final int RATE = 8192;

    @Test
    @DisplayName("a seam receive of N is undone exactly by a seam extract of N, at every fill")
    void seamReceiveIsExactlyReversible() {
        for (int start : new int[] {0, 1, 25, RATE, CAPACITY - 1, CAPACITY}) {
            for (int n : new int[] {0, 1, 25, RATE, CAPACITY, CAPACITY * 4}) {
                final CouplerBuffer buffer = new CouplerBuffer(CAPACITY);
                buffer.setEnergy(start);
                final int taken = buffer.seamReceive(n, false);
                assertEquals(start + taken, buffer.energy(),
                        "receive moved the buffer by something other than what it reported");
                final int back = buffer.seamExtract(taken, false);
                assertEquals(taken, back, "the rollback could not take back what it had just given");
                assertEquals(start, buffer.energy(), "start=" + start + " n=" + n);
            }
        }
    }

    @Test
    @DisplayName("a seam extract of N is undone exactly by a seam receive of N, at every fill")
    void seamExtractIsExactlyReversible() {
        for (int start : new int[] {0, 1, 25, RATE, CAPACITY - 1, CAPACITY}) {
            for (int n : new int[] {0, 1, 25, RATE, CAPACITY, CAPACITY * 4}) {
                final CouplerBuffer buffer = new CouplerBuffer(CAPACITY);
                buffer.setEnergy(start);
                final int given = buffer.seamExtract(n, false);
                assertEquals(start - given, buffer.energy());
                final int back = buffer.seamReceive(given, false);
                assertEquals(given, back, "the rollback could not give back what it had just taken");
                assertEquals(start, buffer.energy(), "start=" + start + " n=" + n);
            }
        }
    }

    @Test
    @DisplayName("a rolled back seam transfer leaves no trace in the tick's reading")
    void aRollbackNetsToZero() {
        final CouplerBuffer buffer = new CouplerBuffer(CAPACITY);
        buffer.roll(1L);
        final int taken = buffer.seamReceive(RATE, false);
        buffer.seamExtract(taken, false);
        assertEquals(0, buffer.seamMoved());
        assertEquals(0, buffer.energy());
    }

    @Test
    @DisplayName("a simulate on either port moves nothing")
    void simulateIsAQuestion() {
        final CouplerBuffer buffer = new CouplerBuffer(CAPACITY);
        buffer.setEnergy(1000);
        buffer.roll(1L);
        assertEquals(1000, buffer.seamExtract(5000, true));
        assertEquals(CAPACITY - 1000, buffer.seamReceive(CAPACITY * 2, true));
        assertEquals(1000, buffer.gridExtract(5000, true, RATE, 1L));
        assertEquals(1000, buffer.energy());
        assertEquals(0, buffer.seamMoved());
        assertEquals(0, buffer.gridMoved());
    }

    @Test
    @DisplayName("the seam port is never held to the rating, because a clamp there is a lying handler")
    void theSeamPortIsUncapped() {
        final CouplerBuffer buffer = new CouplerBuffer(CAPACITY);
        buffer.roll(1L);
        assertEquals(CAPACITY, buffer.seamReceive(Integer.MAX_VALUE, false));
        assertEquals(CAPACITY, buffer.seamMoved());
        assertEquals(CAPACITY, buffer.seamExtract(Integer.MAX_VALUE, false));
    }

    @Test
    @DisplayName("the grid port holds at the rating however many times it is called in one tick")
    void theGridPortHoldsAtTheRating() {
        final CouplerBuffer buffer = new CouplerBuffer(CAPACITY);
        buffer.setEnergy(CAPACITY);
        buffer.roll(1L);
        int total = 0;
        for (int i = 0; i < 50; i++) {
            total += buffer.gridExtract(1000, false, RATE, 1L);
        }
        assertEquals(RATE, total);
        assertEquals(RATE, buffer.gridMoved());
        assertEquals(CAPACITY - RATE, buffer.energy());
    }

    @Test
    @DisplayName("the two allowances are separate, so a seam transfer never spends the grid's")
    void theTwoSidesDoNotShareAnAllowance() {
        final CouplerBuffer buffer = new CouplerBuffer(CAPACITY);
        buffer.roll(1L);
        assertEquals(RATE, buffer.seamReceive(RATE, false));
        assertEquals(RATE, buffer.gridExtract(RATE, false, RATE, 1L));
        assertEquals(0, buffer.energy());
    }

    @Test
    @DisplayName("roll closes the tick that was open and reports it, once")
    void rollClosesTheOpenTick() {
        final CouplerBuffer buffer = new CouplerBuffer(CAPACITY);
        assertNull(buffer.roll(1L), "the first roll has no finished tick to report");
        buffer.seamReceive(500, false);
        buffer.gridExtract(300, false, RATE, 1L);
        assertNull(buffer.roll(1L), "a repeat within the same tick is not a new tick");
        final CouplerBuffer.Tick closed = buffer.roll(2L);
        assertNotNull(closed);
        assertEquals(500, closed.seamMoved());
        assertEquals(300, closed.gridMoved());
        assertEquals(0, buffer.seamMoved());
    }

    @Test
    @DisplayName("no sequence of calls can ever put the buffer outside its bounds")
    void theBufferIsAlwaysWithinBounds() {
        final CouplerBuffer buffer = new CouplerBuffer(CAPACITY);
        final Random random = new Random(20260909L);
        for (int tick = 0; tick < 2000; tick++) {
            buffer.roll(tick);
            for (int call = 0; call < 8; call++) {
                final int amount = random.nextInt(CAPACITY * 2) - CAPACITY / 2;
                switch (random.nextInt(4)) {
                    case 0 -> buffer.seamReceive(amount, false);
                    case 1 -> buffer.seamExtract(amount, false);
                    case 2 -> buffer.gridReceive(amount, false, RATE, tick);
                    default -> buffer.gridExtract(amount, false, RATE, tick);
                }
                assertTrue(buffer.energy() >= 0 && buffer.energy() <= CAPACITY,
                        "energy left its bounds: " + buffer.energy());
            }
            assertTrue(buffer.gridMoved() <= RATE, "the grid allowance was exceeded");
        }
    }

    @Test
    @DisplayName("a saved figure larger than the buffer is clamped rather than trusted")
    void setEnergyClamps() {
        final CouplerBuffer buffer = new CouplerBuffer(CAPACITY);
        buffer.setEnergy(CAPACITY * 10);
        assertEquals(CAPACITY, buffer.energy());
        buffer.setEnergy(-5);
        assertEquals(0, buffer.energy());
    }

    @Test
    @DisplayName("the grid allowance belongs to the game tick it is spent in, not to the Coupler's own roll")
    void theGridAllowanceIsKeyedOnGameTime() {
        final CouplerBuffer buffer = new CouplerBuffer(CAPACITY);
        buffer.setEnergy(CAPACITY);
        assertEquals(RATE, buffer.gridExtract(RATE, false, RATE, 10L));
        assertEquals(0, buffer.gridExtract(RATE, false, RATE, 10L), "the same tick has nothing left");

        buffer.setEnergy(CAPACITY);
        assertEquals(RATE, buffer.gridExtract(RATE, false, RATE, 11L));

        buffer.setEnergy(CAPACITY);
        buffer.roll(11L);
        buffer.roll(12L);
        assertEquals(0, buffer.gridExtract(RATE, false, RATE, 11L), "a roll is not a new game tick");
    }

    @Test
    @DisplayName("a grid simulate reads the allowance and spends none of it")
    void aGridSimulateSpendsNothing() {
        final CouplerBuffer buffer = new CouplerBuffer(CAPACITY);
        buffer.setEnergy(CAPACITY);
        for (int i = 0; i < 5; i++) {
            assertEquals(RATE, buffer.gridExtract(RATE, true, RATE, 3L));
        }
        assertEquals(RATE, buffer.gridAllowance(RATE, 3L));
        assertEquals(RATE, buffer.gridExtract(RATE, false, RATE, 3L));
        assertEquals(0, buffer.gridAllowance(RATE, 3L));
    }
}
