package com.createstats.ground;

import java.util.Collection;

import com.createstats.aerial.V3;
import com.createstats.ground.GroundSnapshot.Shape;

public final class GroundSilhouette {
    private GroundSilhouette() {
    }

    public static Shape of(int unit, Collection<V3> centres) {
        if (centres == null || centres.isEmpty()) {
            return new Shape(unit, 1.0, 0, 0, 0, 0.0, 0.0, 0.0, new byte[0], new byte[0]);
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (final V3 c : centres) {
            minX = Math.min(minX, c.x() - 0.5);
            maxX = Math.max(maxX, c.x() + 0.5);
            minY = Math.min(minY, c.y() - 0.5);
            maxY = Math.max(maxY, c.y() + 0.5);
            minZ = Math.min(minZ, c.z() - 0.5);
            maxZ = Math.max(maxZ, c.z() + 0.5);
        }
        final double extent = Math.max(maxZ - minZ, Math.max(maxX - minX, maxY - minY));
        final double cell = Math.max(1.0, Math.ceil(extent / Shape.MAX_CELLS));
        final int cols = Math.min(Shape.MAX_CELLS, (int) Math.ceil((maxZ - minZ) / cell - 1.0e-9));
        final int topRows = Math.min(Shape.MAX_CELLS, (int) Math.ceil((maxX - minX) / cell - 1.0e-9));
        final int sideRows = Math.min(Shape.MAX_CELLS, (int) Math.ceil((maxY - minY) / cell - 1.0e-9));
        final byte[] top = new byte[(cols * topRows + 7) >> 3];
        final byte[] side = new byte[(cols * sideRows + 7) >> 3];
        for (final V3 c : centres) {
            final int col = index(c.z(), minZ, cell, cols);
            set(top, index(c.x(), minX, cell, topRows) * cols + col);
            set(side, index(c.y(), minY, cell, sideRows) * cols + col);
        }
        return new Shape(unit, cell, cols, topRows, sideRows, minZ, minX, minY, top, side);
    }

    private static int index(double v, double min, double cell, int count) {
        return Math.max(0, Math.min(count - 1, (int) Math.floor((v - min) / cell)));
    }

    private static void set(byte[] bits, int i) {
        bits[i >> 3] |= (byte) (1 << (i & 7));
    }
}
