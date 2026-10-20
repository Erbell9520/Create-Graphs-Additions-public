package com.createstats.power;

import com.createstats.blockentity.BatteryControllerBE;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerBusTest {
    @Test
    @DisplayName("KI-184: the bus holds a tick of every input at its rating, 393,216 at the defaults")
    void theBusHoldsEveryInput() {
        assertTrue((long) BatteryControllerBE.busCapacity()
                >= (long) BatteryControllerPorts.INPUTS * BatteryControllerBE.inputRate(),
                "a bus smaller than its inputs caps them all at its own size");
        assertEquals(3 * 131_072, BatteryControllerBE.busCapacity(), "the configured 64,000 is lifted to three inputs");
    }

    @Test
    @DisplayName("KI-184: an input carries two HV Lines (the owner's 2x HV), an output one; the banks charge through the inputs")
    void inputsAreTwoHvLines() {
        assertEquals(2, BatteryControllerBE.INPUT_LINES);
        assertEquals(131_072, BatteryControllerBE.inputRate());
        assertEquals(65_536, BatteryControllerBE.portRate(), "an output still carries one HV Line");
        assertEquals(3 * 131_072, BatteryControllerBE.reserveChargeRate());
        assertEquals(3 * 65_536, BatteryControllerBE.reserveDischargeRate());
    }
}
