package com.createstats.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepellerSecurityTest {
    private static SecurityEndpoint at(long pos) {
        return SecurityEndpoint.of("minecraft:overworld", pos);
    }

    private static SecurityHousehold fresh() {
        return new SecurityHousehold(UUID.fromString("00000000-0000-0000-0000-0000000000cd"));
    }

    @Test
    @DisplayName("An ungoverned repeller is anybody's to tune, whatever else is set")
    void ungovernedPasses() {
        assertEquals(RepellerGate.ALLOW, RepellerGate.decide(false, false, false, false));

        assertEquals(RepellerGate.ALLOW, RepellerGate.decide(false, true, false, false));
    }

    @Test
    @DisplayName("Locked refuses everybody, including a player who clears it")
    void lockedRefusesEvenTheCleared() {
        assertEquals(RepellerGate.LOCKED, RepellerGate.decide(true, true, true, true));
        assertEquals(RepellerGate.LOCKED, RepellerGate.decide(true, true, false, false));
    }

    @Test
    @DisplayName("Governed with no roster to check fails closed, not open")
    void outOfContactFailsClosed() {
        assertEquals(RepellerGate.OUT_OF_CONTACT, RepellerGate.decide(true, false, false, true));
    }

    @Test
    @DisplayName("Governed and unlocked, the clearance decides")
    void clearanceDecides() {
        assertEquals(RepellerGate.ALLOW, RepellerGate.decide(true, false, true, true));
        assertEquals(RepellerGate.NO_CLEARANCE, RepellerGate.decide(true, false, true, false));
    }

    @Test
    @DisplayName("Taking one down trips only for a player who does not clear it")
    void removal() {
        assertTrue(RepellerGate.removalTrips(false));
        assertFalse(RepellerGate.removalTrips(true));
    }

    @Test
    @DisplayName("A repeller nobody has touched is on, unlocked and obeys redstone")
    void defaults() {
        final SecurityHousehold h = fresh();
        assertTrue(h.repellerOn(at(7L)), "a repeller joining a house must not go quiet");
        assertFalse(h.repellerLocked(at(7L)));
        assertTrue(h.repellerHearsRedstone(at(7L)), "a Time Switch must keep working on a new house");
        assertFalse(h.knows(at(7L)), "defaults are nobody's claim");
        assertTrue(h.isEmpty());
    }

    @Test
    @DisplayName("All three flags survive a save and load")
    void roundTrip() {
        final SecurityHousehold h = fresh();
        h.setRepellerOn(at(7L), false);
        h.setRepellerLocked(at(8L), true);
        h.setRepellerHearsRedstone(at(9L), false);

        final SecurityHousehold back = fresh();
        back.load(h.save());

        assertFalse(back.repellerOn(at(7L)), "switched off must survive a reload");
        assertTrue(back.repellerLocked(at(8L)), "a lock that reloads open is not a lock");
        assertFalse(back.repellerHearsRedstone(at(9L)), "ignoring redstone must survive a reload");
        assertTrue(back.repellerOn(at(8L)));
        assertFalse(back.repellerLocked(at(7L)));
    }

    @Test
    @DisplayName("Each flag is something this house knows, keeps it alive, and is pruned with the block")
    void knowsKeepsAndPrunes() {
        final SecurityHousehold h = fresh();
        h.setRepellerOn(at(1L), false);
        h.setRepellerLocked(at(2L), true);
        h.setRepellerHearsRedstone(at(3L), false);
        assertTrue(h.knows(at(1L)) && h.knows(at(2L)) && h.knows(at(3L)));
        assertFalse(h.isEmpty(), "a household holding repeller policy is not empty");

        h.prune("minecraft:overworld", key -> true);
        assertTrue(h.repellerOn(at(1L)));
        assertFalse(h.repellerLocked(at(2L)));
        assertTrue(h.repellerHearsRedstone(at(3L)));
        assertTrue(h.isEmpty());
    }

    @Test
    @DisplayName("Switching back to the default removes the record rather than storing it")
    void defaultsAreNotStored() {
        final SecurityHousehold h = fresh();
        h.setRepellerOn(at(4L), false);
        h.setRepellerOn(at(4L), true);
        h.setRepellerLocked(at(4L), true);
        h.setRepellerLocked(at(4L), false);
        h.setRepellerHearsRedstone(at(4L), false);
        h.setRepellerHearsRedstone(at(4L), true);
        assertFalse(h.knows(at(4L)));
    }
}
