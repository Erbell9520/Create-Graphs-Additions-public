package com.createstats.aerial;

import java.util.Collection;

import com.createstats.aerial.AerialSnapshot.Silhouette;

public final class AerialSilhouette {
    private AerialSilhouette() {
    }

    public static Silhouette of(Collection<V3> blockCentres, V3 nose) {
        if (blockCentres == null || blockCentres.isEmpty()) {
            return Silhouette.NONE;
        }
        final V3 f = nose == null || nose.length() < 1.0e-6 ? new V3(0.0, 0.0, -1.0) : nose.normalized();
        final V3 u = V3.UP;
        final V3 r = f.cross(u).normalized();
        double minF = Double.POSITIVE_INFINITY;
        double maxF = Double.NEGATIVE_INFINITY;
        double minU = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double minR = Double.POSITIVE_INFINITY;
        double maxR = Double.NEGATIVE_INFINITY;
        for (final V3 b : blockCentres) {
            final double bf = b.dot(f);
            final double bu = b.dot(u);
            final double br = b.dot(r);
            minF = Math.min(minF, bf - 0.5);
            maxF = Math.max(maxF, bf + 0.5);
            minU = Math.min(minU, bu - 0.5);
            maxU = Math.max(maxU, bu + 0.5);
            minR = Math.min(minR, br - 0.5);
            maxR = Math.max(maxR, br + 0.5);
        }
        final double extent = Math.max(maxF - minF, Math.max(maxU - minU, maxR - minR));
        final double cell = Math.max(1.0, Math.ceil(extent / Silhouette.MAX_CELLS));
        final int cols = Math.min(Silhouette.MAX_CELLS, (int) Math.ceil((maxF - minF) / cell));
        final int sideRows = Math.min(Silhouette.MAX_CELLS, (int) Math.ceil((maxU - minU) / cell));
        final int topRows = Math.min(Silhouette.MAX_CELLS, (int) Math.ceil((maxR - minR) / cell));
        final byte[] side = new byte[(cols * sideRows + 7) >> 3];
        final byte[] top = new byte[(cols * topRows + 7) >> 3];
        for (final V3 b : blockCentres) {
            final int c = index(b.dot(f), minF, cell, cols);
            set(side, index(b.dot(u), minU, cell, sideRows) * cols + c);
            set(top, index(b.dot(r), minR, cell, topRows) * cols + c);
        }
        return new Silhouette(cell, cols, sideRows, topRows, minF, minU, minR, side, top);
    }

    private static int index(double v, double min, double cell, int count) {
        return Math.max(0, Math.min(count - 1, (int) Math.floor((v - min) / cell)));
    }

    private static void set(byte[] bits, int i) {
        bits[i >> 3] |= (byte) (1 << (i & 7));
    }
}
