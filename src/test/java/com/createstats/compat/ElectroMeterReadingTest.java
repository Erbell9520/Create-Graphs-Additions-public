package com.createstats.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElectroMeterReadingTest {
    private static final double EPS = 1e-9;

    @Test
    void anAmmeterReadsOhmsLawOverItsShuntNotItsStoredVoltage() {
        assertEquals(12.3, ElectroMeterReading.amps(0.123, 1.0), EPS);
        assertEquals(12.3, ElectroMeterReading.reading(false, 0.123, 1.0), EPS);
    }

    @Test
    void aVoltmeterReadsItsStoredVoltage() {
        assertEquals(230.0, ElectroMeterReading.volts(230.0, 1.0), EPS);
        assertEquals(230.0, ElectroMeterReading.reading(true, 230.0, 1.0), EPS);
    }

    @Test
    void bothReadUnsignedLikeTheMeter() {
        assertEquals(230.0, ElectroMeterReading.volts(-230.0, 1.0), EPS);
        assertEquals(12.3, ElectroMeterReading.amps(-0.123, 1.0), EPS);
    }

    @Test
    void theScalingKnobMultipliesTheReading() {
        assertEquals(11_000.0, ElectroMeterReading.volts(11.0, 1000.0), 1e-6);
        assertEquals(0.23, ElectroMeterReading.volts(230.0, 0.001), EPS);
        assertEquals(123.0, ElectroMeterReading.amps(0.123, 10.0), 1e-9);
    }

    @Test
    void anIdleMeterReadsZero() {
        assertEquals(0.0, ElectroMeterReading.volts(0.0, 1.0), EPS);
        assertEquals(0.0, ElectroMeterReading.amps(0.0, 1.0), EPS);
    }
}
