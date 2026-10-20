package com.createstats.vacuum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VacuumFrameTest {
    private static Vec3 normal(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    private static void near(Vec3 expected, Vec3 actual, String what) {
        assertTrue(expected.distanceTo(actual) < 1.0e-9, what + ": expected " + expected + " got " + actual);
    }

    @Test
    void theSniffersHeadPointsAlongItsFacing() {
        for (Direction facing : Direction.values()) {
            near(normal(facing), VacuumFrame.mountedDir(facing, 0, 1, 0), "up in the model, facing=" + facing);
        }
    }

    @Test
    void theInverseUndoesTheTurn() {
        final Vec3 v = new Vec3(0.3, -0.7, 0.55);
        for (Direction facing : Direction.values()) {
            final Vec3 w = VacuumFrame.mountedDir(facing, v.x, v.y, v.z);
            near(v, VacuumFrame.toMounted(facing, w), "facing=" + facing);
        }
    }

    @Test
    void theHorizontalMouthFacesItsFacing() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            near(normal(facing), VacuumFrame.horizontalDir(facing, 0, 0, -1), "north in the model, facing=" + facing);

            final Vec3 mouth = VacuumFrame.horizontal(BlockPos.ZERO, facing, 5.0, 9.75, -2.0);
            final Vec3 out = mouth.subtract(0.5, 0.5, 0.5);
            assertTrue(out.dot(normal(facing)) > 0.5, "mouth in front, facing=" + facing + " at " + mouth);
        }
    }

    @Test
    void aClickTurnsBackIntoTheModel() {
        final double[] p = {5.0, 9.75, 0.0};
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            final Vec3 world = VacuumFrame.horizontal(BlockPos.ZERO, facing, p[0], p[1], p[2]);
            near(new Vec3(p[0], p[1], p[2]), VacuumFrame.toHorizontal(facing, world).scale(16.0), "facing=" + facing);
        }
    }

    @Test
    void theRendererTurnMatchesTheFrame() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            final double a = Math.toRadians(VacuumFrame.yRotation(facing));
            final Vec3 turned = new Vec3(0 * Math.cos(a) + (-1) * Math.sin(a), 0, -(0) * Math.sin(a) + (-1) * Math.cos(a));
            near(VacuumFrame.horizontalDir(facing, 0, 0, -1), turned, "yRotation facing=" + facing);
        }
    }

    @Test
    void theSniffersHitBoxSitsOnTheFaceItIsBoltedTo() {
        final Map<Direction, VoxelShape> shapes = com.createstats.block.VacuumShapes.mounted(
                new double[] {1, 0, 1.5, 15, 1, 15.5});
        for (Direction facing : Direction.values()) {
            final AABB b = shapes.get(facing).bounds();
            final Direction back = facing.getOpposite();
            final double against = switch (back) {
                case DOWN -> b.minY;
                case UP -> 1.0 - b.maxY;
                case NORTH -> b.minZ;
                case SOUTH -> 1.0 - b.maxZ;
                case WEST -> b.minX;
                case EAST -> 1.0 - b.maxX;
            };
            assertEquals(0.0, against, 1.0e-9, "mount plate on the " + back + " face, facing=" + facing);
        }
    }
}
