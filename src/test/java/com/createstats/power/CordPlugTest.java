package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CordPlugTest {
    private static final BlockPos SOCKET = new BlockPos(-197, 69, -23);

    private static final double[] SINGLE = {8.0 / 16.0};

    private static final double[] DUPLEX = {4.0 / 16.0, 12.0 / 16.0};

    @Test
    @DisplayName("the plug stands out of the room side of the plate, on every facing")
    void thePlugIsInTheRoom() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            final Vec3 mouth = CordPlug.socketMouth(facing, SOCKET, SINGLE, null);

            final Vec3 local = mouth.subtract(Vec3.atLowerCornerOf(SOCKET));
            final double outward = along(local, facing.getOpposite());
            assertEquals(2.0 / 16.0 + CordPlug.PLUG_LENGTH, outward, 1.0e-9,
                    "the plug is at the wrong depth on facing " + facing);
            assertTrue(inside(local), "the plug left its own block on facing " + facing + ": " + local);
        }
    }

    @Test
    @DisplayName("a plug is centred on the plate, and a duplex's two are not")
    void thePlugIsOnItsGang() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            final Vec3 single = CordPlug.socketMouth(facing, SOCKET, SINGLE, null);

            assertEquals(0.5, across(single.subtract(Vec3.atLowerCornerOf(SOCKET)), facing), 1.0e-9,
                    "the single outlet's plug is off centre on facing " + facing);

            final Vec3 left = CordPlug.boreMouth(facing, SOCKET, DUPLEX[0]);
            final Vec3 right = CordPlug.boreMouth(facing, SOCKET, DUPLEX[1]);
            assertEquals(0.5, left.distanceTo(right), 1.0e-9,
                    "the duplex's two gangs are not half a block apart on facing " + facing);
        }
    }

    @Test
    @DisplayName("a duplex uses whichever of its two sockets the machine is nearer")
    void theNearerGangWins() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            final Vec3 left = CordPlug.boreMouth(facing, SOCKET, DUPLEX[0]);
            final Vec3 right = CordPlug.boreMouth(facing, SOCKET, DUPLEX[1]);

            assertTrue(CordPlug.socketMouth(facing, SOCKET, DUPLEX, left).distanceTo(left)
                            < CordPlug.socketMouth(facing, SOCKET, DUPLEX, right).distanceTo(left),
                    "the duplex reached across itself on facing " + facing);
        }
    }

    private static double along(Vec3 local, Direction direction) {
        return switch (direction) {
            case NORTH -> 1.0 - local.z;
            case SOUTH -> local.z;
            case WEST -> 1.0 - local.x;
            case EAST -> local.x;
            case DOWN -> 1.0 - local.y;
            case UP -> local.y;
        };
    }

    private static double across(Vec3 local, Direction facing) {
        return facing.getAxis() == Direction.Axis.Z ? local.x : local.z;
    }

    private static boolean inside(Vec3 local) {
        return local.x >= 0.0 && local.x <= 1.0 && local.y >= 0.0 && local.y <= 1.0
                && local.z >= 0.0 && local.z <= 1.0;
    }
}
