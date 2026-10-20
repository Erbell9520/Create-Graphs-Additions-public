package com.createstats.statsmk3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

final class Mk3RenderBounds {
    static final double ANTENNA_CLEARANCE = 15.0 / 16.0;

    private Mk3RenderBounds() {
    }

    static AABB assembly(BlockPos origin, int width, int height, Direction facing) {
        final int resolvedWidth = Math.max(1, width);
        final int resolvedHeight = Math.max(1, height);
        final Direction right = facing.getClockWise();
        final BlockPos corner = origin.relative(right, resolvedWidth - 1).above(resolvedHeight - 1);
        final AABB blocks = new AABB(origin).minmax(new AABB(corner));
        return new AABB(blocks.minX, blocks.minY, blocks.minZ,
                blocks.maxX, blocks.maxY + ANTENNA_CLEARANCE, blocks.maxZ);
    }
}
