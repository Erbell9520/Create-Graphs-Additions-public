package com.createstats.repeller;

import java.util.ArrayList;
import java.util.List;

public final class VolumeOutline {
    private VolumeOutline() {
    }

    public static List<double[]> sphere(double r, int latitudes, int longitudes, int segments) {
        final List<double[]> out = new ArrayList<>();
        for (int k = 1; k <= latitudes; k++) {
            final double phi = -Math.PI / 2 + Math.PI * k / (latitudes + 1);
            out.add(ring(r * Math.cos(phi), r * Math.sin(phi), segments));
        }
        final int half = Math.max(2, segments / 2);
        for (int j = 0; j < longitudes; j++) {
            final double theta = 2 * Math.PI * j / longitudes;
            final double[] line = new double[(half + 1) * 3];
            for (int s = 0; s <= half; s++) {
                final double phi = -Math.PI / 2 + Math.PI * s / half;
                line[s * 3] = r * Math.cos(phi) * Math.cos(theta);
                line[s * 3 + 1] = r * Math.sin(phi);
                line[s * 3 + 2] = r * Math.cos(phi) * Math.sin(theta);
            }
            out.add(line);
        }
        return out;
    }

    public static List<double[]> column(double r, double yLow, double yHigh, int rings, int verticals, int segments) {
        final List<double[]> out = new ArrayList<>();
        final int n = Math.max(2, rings);
        for (int k = 0; k < n; k++) {
            out.add(ring(r, yLow + (yHigh - yLow) * k / (n - 1), segments));
        }
        for (int j = 0; j < verticals; j++) {
            final double theta = 2 * Math.PI * j / verticals;
            out.add(new double[] {r * Math.cos(theta), yLow, r * Math.sin(theta),
                    r * Math.cos(theta), yHigh, r * Math.sin(theta)});
        }
        return out;
    }

    public static double[] ring(double r, double y, int segments) {
        final int n = Math.max(3, segments);
        final double[] line = new double[(n + 1) * 3];
        for (int s = 0; s <= n; s++) {
            final double theta = 2 * Math.PI * (s % n) / n;
            line[s * 3] = r * Math.cos(theta);
            line[s * 3 + 1] = y;
            line[s * 3 + 2] = r * Math.sin(theta);
        }
        return line;
    }
}
