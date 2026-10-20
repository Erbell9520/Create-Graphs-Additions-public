package com.createstats.seismic;

import java.util.ArrayList;
import java.util.List;

public final class SurveyBowl {
    static final int RINGS = 4;

    static final int MERIDIANS = 12;

    private SurveyBowl() {
    }

    public static List<double[]> dashes(double radius, int segments) {
        final List<double[]> out = new ArrayList<>();
        if (radius <= 0.0 || segments < 8) {
            return out;
        }
        final int steps = segments - segments % 2;

        for (int k = 0; k < RINGS; k++) {
            final double phi = Math.PI / 2.0 * k / RINGS;
            final double rr = radius * Math.cos(phi);
            final double y = -radius * Math.sin(phi);
            final double offset = k % 2 == 0 ? 0.0 : Math.PI / steps;
            for (int s = 0; s < steps; s += 2) {
                final double a0 = 2.0 * Math.PI * s / steps + offset;
                final double a1 = 2.0 * Math.PI * (s + 1) / steps + offset;
                out.add(new double[] {rr * Math.cos(a0), y, rr * Math.sin(a0), rr * Math.cos(a1), y, rr * Math.sin(a1)});
            }
        }

        final int down = Math.max(4, steps / 4);
        for (int m = 0; m < MERIDIANS; m++) {
            final double theta = 2.0 * Math.PI * m / MERIDIANS;
            final double cx = Math.cos(theta);
            final double cz = Math.sin(theta);
            for (int s = 0; s < down; s += 2) {
                final double p0 = Math.PI / 2.0 * s / down;
                final double p1 = Math.PI / 2.0 * Math.min(down, s + 1) / down;
                out.add(new double[] {
                        radius * Math.cos(p0) * cx, -radius * Math.sin(p0), radius * Math.cos(p0) * cz,
                        radius * Math.cos(p1) * cx, -radius * Math.sin(p1), radius * Math.cos(p1) * cz});
            }
        }
        return out;
    }
}
