package com.createstats.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeIdleLoadTest {
    private static final int DRAW = 6;

    @Test
    void aWellFedDeviceRunsAndPaysForIt() {
        final FeIdleLoad load = FeIdleLoad.of(false, 500, DRAW);
        assertTrue(load.powered());
        assertEquals(DRAW, load.spend());
        assertEquals(DRAW, load.rated());
        assertEquals(DRAW, load.measured());
    }

    @Test
    void aSuppressedDeviceStopsRatherThanRunningForFree() {
        final FeIdleLoad load = FeIdleLoad.of(true, 500, DRAW);
        assertFalse(load.powered(), "shed must mean stopped, not free");
        assertEquals(0, load.spend());
        assertEquals(0, load.measured());
    }

    @Test
    void aSuppressedDeviceHoldsNoBudget() {
        assertEquals(0, FeIdleLoad.of(true, 500, DRAW).rated());
        assertEquals(0, FeIdleLoad.of(true, 0, DRAW).rated());
    }

    @Test
    void aStarvedDeviceKeepsItsRatedDrawAndSpendsNothing() {
        final FeIdleLoad load = FeIdleLoad.of(false, DRAW - 1, DRAW);
        assertFalse(load.powered());
        assertEquals(DRAW, load.rated(), "a starved device still wants to run and still holds budget");
        assertEquals(0, load.spend());
        assertEquals(0, load.measured(), "and must not report spending what it could not get");
    }

    @Test
    void exactlyEnoughEnergyIsEnough() {
        final FeIdleLoad load = FeIdleLoad.of(false, DRAW, DRAW);
        assertTrue(load.powered(), "the buffer holding exactly one tick of draw is one more tick");
        assertEquals(DRAW, load.spend());
    }

    @Test
    void measuredNeverExceedsSpend() {
        for (boolean suppressed : new boolean[] {false, true}) {
            for (int energy : new int[] {0, 1, DRAW - 1, DRAW, 10_000}) {
                final FeIdleLoad load = FeIdleLoad.of(suppressed, energy, DRAW);
                assertEquals(load.spend(), load.measured(),
                        "suppressed=" + suppressed + " energy=" + energy);
                assertTrue(load.spend() <= energy, "must never spend a buffer into the negative");
            }
        }
    }

    @Test
    void aFreeDeviceIsPoweredAndCostsNothing() {
        final FeIdleLoad load = FeIdleLoad.of(false, 0, 0);
        assertTrue(load.powered());
        assertEquals(0, load.spend());
        assertEquals(0, load.rated());
    }

    @Test
    void aNegativeDrawIsTreatedAsFree() {
        final FeIdleLoad load = FeIdleLoad.of(false, 0, -50);
        assertTrue(load.powered());
        assertEquals(0, load.spend());
        assertEquals(0, load.rated());
    }

    @Test
    void theRawShedFlagIsNotTheTestToUse() {
        final FeManagedState managed = new FeManagedState();
        managed.setShed(true);
        assertTrue(FeIdleLoad.of(managed.isSuppressed(), 500, DRAW).powered() == false);

        managed.setLock(FeLock.FORCE_ON);
        managed.setShed(true);
        assertTrue(managed.isShed(), "the flag is still set");
        assertTrue(FeIdleLoad.of(managed.isSuppressed(), 500, DRAW).powered(),
                "but a force-on overrides it, which reading the flag would have missed");
    }

    @Test
    void forceOffStopsADeviceNothingHasShed() {
        final FeManagedState managed = new FeManagedState();
        managed.setLock(FeLock.FORCE_OFF);
        assertFalse(managed.isShed(), "nothing shed it; the player switched it off");
        final FeIdleLoad load = FeIdleLoad.of(managed.isSuppressed(), 500, DRAW);
        assertFalse(load.powered(), "reading isShed() here let a force-off device keep running");
        assertEquals(0, load.rated());
    }
}
