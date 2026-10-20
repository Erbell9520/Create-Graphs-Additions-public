package com.createstats.power;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteryControllerPortsTest {
    @Test
    void aClickOnATerminalCentreResolvesToThatTerminal() {
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST,
                Direction.SOUTH, Direction.WEST}) {
            for (int port = 0; port < BatteryControllerPorts.COUNT; port++) {
                final double[] world = BatteryControllerPorts.toBlockSpace(facing,
                        BatteryControllerPorts.localX(port), BatteryControllerPorts.localZ(port));
                assertEquals(port, BatteryControllerPorts.pick(facing, world[0], world[1]),
                        "facing " + facing + ", port " + port);
            }
        }
    }

    @Test
    void theTwoTransformsAreInversesOfEachOther() {
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST,
                Direction.SOUTH, Direction.WEST}) {
            for (double x = 0.05; x < 1.0; x += 0.1) {
                for (double z = 0.05; z < 1.0; z += 0.1) {
                    final double[] model = BatteryControllerPorts.toModelSpace(facing, x, z);
                    final double[] back = BatteryControllerPorts.toBlockSpace(facing, model[0], model[1]);
                    assertEquals(x, back[0], 1.0e-9, "facing " + facing);
                    assertEquals(z, back[1], 1.0e-9, "facing " + facing);
                }
            }
        }
    }

    @Test
    void aRotatedControllerIsNotSilentlyMirrored() {
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST,
                Direction.SOUTH, Direction.WEST}) {
            final double[] inputA = BatteryControllerPorts.toBlockSpace(facing,
                    BatteryControllerPorts.localX(0), BatteryControllerPorts.localZ(0));
            final int hit = BatteryControllerPorts.pick(facing, inputA[0], inputA[1]);
            assertTrue(BatteryControllerPorts.isInput(hit),
                    "the input column must stay the input column on facing " + facing);
            assertNotEquals(BatteryControllerPorts.outputPort(0), hit);
        }
    }

    @Test
    void theGapBetweenTerminalsResolvesToNothing() {
        final double midZ = (BatteryControllerPorts.localZ(0) + BatteryControllerPorts.localZ(1)) * 0.5;
        assertEquals(-1, BatteryControllerPorts.pick(Direction.NORTH,
                BatteryControllerPorts.localX(0), midZ));

        assertEquals(-1, BatteryControllerPorts.pick(Direction.NORTH, 0.5, 0.5));
    }

    @Test
    void inputsAndOutputsAreNamedApartFromEachOther() {
        assertEquals("A", BatteryControllerPorts.label(0));
        assertEquals("C", BatteryControllerPorts.label(2));
        assertEquals("1", BatteryControllerPorts.label(BatteryControllerPorts.outputPort(0)));
        assertEquals("3", BatteryControllerPorts.label(BatteryControllerPorts.outputPort(2)));
        assertEquals("Input A", BatteryControllerPorts.describe(0));
        assertEquals("Output 2", BatteryControllerPorts.describe(BatteryControllerPorts.outputPort(1)));
    }

    @Test
    void roleQueriesAgreeWithEachOther() {
        for (int port = 0; port < BatteryControllerPorts.COUNT; port++) {
            assertTrue(BatteryControllerPorts.isPortIndex(port));
            assertNotEquals(BatteryControllerPorts.isInput(port), BatteryControllerPorts.isOutput(port),
                    "a terminal is exactly one of the two");
        }
        assertFalse(BatteryControllerPorts.isPortIndex(-1));
        assertFalse(BatteryControllerPorts.isPortIndex(BatteryControllerPorts.COUNT));
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            assertEquals(i, BatteryControllerPorts.outputIndex(BatteryControllerPorts.outputPort(i)));
        }
    }

    @Test
    void everyTerminalBoxSitsInsideTheBlock() {
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST,
                Direction.SOUTH, Direction.WEST}) {
            for (int port = 0; port < BatteryControllerPorts.COUNT; port++) {
                final double[] box = BatteryControllerPorts.boxOf(facing, port);
                assertTrue(box[0] >= 0.0 && box[2] <= 1.0, "x within the block, port " + port);
                assertTrue(box[1] >= 0.0 && box[3] <= 1.0, "z within the block, port " + port);
                assertTrue(box[2] > box[0] && box[3] > box[1], "a box with area");
            }
        }
    }
}
