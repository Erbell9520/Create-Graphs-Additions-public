package com.createstats.vccmk4;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrustLeaseTest {
    private static final UUID A = new UUID(1, 1);
    private static final UUID B = new UUID(2, 2);

    @Test
    void aLeaseIsLiveForTheTickItWasWrittenAndTheNext() {
        Vcc4ThrustLeases.Table t = new Vcc4ThrustLeases.Table();
        assertNotNull(t.renew(7L, A, 100, () -> null));
        assertTrue(t.takeExpired(100).isEmpty(), "live the tick it was written");
        assertTrue(t.takeExpired(101).isEmpty(), "and the next, so a console that writes every tick never drops it");
        Map<Long, Vcc4ThrustLeases.Lease> lapsed = t.takeExpired(102);
        assertEquals(1, lapsed.size(), "gone the tick after that with no renewal");
        assertEquals(A, lapsed.get(7L).owner());
        assertEquals(0, t.size(), "taken out of the table once handed back");
        assertNotNull(t.renew(7L, A, 100, () -> null));
        assertNotNull(t.renew(7L, A, 101, () -> null));
        assertNotNull(t.renew(7L, A, 102, () -> null));
        assertTrue(t.takeExpired(103).isEmpty(), "renewed every tick, it never lapses");
        assertEquals(1, t.takeExpired(104).size());
    }

    @Test
    void aLiveLeaseKeepsASecondConsoleOff() {
        Vcc4ThrustLeases.Table t = new Vcc4ThrustLeases.Table();
        assertNotNull(t.renew(7L, A, 100, () -> null));
        assertNull(t.renew(7L, B, 101, () -> null), "B cannot take it while A's lease is live");
        assertTrue(t.heldByAnother(7L, B, 101));
        assertFalse(t.heldByAnother(7L, A, 101), "not foreign to its own holder");
        assertNotNull(t.renew(7L, B, 102, () -> null), "once A's lapsed, B takes it");
        assertTrue(t.heldByAnother(7L, A, 102));
        assertNull(t.renew(7L, A, 103, () -> null));
    }

    @Test
    void theNozzleTargetIsRememberedFromTheFirstTakeOverAndRestoredOnlyIfMoved() {
        Vcc4ThrustLeases.Table t = new Vcc4ThrustLeases.Table();
        Vcc4ThrustLeases.Lease first = t.renew(7L, A, 100, () -> new float[]{0.4f, -0.2f});
        assertNotNull(first);
        assertArrayEquals(new float[]{0.4f, -0.2f}, first.nozzle(), 1e-6f, "where the player had it");
        assertFalse(first.nozzleMoved());
        Vcc4ThrustLeases.Lease renewed = t.renew(7L, A, 101, () -> new float[]{0.9f, 0.9f});
        assertNotNull(renewed);
        assertArrayEquals(new float[]{0.4f, -0.2f}, renewed.nozzle(), 1e-6f, "a renewal keeps the original, not what the console wrote since");
        t.nozzleMoved(7L, A);

        assertNull(t.renew(7L, B, 102, () -> new float[]{0f, 0f}), "still A's on the tick after its last renewal");
        Vcc4ThrustLeases.Lease taken = t.renew(7L, B, 103, () -> new float[]{0f, 0f});
        assertNotNull(taken);
        assertArrayEquals(new float[]{0.4f, -0.2f}, taken.nozzle(), 1e-6f, "handed on with the lease");
        assertTrue(taken.nozzleMoved(), "and so is the fact that it was moved");
        assertTrue(t.takeExpired(200).get(7L).nozzleMoved(), "moved: the hand-back restores it");
        Vcc4ThrustLeases.Lease fixed = t.renew(8L, A, 100, () -> null);
        assertNotNull(fixed);
        assertNull(fixed.nozzle(), "a fixed nozzle has nothing to restore");
        t.nozzleMoved(8L, B);
        assertFalse(t.takeOwned(A).get(8L).nozzleMoved(), "only the holder's own writes count");
    }

    @Test
    void aConsoleThatStopsDrivingTakesAllItsLeasesBackAtOnce() {
        Vcc4ThrustLeases.Table t = new Vcc4ThrustLeases.Table();
        t.renew(1L, A, 100, () -> null);
        t.renew(2L, A, 100, () -> null);
        t.renew(3L, B, 100, () -> null);
        Map<Long, Vcc4ThrustLeases.Lease> mine = t.takeOwned(A);
        assertEquals(2, mine.size());
        assertEquals(1, t.size(), "B's stays");
        assertNotNull(t.renew(1L, B, 100, () -> null), "and the freed jet can be taken at once");
    }

    @Test
    void aLeaseFromAnOlderClockIsStaleNotLive() {
        Vcc4ThrustLeases.Table t = new Vcc4ThrustLeases.Table();
        assertNotNull(t.renew(7L, A, 1_000_000, () -> null));
        assertFalse(t.heldByAnother(7L, B, 5), "a lease far ahead of the clock cannot be a live one");
        assertNotNull(t.renew(7L, B, 5, () -> null));
        assertNotNull(t.renew(9L, A, 1_000_000, () -> null));
        assertEquals(1, t.takeExpired(5).size(), "and the sweeper drops it");
    }
}
