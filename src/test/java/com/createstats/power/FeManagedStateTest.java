package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [1] */
class FeManagedStateTest {

    @Test
    @DisplayName("a fresh device is Normal tier with no override")
    void defaultsAreNormalAndUnlocked() {
        final FeManagedState state = new FeManagedState();
        assertEquals(FePriority.DEFAULT, state.priority());
        assertEquals(FeLock.NONE, state.lock());
        assertFalse(state.isSuppressed());
    }

    @Test
    @DisplayName("a tier survives being set, which is the whole complaint")
    void tierSticks() {
        final FeManagedState state = new FeManagedState();
        state.setPriority(FePriority.CRITICAL);
        assertEquals(FePriority.CRITICAL, state.priority());
        assertEquals(FePriority.CRITICAL.ordinal(), state.priorityOrdinal());
    }

    @Test
    @DisplayName("force off suppresses the device outright")
    void forceOffSuppresses() {
        final FeManagedState state = new FeManagedState();
        state.setLock(FeLock.FORCE_OFF);
        assertTrue(state.isSuppressed());
        assertTrue(state.isForcedOff());
    }

    @Test
    @DisplayName("force on beats a shed instruction, because an explicit order outranks a heuristic")
    void forceOnOutranksShedding() {
        final FeManagedState state = new FeManagedState();
        state.setLock(FeLock.FORCE_ON);
        state.setShed(true);
        assertFalse(state.isSuppressed(), "a locked on device must keep running through a shed");
    }

    @Test
    @DisplayName("shedding an unlocked device does suppress it")
    void shedSuppressesAnUnlockedDevice() {
        final FeManagedState state = new FeManagedState();
        state.setShed(true);
        assertTrue(state.isSuppressed());
    }

    @Test
    @DisplayName("releasing a lock clears the stale shed flag, or the unlock looks like it did nothing")
    void releasingALockClearsShed() {
        final FeManagedState state = new FeManagedState();
        state.setLock(FeLock.FORCE_OFF);
        state.setShed(true);
        state.setLock(FeLock.NONE);
        assertFalse(state.isShed());
        assertFalse(state.isSuppressed());
    }

    @Test
    @DisplayName("an edited or older save cannot produce an out of range tier")
    void loadToleratesRubbish() {
        final FeManagedState state = new FeManagedState();
        state.load(999, -4);
        assertEquals(FePriority.DEFAULT, state.priority());
        assertEquals(FeLock.NONE, state.lock());
    }

    @Test
    @DisplayName("a saved lock comes back, because forgetting it would be losing the player's work")
    void loadRoundTripsARealSetting() {
        final FeManagedState saved = new FeManagedState();
        saved.setPriority(FePriority.CRITICAL);
        saved.setLock(FeLock.FORCE_ON);

        final FeManagedState restored = new FeManagedState();
        restored.load(saved.priorityOrdinal(), saved.lockOrdinal());
        assertEquals(FePriority.CRITICAL, restored.priority());
        assertEquals(FeLock.FORCE_ON, restored.lock());
        assertFalse(restored.isShed(), "shed is a live decision and must not survive a reload");
    }
}
