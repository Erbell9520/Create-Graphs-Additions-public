package com.createstats.security;

import com.createstats.power.SecurityDevice;
import com.createstats.stormcoil.CoilRules;
import com.createstats.stormcoil.CoilRules.Grid;
import com.createstats.stormcoil.CoilRules.HouseState;
import com.createstats.stormcoil.CoilRules.Rule;
import com.createstats.stormcoil.CoilTargets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilSecurityTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    private static SecurityEndpoint at(long pos) {
        return SecurityEndpoint.of("minecraft:overworld", pos);
    }

    private static SecurityHousehold fresh() {
        return new SecurityHousehold(UUID.fromString("00000000-0000-0000-0000-0000000000ce"));
    }

    @Test
    @DisplayName("A coil nobody has touched is on guard, unlocked, obeys redstone and holds no grid")
    void defaults() {
        final SecurityHousehold h = fresh();
        assertTrue(h.coilOn(at(7L)), "a coil joining a house must not stand down by itself");
        assertFalse(h.coilLocked(at(7L)));
        assertTrue(h.coilHearsRedstone(at(7L)), "a Daylight Sensor must keep working on a new house");
        assertNull(h.coilRules(at(7L)), "no grid: it follows its own Targets setting");
        assertFalse(h.hasCoilRules(at(7L)));
        assertFalse(h.knows(at(7L)), "defaults are nobody's claim");
        assertTrue(h.isEmpty());
    }

    @Test
    @DisplayName("All four survive a save and load, the grid bit for bit")
    void roundTrip() {
        final SecurityHousehold h = fresh();
        final Grid grid = Grid.ofMode(CoilTargets.Mode.HOSTILE)
                .with(HouseState.AWAY, new Rule(true, false, true, 0))
                .with(HouseState.LOCKDOWN, new Rule(true, true, true, 5));
        h.setCoilRules(at(6L), grid);
        h.setCoilOn(at(7L), false);
        h.setCoilLocked(at(8L), true);
        h.setCoilHearsRedstone(at(9L), false);

        final SecurityHousehold back = fresh();
        back.load(h.save());

        assertEquals(grid, back.coilRules(at(6L)), "the grid must come back whole");
        assertFalse(back.coilOn(at(7L)), "stood down must survive a reload");
        assertTrue(back.coilLocked(at(8L)), "a lock that reloads open is not a lock");
        assertFalse(back.coilHearsRedstone(at(9L)), "ignoring redstone must survive a reload");
        assertTrue(back.coilOn(at(8L)));
        assertNull(back.coilRules(at(7L)));
    }

    @Test
    @DisplayName("An all-off grid is a grid, not the absence of one")
    void anEmptyGridIsKept() {
        final SecurityHousehold h = fresh();
        h.setCoilRules(at(5L), Grid.NOTHING);
        assertTrue(h.hasCoilRules(at(5L)));
        assertEquals(Grid.NOTHING, h.coilRules(at(5L)));
        final SecurityHousehold back = fresh();
        back.load(h.save());
        assertEquals(Grid.NOTHING, back.coilRules(at(5L)), "zero must not load as unset");
    }

    @Test
    @DisplayName("Each flag is known, keeps the coil's policy alive, and is pruned with the block")
    void knowsKeepsAndPrunes() {
        final SecurityHousehold h = fresh();
        h.setCoilRules(at(1L), Grid.ofMode(CoilTargets.Mode.TRANSFORM));
        h.setCoilOn(at(2L), false);
        h.setCoilLocked(at(3L), true);
        h.setCoilHearsRedstone(at(4L), false);
        for (long p = 1; p <= 4; p++) {
            assertTrue(h.knows(at(p)), "the house knows coil " + p);
        }
        assertFalse(h.isEmpty(), "a household holding coil policy is not empty");

        h.prune("minecraft:overworld", key -> true);
        assertNull(h.coilRules(at(1L)));
        assertTrue(h.coilOn(at(2L)));
        assertFalse(h.coilLocked(at(3L)));
        assertTrue(h.coilHearsRedstone(at(4L)));
        assertTrue(h.isEmpty());
    }

    @Test
    @DisplayName("Switching back to the default removes the record rather than storing it")
    void defaultsAreNotStored() {
        final SecurityHousehold h = fresh();
        h.setCoilOn(at(4L), false);
        h.setCoilOn(at(4L), true);
        h.setCoilLocked(at(4L), true);
        h.setCoilLocked(at(4L), false);
        h.setCoilHearsRedstone(at(4L), false);
        h.setCoilHearsRedstone(at(4L), true);
        assertFalse(h.knows(at(4L)));
    }

    @Test
    @DisplayName("A coil counts for monitoring only while the house has it on guard; enrolled under its own kind")
    void monitoring() {
        final SecurityHousehold h = fresh();
        assertTrue(SecurityKinds.ALL.contains(SecurityKinds.COIL));
        assertTrue(SecurityKinds.monitors(SecurityKinds.COIL));
        assertTrue(h.requiredForMonitoring(at(3L), SecurityKinds.COIL));
        h.setCoilOn(at(3L), false);
        assertFalse(h.requiredForMonitoring(at(3L), SecurityKinds.COIL), "a stood-down coil is a choice, not a hole");
        assertTrue(h.requiredForMonitoring(at(3L), SecurityKinds.DOOR), "a coil's flag is the coil's only");
        assertTrue(h.enroll(at(3L), SecurityKinds.COIL, 10L));
        assertEquals(SecurityKinds.COIL, h.enrollment(at(3L)).kind());
    }

    @Test
    @DisplayName("The class is appended after the relay, so no saved ordinal moves")
    void appended() {
        final SecurityDevice.DeviceClass[] all = SecurityDevice.DeviceClass.all();
        assertEquals(SecurityDevice.DeviceClass.RELAY.ordinal() + 1, SecurityDevice.DeviceClass.COIL.ordinal());
        assertEquals(SecurityDevice.DeviceClass.COIL, all[all.length - 1]);
        assertEquals("coil", SecurityDevice.DeviceClass.COIL.key());
    }

    @Test
    @DisplayName("Ungoverned, both boxes turn for anybody, whatever else is set")
    void ungovernedPasses() {
        for (CoilGate.Box box : CoilGate.Box.values()) {
            assertEquals(CoilGate.ALLOW, CoilGate.decide(box, false, false, false, false));
            assertEquals(CoilGate.ALLOW, CoilGate.decide(box, false, true, false, false), "a stale lock must not strand it");
        }
    }

    @Test
    @DisplayName("Locked refuses both boxes to everybody, the cleared included")
    void lockedRefuses() {
        for (CoilGate.Box box : CoilGate.Box.values()) {
            assertEquals(CoilGate.LOCKED, CoilGate.decide(box, true, true, true, true));
            assertEquals(CoilGate.LOCKED, CoilGate.decide(box, true, true, false, false));
        }
    }

    @Test
    @DisplayName("Governed, Targets is the panel's for everybody; Height asks the roster and fails closed")
    void governed() {
        for (boolean known : new boolean[] {false, true}) {
            for (boolean passes : new boolean[] {false, true}) {
                assertEquals(CoilGate.PANEL_SETS, CoilGate.decide(CoilGate.Box.TARGETS, true, false, known, passes),
                        "the owner cannot turn Targets either");
            }
        }
        assertEquals(CoilGate.OUT_OF_CONTACT, CoilGate.decide(CoilGate.Box.HEIGHT, true, false, false, true));
        assertEquals(CoilGate.ALLOW, CoilGate.decide(CoilGate.Box.HEIGHT, true, false, true, true));
        assertEquals(CoilGate.NO_CLEARANCE, CoilGate.decide(CoilGate.Box.HEIGHT, true, false, true, false));
    }

    @Test
    @DisplayName("Taking one down trips only for a player who does not clear it")
    void removal() {
        assertTrue(CoilGate.removalTrips(false));
        assertFalse(CoilGate.removalTrips(true));
    }

    @Test
    @DisplayName("Who a player is: owner and admin keyholders, a member at their clearance, a stranger, unknown")
    void personOf() {
        final SecurityRoster r = new SecurityRoster();
        r.setOwner(OWNER, "Ana");
        r.add(ADMIN, "Bolat");
        r.setRole(ADMIN, SecurityRole.ADMIN);
        r.add(MEMBER, "Mira");
        r.setLevel(MEMBER, 3);
        assertEquals(CoilRules.Person.KEYHOLDER, CoilGate.personOf(r, OWNER));
        assertEquals(CoilRules.Person.KEYHOLDER, CoilGate.personOf(r, ADMIN));
        assertEquals(CoilRules.Person.member(3), CoilGate.personOf(r, MEMBER));
        assertEquals(CoilRules.Person.STRANGER, CoilGate.personOf(r, STRANGER),
                "roleOf calls a stranger a MEMBER; the coil must not");
        assertEquals(CoilRules.Person.UNKNOWN, CoilGate.personOf(null, MEMBER), "no roster: nobody is placed");
        assertEquals(CoilRules.Person.UNKNOWN, CoilGate.personOf(r, null));
    }

    private static CoilReading reading(boolean flat, String blockedBy, boolean fedShort, boolean redstone,
                                       boolean engaged) {
        return new CoilReading(2, flat ? 0 : 5, 8, 1, 10, flat, blockedBy, fedShort, redstone, engaged,
                Long.MIN_VALUE, "", CoilTargets.Mode.HOSTILE);
    }

    @Test
    @DisplayName("Choices outrank faults: stood down and held are Standby whatever else is wrong")
    void choicesFirst() {
        final CoilReading broken = reading(true, "Stone", true, true, true);
        assertEquals(CoilStatus.STOOD_DOWN, CoilStatus.of(false, true, broken, true));
        assertEquals(CoilStatus.HELD, CoilStatus.of(true, true, broken, true));
        assertFalse(CoilStatus.STOOD_DOWN.isFault());
        assertFalse(CoilStatus.HELD.isFault());
        assertEquals(SecurityDevice.DeviceState.IDLE, CoilStatus.STOOD_DOWN.deviceState());
        assertEquals(SecurityDevice.DeviceState.IDLE, CoilStatus.HELD.deviceState());
    }

    @Test
    @DisplayName("Redstone holds it only while the house lets it obey")
    void deafIgnoresTheLever() {
        final CoilReading lever = reading(false, "", false, true, false);
        assertEquals(CoilStatus.HELD, CoilStatus.of(true, true, lever, true));
        assertEquals(CoilStatus.ON_GUARD, CoilStatus.of(true, false, lever, true));
    }

    @Test
    @DisplayName("Past the choices: blocked, then fed short, then flat; all three faults")
    void faults() {
        assertEquals(CoilStatus.BLOCKED, CoilStatus.of(true, true, reading(true, "Stone", true, false, false), true));
        assertEquals(CoilStatus.FED_SHORT, CoilStatus.of(true, true, reading(true, "", true, false, false), true));
        assertEquals(CoilStatus.FLAT, CoilStatus.of(true, true, reading(true, "", false, false, false), true));
        for (CoilStatus s : new CoilStatus[] {CoilStatus.BLOCKED, CoilStatus.FED_SHORT, CoilStatus.FLAT}) {
            assertTrue(s.isFault(), s.name());
            assertFalse(s.onGuard());
        }
        assertEquals(SecurityDevice.DeviceState.UNPOWERED, CoilStatus.FLAT.deviceState());
        assertEquals(SecurityDevice.DeviceState.DAMAGED, CoilStatus.BLOCKED.deviceState());
        assertEquals(SecurityDevice.DeviceState.DAMAGED, CoilStatus.FED_SHORT.deviceState());
    }

    @Test
    @DisplayName("A fault still shows when this state arcs at nothing; otherwise nothing to arc at is Standby")
    void nothingToArcAt() {
        assertEquals(CoilStatus.FLAT, CoilStatus.of(true, true, reading(true, "", false, false, false), false),
                "flat at home is flat when the house leaves: say it now");
        assertEquals(CoilStatus.NO_TARGETS, CoilStatus.of(true, true, reading(false, "", false, false, true), false));
        assertTrue(CoilStatus.NO_TARGETS.standby());
        assertEquals(SecurityDevice.DeviceState.IDLE, CoilStatus.NO_TARGETS.deviceState());
    }

    @Test
    @DisplayName("On guard, and fighting while engaged; both ACTIVE, which the Overview counts")
    void onGuard() {
        assertEquals(CoilStatus.ON_GUARD, CoilStatus.of(true, true, reading(false, "", false, false, false), true));
        assertEquals(CoilStatus.FIGHTING, CoilStatus.of(true, true, reading(false, "", false, false, true), true));
        assertEquals(SecurityDevice.DeviceState.ACTIVE, CoilStatus.ON_GUARD.deviceState());
        assertEquals(SecurityDevice.DeviceState.ACTIVE, CoilStatus.FIGHTING.deviceState());
        assertEquals(CoilStatus.UNLOADED, CoilStatus.of(true, true, null, true));
        assertEquals(CoilStatus.UNLOADED, CoilStatus.byOrdinal(-1));
    }

    @Test
    @DisplayName("The lamp: none ungoverned, green only on guard, amber otherwise, red flashing in a lockdown")
    void lamp() {
        for (CoilStatus s : CoilStatus.values()) {
            assertEquals(CoilStatus.Lamp.NONE, CoilStatus.lamp(false, true, s), "an ungoverned coil has no lamp");
            assertEquals(CoilStatus.Lamp.RED_FLASHING, CoilStatus.lamp(true, true, s), "a lockdown outranks " + s);
            assertEquals(s.onGuard() ? CoilStatus.Lamp.GREEN : CoilStatus.Lamp.AMBER, CoilStatus.lamp(true, false, s),
                    s.name());
        }
        int lit = 0;
        for (int t = 0; t < 20; t++) {
            if (CoilStatus.flashOn(t)) {
                lit++;
            }
        }
        assertEquals(12, lit, "six ticks of every ten: two flashes a second");
        assertTrue(CoilStatus.flashOn(1000.4) == CoilStatus.flashOn(1000.0), "a frame's partial tick does not flicker it");
    }

    @Test
    @DisplayName("Never arced reads never, not a moment ago")
    void sinceArc() {
        final CoilReading never = reading(false, "", false, false, false);
        assertEquals(-1L, never.sinceArc(1000L), "Long.MIN_VALUE must not wrap into 'just now'");
        final CoilReading once = new CoilReading(1, 1, 8, 0, 10, false, null, false, false, false, 400L, null, null);
        assertEquals(600L, once.sinceArc(1000L));
        assertEquals(0L, once.sinceArc(100L));
        assertEquals("", once.blockedBy());
        assertEquals(CoilTargets.Mode.HOSTILE, once.targets());
    }
}
