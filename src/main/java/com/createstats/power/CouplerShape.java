package com.createstats.power;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class CouplerShape {
    private static final double[][] NORTH_PX = {
            {0.5, 0, 0, 15.5, 2, 16},
            {1, 2, 0, 15, 12.5, 2.5},
            {1, 2, 13.5, 15, 12.5, 16},
            {2.5, 2, 2.5, 13.5, 14.5, 13.5},
            {13.5, 0, 0, 16, 12.75, 16},
            {0, 2, 2.75, 2.5, 11, 13.25},
            {3.25, 12.5, 0, 13.25, 15.5, 2.5},
            {3.25, 12.5, 13.5, 13.25, 15, 16},
            {6.75, 12.5, 0, 9.25, 16, 2.5},
            {6.75, 12.5, 13.5, 9.25, 16, 16},
    };

    private CouplerShape() {
    }

    public static List<AABB> boxes(Direction facing) {
        final List<AABB> out = new ArrayList<>(NORTH_PX.length);
        for (double[] b : NORTH_PX) {
            out.add(CouplerSockets.turn(new AABB(b[0] / 16, b[1] / 16, b[2] / 16, b[3] / 16, b[4] / 16, b[5] / 16),
                    facing));
        }
        return out;
    }

    public static boolean contains(Direction facing, AABB box) {
        for (AABB part : boxes(facing)) {
            if (part.inflate(1.0e-6).contains(box.minX, box.minY, box.minZ)
                    && part.inflate(1.0e-6).contains(box.maxX, box.maxY, box.maxZ)) {
                return true;
            }
        }
        return false;
    }
}
