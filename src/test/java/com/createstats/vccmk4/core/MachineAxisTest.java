package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineAxisTest {
    private static final MachineAxis.Setting HELD = MachineAxis.Setting.SPRUNG;
    private static final MachineAxis.Setting SWITCH = MachineAxis.Setting.LATCHED;

    @Test
    void aHeldMachineFollowsItsKeysAndStopsOnRelease() {
        MachineAxis m = new MachineAxis();
        assertEquals(1.0, m.step(HELD, 1.0, true), 1.0e-9, "the fork goes up while the key is down");
        assertEquals(-1.0, m.step(HELD, -1.0, true), 1.0e-9, "and down on the other key");
        assertEquals(0.0, m.step(HELD, 0.0, true), 1.0e-9, "and stops when let go");
        assertEquals(0.5, m.step(HELD, 0.5, true), 1.0e-9, "a throttle-driven machine follows the throttle");
    }

    @Test
    void twoKeysStepASwitchThroughBackStopForward() {
        MachineAxis m = new MachineAxis();
        assertEquals(1.0, m.step(SWITCH, 1.0, true), 1.0e-9, "press forward: runs");
        assertEquals(1.0, m.step(SWITCH, 1.0, true), 1.0e-9, "holding is not another press");
        assertEquals(1.0, m.step(SWITCH, 0.0, true), 1.0e-9, "let go: still runs");
        assertEquals(0.0, m.step(SWITCH, -1.0, true), 1.0e-9, "the other key steps it down to stop");
        m.step(SWITCH, 0.0, true);
        assertEquals(-1.0, m.step(SWITCH, -1.0, true), 1.0e-9, "and again to backwards");
        m.step(SWITCH, 0.0, true);
        assertEquals(-1.0, m.step(SWITCH, -1.0, true), 1.0e-9, "and no further");
    }

    @Test
    void oneKeyTogglesOnAndOff() {
        MachineAxis m = new MachineAxis();
        assertEquals(1.0, m.step(SWITCH, 1.0, false), 1.0e-9);
        m.step(SWITCH, 0.0, false);
        assertEquals(0.0, m.step(SWITCH, 1.0, false), 1.0e-9, "the same key stops it");
        m.step(SWITCH, 0.0, false);
        assertEquals(1.0, m.step(SWITCH, 1.0, false), 1.0e-9);
    }

    @Test
    void invertSwapsTheWayItRunsAndResetStopsIt() {
        MachineAxis m = new MachineAxis();
        assertEquals(-1.0, m.step(HELD.withInverted(true), 1.0, true), 1.0e-9);
        m.step(SWITCH, 1.0, true);
        m.reset();
        assertEquals(0, m.state(), "the driver left: the drill stops");
        assertEquals(0.0, m.step(SWITCH, 0.0, true), 1.0e-9);
    }

    @Test
    void speedIsClampedIntoTheGovernorsRange() {
        assertEquals(MachineAxis.MAX_RPM, HELD.withRpm(9999).rpm());
        assertEquals(MachineAxis.MIN_RPM, HELD.withRpm(-5).rpm());
        assertEquals(96, HELD.withRpm(96).rpm());
    }

    @Test
    void drillsAndBoreheadsProposeASwitchPistonsAndWinchesDoNot() {
        assertTrue(MachineAxis.runnerByDefault(List.of("block.create.mechanical_drill")));
        assertTrue(MachineAxis.runnerByDefault(List.of("block.offroad.borehead_bearing")));
        assertTrue(MachineAxis.runnerByDefault(List.of("block.create.encased_fan")));
        assertFalse(MachineAxis.runnerByDefault(List.of("block.create.mechanical_piston")));
        assertFalse(MachineAxis.runnerByDefault(List.of("block.create.rope_pulley", "block.simulated.rope_winch")));
        assertFalse(MachineAxis.runnerByDefault(List.of("block.createbigcannons.cannon_mount")));
        assertFalse(MachineAxis.runnerByDefault(List.of()));
        assertEquals(SWITCH, MachineAxis.defaultSetting(true));
        assertEquals(HELD, MachineAxis.defaultSetting(false));
    }
}
