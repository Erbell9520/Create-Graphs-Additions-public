package com.createstats.statsmk3;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mk3RenderBoundsTest {
    private static final double EPSILON = 1.0e-9;
    private static final EnumSet<Direction> FACINGS = EnumSet.of(
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);

    @Test
    void boundsContainEveryAssemblyBlockForEveryFacing() {
        final BlockPos origin = new BlockPos(12, 30, -8);
        for (Direction facing : FACINGS) {
            final AABB bounds = Mk3RenderBounds.assembly(origin, 5, 3, facing);
            final Direction right = facing.getClockWise();
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 3; y++) {
                    final AABB block = new AABB(origin.relative(right, x).above(y));
                    assertContains(bounds, block, facing + " block " + x + "," + y);
                }
            }
        }
    }

    @Test
    void boundsReachPastTallestAntennaWithoutBroadInflation() {
        final BlockPos origin = new BlockPos(12, 30, -8);
        for (Direction facing : FACINGS) {
            final AABB bounds = Mk3RenderBounds.assembly(origin, 5, 3, facing);

            assertEquals(origin.getY() + 3 + Mk3RenderBounds.ANTENNA_CLEARANCE,
                    bounds.maxY, EPSILON, facing.toString());
            assertEquals(3 + Mk3RenderBounds.ANTENNA_CLEARANCE,
                    bounds.getYsize(), EPSILON, facing.toString());
            assertTrue(Math.max(bounds.getXsize(), bounds.getZsize()) <= 5.0 + EPSILON,
                    facing + ": horizontal bounds were inflated beyond the assembly");
            assertTrue(Math.min(bounds.getXsize(), bounds.getZsize()) <= 1.0 + EPSILON,
                    facing + ": horizontal bounds were inflated away from the panel face");
        }
    }

    private static void assertContains(AABB outer, AABB inner, String message) {
        assertTrue(outer.minX <= inner.minX + EPSILON && outer.minY <= inner.minY + EPSILON
                        && outer.minZ <= inner.minZ + EPSILON && outer.maxX + EPSILON >= inner.maxX
                        && outer.maxY + EPSILON >= inner.maxY && outer.maxZ + EPSILON >= inner.maxZ,
                message);
    }
}
