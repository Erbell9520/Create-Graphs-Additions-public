package com.createstats.security;

import com.createstats.power.FePriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensorPolicyTest {
    @Test
    void modeDecidesWhatASensorsOwnArmedFlagIsWorth() {
        assertFalse(SecurityMode.HOME.armsIntrusion(true), "HOME must force logging only");
        assertFalse(SecurityMode.HOME.armsIntrusion(false));

        assertTrue(SecurityMode.AWAY.armsIntrusion(false), "AWAY must arm everything");
        assertTrue(SecurityMode.AWAY.armsIntrusion(true));

        assertTrue(SecurityMode.CUSTOM.armsIntrusion(true), "CUSTOM is the only reader of the flag");
        assertFalse(SecurityMode.CUSTOM.armsIntrusion(false));

        assertTrue(SecurityMode.CUSTOM.usesPerSensorFlag());
        assertFalse(SecurityMode.HOME.usesPerSensorFlag());
        assertFalse(SecurityMode.AWAY.usesPerSensorFlag());
    }

    @Test
    void hazardSensorsIgnoreTheModeAndNeverLockDown() {
        assertTrue(SensorKind.FIRE.isHazard());
        assertTrue(SensorKind.WATER.isHazard());
        assertFalse(SensorKind.MOTION.isHazard());
        assertFalse(SensorKind.TAMPER.isHazard());

        for (SensorKind kind : SensorKind.all()) {
            assertEquals(!kind.isHazard(), kind.family().armable(),
                    kind + ": only an intrusion sensor is armable");
            assertEquals(!kind.isHazard(), kind.family().locksDown(),
                    kind + ": only an intrusion sensor raises a lockdown");
        }
    }

    @Test
    void everySensorOutranksEveryMachineAndTheTwoCriticalOnesAreFireAndMotion() {
        assertEquals(FePriority.CRITICAL, expectedTier(SensorKind.MOTION));
        assertEquals(FePriority.CRITICAL, expectedTier(SensorKind.FIRE));
        assertEquals(FePriority.HIGH, expectedTier(SensorKind.TAMPER));
        assertEquals(FePriority.HIGH, expectedTier(SensorKind.WATER));

        for (SensorKind kind : SensorKind.all()) {
            assertTrue(expectedTier(kind).ordinal() <= FePriority.HIGH.ordinal(),
                    kind + " must never be shed before a machine is");
        }
    }

    private static FePriority expectedTier(SensorKind kind) {
        return switch (kind) {
            case MOTION, FIRE -> FePriority.CRITICAL;
            case TAMPER, WATER -> FePriority.HIGH;
        };
    }

    @Test
    void rangeIsClampedToSomethingAHouseSizedBuildingCanUse() {
        assertEquals(SensorKind.minRadius(), SensorKind.clampRadius(0));
        assertEquals(SensorKind.minRadius(), SensorKind.clampRadius(Integer.MIN_VALUE));
        assertEquals(SensorKind.maxRadius(), SensorKind.clampRadius(Integer.MAX_VALUE));
        assertEquals(6, SensorKind.clampRadius(6));

        for (SensorKind kind : SensorKind.all()) {
            final int fresh = kind.defaultRadius();
            assertEquals(fresh, SensorKind.clampRadius(fresh),
                    kind + ": a freshly placed sensor must not start outside its own bounds");
        }
    }
}
