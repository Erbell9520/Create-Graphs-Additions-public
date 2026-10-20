package com.createstats.security.relay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayPowerTest {
    @Test
    @DisplayName("Standby alone costs one a tick; the coil adds two while the output is driven")
    void fullPayment() {
        final RelayPower.Tick idle = RelayPower.step(100, false, false);
        assertEquals(1, idle.paid());
        assertTrue(idle.logicRuns());
        assertFalse(idle.coilRuns());
        assertEquals(99, idle.energyAfter());
        final RelayPower.Tick driven = RelayPower.step(100, false, true);
        assertEquals(3, driven.paid());
        assertTrue(driven.coilRuns());
        assertEquals(97, driven.energyAfter());
    }

    @Test
    @DisplayName("Paid in part: enough for the board and not the coil keeps contact and drops the output")
    void partialPayment() {
        final RelayPower.Tick part = RelayPower.step(2, false, true);
        assertEquals(1, part.paid());
        assertTrue(part.logicRuns(), "the lamps and the contact stay up");
        assertFalse(part.coilRuns(), "the coil cannot pull in, so nothing is driven");
        final RelayPower.Tick dark = RelayPower.step(0, false, true);
        assertEquals(0, dark.paid());
        assertFalse(dark.logicRuns());
    }

    @Test
    @DisplayName("Shed or forced off: nothing runs and nothing is taken")
    void suppressed() {
        final RelayPower.Tick shed = RelayPower.step(200, true, true);
        assertEquals(0, shed.paid());
        assertFalse(shed.logicRuns());
        assertEquals(200, shed.energyAfter());
    }

    @Test
    @DisplayName("A board is quoted the peak, so it never admits a relay it would starve at the first alarm")
    void ratedIsThePeak() {
        assertEquals(RelayPower.STANDBY + RelayPower.COIL, RelayPower.rated(false));
        assertEquals(0, RelayPower.rated(true));
        assertTrue(RelayPower.BUFFER / RelayPower.RATED >= 60, "three seconds of ride-through with the coil in");
    }

    @Test
    @DisplayName("Nine conditions, unique ids, and the plan's behaviour table exactly")
    void descriptors() {
        assertEquals(9, RelayConditions.all().size());
        final Set<String> ids = new HashSet<>();
        for (RelayCondition c : RelayConditions.all()) {
            assertTrue(ids.add(c.id()), "duplicate id " + c.id());
            assertTrue(c.id().startsWith("create_stats:"), "namespaced: " + c.id());
            if (c.isPoint()) {
                assertFalse(c.offers(RelayBehavior.FOLLOW), c.id() + " is a point event and must not follow");
                assertTrue(c.offers(RelayBehavior.PULSE) && c.offers(RelayBehavior.HOLD));
            }
        }
        for (RelayCondition c : new RelayCondition[]{RelayConditions.TROUBLE, RelayConditions.HEALTHY}) {
            assertTrue(c.offers(RelayBehavior.FOLLOW));
            assertFalse(c.offers(RelayBehavior.PULSE), "supervision is Follow only in V1");
            assertFalse(c.offers(RelayBehavior.HOLD), "supervision is Follow only in V1");
        }
        for (RelayCondition c : new RelayCondition[]{RelayConditions.LOCKDOWN, RelayConditions.FIRE,
                RelayConditions.WATER, RelayConditions.BREACHED, RelayConditions.MODE}) {
            for (RelayBehavior b : RelayBehavior.all()) {
                assertTrue(c.offers(b), c.id() + " offers " + b);
            }
        }
        assertFalse(RelayConditions.TROUBLE.acceptsDevice("relay"), "a relay cannot watch a relay");
        assertTrue(RelayConditions.TROUBLE.acceptsDevice("sensor:fire"));
        assertFalse(RelayConditions.LOCKDOWN.narrowable());
        assertTrue(RelayConditions.MODE.needsMode());
    }

    @Test
    @DisplayName("Behaviours are saved by key, and an unknown key is unknown rather than the first one")
    void behaviourKeys() {
        for (RelayBehavior b : RelayBehavior.all()) {
            assertEquals(b, RelayBehavior.byKey(b.key()));
        }
        assertEquals(null, RelayBehavior.byKey("strobe"));
    }
}
