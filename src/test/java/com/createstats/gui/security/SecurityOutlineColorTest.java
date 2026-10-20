package com.createstats.gui.security;

import com.createstats.network.SecuritySyncPacket;
import com.createstats.power.SecurityDevice;
import com.createstats.security.SensorKind;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SecurityOutlineColorTest {
    private static SecuritySyncPacket.Row door(SecurityDevice.DeviceState state) {
        return new SecuritySyncPacket.Row(BlockPos.ZERO, "Iron Door",
                SecurityDevice.DeviceClass.DOOR.ordinal(), state.ordinal(), 1.0f, 1, "N 4m",
                false, 1, -1, false, 0, false, false);
    }

    private static SecuritySyncPacket.Row sensor(SensorKind kind,
                                                 SecurityDevice.DeviceState state, boolean armed) {
        return new SecuritySyncPacket.Row(BlockPos.ZERO, "Sensor",
                SecurityDevice.DeviceClass.SENSOR.ordinal(), state.ordinal(), -1.0f, 1, "N 4m",
                false, 1, kind.ordinal(), armed, 6, false, false);
    }

    @Test
    void unpoweredIsGreyOnADoorAndRedOnASensor() {
        assertEquals(SecurityTheme.OFF,
                SecurityTheme.outlineColor(door(SecurityDevice.DeviceState.UNPOWERED)));
        assertEquals(SecurityTheme.RED,
                SecurityTheme.outlineColor(
                        sensor(SensorKind.MOTION, SecurityDevice.DeviceState.UNPOWERED, true)));
    }

    @Test
    void activeIsGreenOnADoorAndRedOnASensor() {
        assertEquals(SecurityTheme.GREEN,
                SecurityTheme.outlineColor(door(SecurityDevice.DeviceState.ACTIVE)));
        assertEquals(SecurityTheme.RED,
                SecurityTheme.outlineColor(
                        sensor(SensorKind.MOTION, SecurityDevice.DeviceState.ACTIVE, true)));
    }

    @Test
    void aRestingHazardIsAmberWhateverItsArmedFlagSays() {
        for (boolean armed : new boolean[]{true, false}) {
            assertEquals(SecurityTheme.AMBER,
                    SecurityTheme.outlineColor(
                            sensor(SensorKind.FIRE, SecurityDevice.DeviceState.IDLE, armed)),
                    "a fire sensor at rest, armed=" + armed);
            assertEquals(SecurityTheme.AMBER,
                    SecurityTheme.outlineColor(
                            sensor(SensorKind.WATER, SecurityDevice.DeviceState.IDLE, armed)),
                    "a water sensor at rest, armed=" + armed);
        }
    }

    @Test
    void aRestingIntrusionSensorReadsItsArming() {
        assertEquals(SecurityTheme.GREEN,
                SecurityTheme.outlineColor(
                        sensor(SensorKind.MOTION, SecurityDevice.DeviceState.IDLE, true)));
        assertEquals(SecurityTheme.OFF,
                SecurityTheme.outlineColor(
                        sensor(SensorKind.TAMPER, SecurityDevice.DeviceState.IDLE, false)));
    }

    @Test
    void breachedIsRedOnEveryClass() {
        assertEquals(SecurityTheme.RED,
                SecurityTheme.outlineColor(door(SecurityDevice.DeviceState.BREACHED)));
        assertEquals(SecurityTheme.RED,
                SecurityTheme.outlineColor(
                        sensor(SensorKind.TAMPER, SecurityDevice.DeviceState.BREACHED, false)));
        assertNotEquals(SecurityTheme.outlineColor(door(SecurityDevice.DeviceState.BREACHED)),
                SecurityTheme.outlineColor(door(SecurityDevice.DeviceState.IDLE)));
    }
}
