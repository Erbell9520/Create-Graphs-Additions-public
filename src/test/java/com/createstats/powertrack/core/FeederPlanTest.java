package com.createstats.powertrack.core;

import com.createstats.powertrack.core.FeederPlan.Input;
import com.createstats.powertrack.core.FeederPlan.Mode;
import com.createstats.powertrack.core.FeederPlan.Plan;
import com.createstats.powertrack.core.FeederPlan.Reason;
import com.createstats.powertrack.core.SectionExchange.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeederPlanTest {
    private static final int RATE = 4_096;
    private static final long CAP = 1_000_000L;

    private static Plan plan(Mode mode, int buffer, long stored, int floor, boolean holding, boolean auto) {
        return FeederPlan.plan(new Input(mode, true, false, RATE, buffer, stored, CAP, floor, holding, auto));
    }

    @Test
    @DisplayName("supplying, the circuit tops the buffer up to the dial and the rail may take what is in it")
    void supplyTopsUp() {
        final Plan p = plan(Mode.SUPPLY, 1_000, 900_000, 50, false, false);
        assertEquals(Role.SUPPLY, p.role());
        assertEquals(RATE - 1_000, p.gridIntake());
        assertEquals(1_000, p.railAvailable());
        assertFalse(p.offersToGrid());
        assertEquals(Reason.READY, p.reason());
    }

    @Test
    @DisplayName("a held floor stops the circuit, but what is already in the buffer still goes on the rail")
    void floorHoldsTheStationNotTheBuffer() {
        final Plan p = plan(Mode.SUPPLY, 1_000, 400_000, 50, false, false);
        assertEquals(Reason.FLOOR_HELD, p.reason());
        assertEquals(0, p.gridIntake());
        assertEquals(1_000, p.railAvailable());
        assertTrue(p.floorHolding());

        assertTrue(plan(Mode.SUPPLY, 0, 520_000, 50, true, false).floorHolding());
        assertFalse(plan(Mode.SUPPLY, 0, 560_000, 50, true, false).floorHolding());
    }

    @Test
    @DisplayName("switched off, a supplying feeder neither fills nor energises the rail")
    void switchedOffSupply() {
        final Plan p = FeederPlan.plan(new Input(Mode.SUPPLY, true, true, RATE, 2_000, CAP, CAP, 0, false, false));
        assertEquals(Reason.SWITCHED_OFF, p.reason());
        assertEquals(0, p.gridIntake());
        assertEquals(0, p.railAvailable());
    }

    @Test
    @DisplayName("collecting, the rail fills the room left under the dial and the circuit is offered the buffer")
    void collectOffers() {
        final Plan p = plan(Mode.COLLECT, 1_000, 0, 30, false, false);
        assertEquals(Role.COLLECT, p.role());
        assertEquals(RATE - 1_000, p.railRoom());
        assertTrue(p.offersToGrid());
        assertEquals(30, p.trainFloorPct());
        final Plan empty = plan(Mode.COLLECT, 0, 0, 30, false, false);
        assertTrue(empty.offersToGrid(), "a source keeps claiming extract when it is empty (FeSupply's rule)");
        final Plan off = FeederPlan.plan(new Input(Mode.COLLECT, true, true, RATE, 500, 0, CAP, 0, false, false));
        assertEquals(0, off.railRoom());
        assertFalse(off.offersToGrid(), "a switched-off feeder holds what it has");
    }

    @Test
    @DisplayName("off the rail, a feeder takes nothing from its circuit and nothing from the rail")
    void noRail() {
        final Plan s = FeederPlan.plan(new Input(Mode.SUPPLY, false, false, RATE, 0, CAP, CAP, 0, false, false));
        assertEquals(Reason.NO_RAIL, s.reason());
        assertEquals(0, s.gridIntake());
        final Plan c = FeederPlan.plan(new Input(Mode.COLLECT, false, false, RATE, 100, 0, CAP, 0, false, false));
        assertEquals(0, c.railRoom());
        assertTrue(c.offersToGrid(), "FE already collected is still the circuit's");
    }

    @Test
    @DisplayName("Auto supplies above the floor and collects at it, with five points between the two")
    void autoFollowsTheStation() {
        assertEquals(Role.SUPPLY, plan(Mode.AUTO, 0, 800_000, 50, false, true).role());
        final Plan atFloor = plan(Mode.AUTO, 0, 500_000, 50, false, true);
        assertEquals(Role.COLLECT, atFloor.role());
        assertFalse(atFloor.autoSupplying());
        assertEquals(50, atFloor.trainFloorPct(), "and it leaves trains the same line it keeps");
        assertEquals(Role.COLLECT, plan(Mode.AUTO, 0, 530_000, 50, false, false).role());
        assertEquals(Role.SUPPLY, plan(Mode.AUTO, 0, 550_000, 50, false, false).role());
    }

    @Test
    @DisplayName("Auto at a station with no banks supplies from generation: there is nothing to judge or protect")
    void autoWithoutBanks() {
        final Plan p = FeederPlan.plan(new Input(Mode.AUTO, true, false, RATE, 0, 0, 0, 50, false, false));
        assertEquals(Role.SUPPLY, p.role());
        assertEquals(RATE, p.gridIntake());
    }

    @Test
    @DisplayName("under a manager: a held board stops supply, and Auto turns to the way its terminal works")
    void managed() {
        final Plan held = FeederPlan.plan(new Input(Mode.SUPPLY, true, false, RATE, 0, CAP, CAP, 0, false, false,
                true, false, false));
        assertEquals(Reason.STATION_HELD, held.reason());
        assertEquals(0, held.gridIntake());
        final Plan autoHeld = FeederPlan.plan(new Input(Mode.AUTO, true, false, RATE, 0, CAP, CAP, 0, false, true,
                true, false, false));
        assertEquals(Role.COLLECT, autoHeld.role(), "a short board does not charge trains, it takes from them");
        final Plan input = FeederPlan.plan(new Input(Mode.SUPPLY, true, false, RATE, 0, CAP, CAP, 0, false, false,
                false, true, false));
        assertEquals(Reason.WRONG_TERMINAL, input.reason());
        assertEquals(0, input.gridIntake());
        final Plan output = FeederPlan.plan(new Input(Mode.AUTO, true, false, RATE, 0, 0, CAP, 50, false, false,
                false, false, true));
        assertEquals(Role.SUPPLY, output.role(), "a controller output is never read from, so Auto supplies there");
        final Plan collectOnOutput = FeederPlan.plan(new Input(Mode.COLLECT, true, false, RATE, 0, 0, CAP, 0, false,
                false, false, false, true));
        assertEquals(Reason.WRONG_TERMINAL, collectOnOutput.reason());
        assertEquals(0, collectOnOutput.railRoom());
    }

    @Test
    @DisplayName("the buffer is the dial, floored above zero so the feeder is never read as a network mouth")
    void bufferIsTheDial() {
        assertEquals(8_192, FeederPlan.bufferCapacity(8_192));
        assertTrue(FeederPlan.bufferCapacity(0) > 0);
    }

    @Test
    @DisplayName("the dial's rows round-trip and a saved row out of range lands on a real mode")
    void rows() {
        for (Mode m : Mode.values()) {
            assertEquals(m, Mode.byRow(m.row()));
        }
        assertEquals(Mode.SUPPLY, Mode.byRow(3));
    }
}
