package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerCouplerModeTest {
    @Test
    @DisplayName("a port is never open in both directions, so neither grid reads the Coupler as storage")
    void everyPortIsOneWay() {
        for (PowerCouplerMode mode : PowerCouplerMode.values()) {
            assertFalse(mode.seamReceives() && mode.seamGives(), mode + " seam claims both ways");
            assertFalse(mode.gridReceives() && mode.gridGives(), mode + " grid claims both ways");
            assertTrue(mode.seamReceives() || mode.seamGives(), mode + " seam is dead");
            assertTrue(mode.gridReceives() || mode.gridGives(), mode + " grid is dead");
        }
    }

    @Test
    @DisplayName("the grid port always faces the opposite way to the New Age port")
    void theTwoPortsAlwaysOppose() {
        for (PowerCouplerMode mode : PowerCouplerMode.values()) {
            assertEquals(mode.seamReceives(), mode.gridGives(), mode.toString());
            assertEquals(mode.seamGives(), mode.gridReceives(), mode.toString());
        }
    }

    @Test
    @DisplayName("PULL imports: their network fills the buffer and our grid draws on it")
    void pullImports() {
        assertTrue(PowerCouplerMode.PULL.seamReceives());
        assertTrue(PowerCouplerMode.PULL.gridGives());
    }

    @Test
    @DisplayName("PUSH exports: our grid fills the buffer and their network drains it")
    void pushExports() {
        assertTrue(PowerCouplerMode.PUSH.gridReceives());
        assertTrue(PowerCouplerMode.PUSH.seamGives());
    }

    @Test
    @DisplayName("the wrench cycles, and the names are the blockstate values")
    void wrenchAndNames() {
        assertEquals(PowerCouplerMode.PUSH, PowerCouplerMode.PULL.next());
        assertEquals(PowerCouplerMode.PULL, PowerCouplerMode.PUSH.next());
        assertEquals("pull", PowerCouplerMode.PULL.getSerializedName());
        assertEquals("push", PowerCouplerMode.PUSH.getSerializedName());
    }

    @Test
    @DisplayName("an ordinal out of range reads as PULL rather than throwing")
    void ordinalIsForgiving() {
        assertEquals(PowerCouplerMode.PULL, PowerCouplerMode.byOrdinal(-1));
        assertEquals(PowerCouplerMode.PULL, PowerCouplerMode.byOrdinal(99));
        assertEquals(PowerCouplerMode.PUSH, PowerCouplerMode.byOrdinal(1));
    }
}
