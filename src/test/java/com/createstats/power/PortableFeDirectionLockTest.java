package com.createstats.power;

import com.createstats.power.PortableFeTransfer.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableFeDirectionLockTest {
    @Test
    @DisplayName("an opposite-mode interface is refused while the holder is moving")
    void oppositeIsRefused() {
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        assertTrue(lock.stamp(7L, Mode.CHARGE, 100));
        assertFalse(lock.mayMove(7L, Mode.DRAW, 100));
        assertFalse(lock.stamp(7L, Mode.DRAW, 100), "stamp must not steal a live claim");
    }

    @Test
    @DisplayName("tick order does not matter: the drawer ticking first next tick is still refused")
    void orderIndependent() {
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        lock.stamp(7L, Mode.CHARGE, 100);
        assertFalse(lock.mayMove(7L, Mode.DRAW, 101));
    }

    @Test
    @DisplayName("the loop cannot run: across many ticks in shuffled order, only one direction ever moves")
    void loopNeverRuns() {
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        int charged = 0;
        int drawn = 0;
        for (long now = 0; now < 200; now++) {
            final boolean drawerFirst = now % 3 == 0;
            for (int turn = 0; turn < 2; turn++) {
                final Mode mode = (turn == 0) == drawerFirst ? Mode.DRAW : Mode.CHARGE;
                if (lock.mayMove(7L, mode, now) && lock.stamp(7L, mode, now)) {
                    if (mode == Mode.CHARGE) {
                        charged++;
                    } else {
                        drawn++;
                    }
                }
            }
        }
        assertTrue(charged == 0 || drawn == 0, "charged " + charged + ", drawn " + drawn);
        assertEquals(200, charged + drawn);
    }

    @Test
    @DisplayName("same-mode interfaces share, so two chargers are a faster charge")
    void sameModeShares() {
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        assertTrue(lock.stamp(7L, Mode.CHARGE, 100));
        assertTrue(lock.stamp(7L, Mode.CHARGE, 100));
    }

    @Test
    @DisplayName("a holder that stops moving releases its claim two ticks later")
    void claimLapses() {
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        lock.stamp(7L, Mode.CHARGE, 100);
        assertFalse(lock.mayMove(7L, Mode.DRAW, 101));
        assertTrue(lock.mayMove(7L, Mode.DRAW, 102));
        assertTrue(lock.stamp(7L, Mode.DRAW, 102));
    }

    @Test
    @DisplayName("contraptions are independent")
    void keysIndependent() {
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        lock.stamp(7L, Mode.CHARGE, 100);
        assertTrue(lock.mayMove(8L, Mode.DRAW, 100));
    }

    @Test
    @DisplayName("stale claims are swept, so the table does not grow with every train ever docked")
    void staleClaimsSwept() {
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        for (long key = 0; key < 50; key++) {
            lock.stamp(key, Mode.CHARGE, 10);
        }
        lock.stamp(999L, Mode.CHARGE, 20);
        assertEquals(1, lock.size());
    }
}
