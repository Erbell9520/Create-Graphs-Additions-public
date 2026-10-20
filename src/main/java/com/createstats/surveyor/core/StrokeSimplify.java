package com.createstats.surveyor.core;

import java.util.ArrayList;
import java.util.List;

// RDP at 0.4 blocks
public final class StrokeSimplify {
    public static final double EPSILON = 0.4;
    public static final double MIN_SPACING = 1.0;

    private StrokeSimplify() {
    }

    public static List<double[]> simplify(List<double[]> points, double epsilon, double minSpacing) {
        List<double[]> pts = new ArrayList<>(points.size());
        double min2 = minSpacing * minSpacing;
        for (double[] p : points) {
            if (p == null || p.length < 3 || !Double.isFinite(p[0]) || !Double.isFinite(p[2])) continue;
            if (!pts.isEmpty()) {
                double[] last = pts.get(pts.size() - 1);
                double d2 = sq(p[0] - last[0]) + sq(p[2] - last[2]);
                if (d2 < min2) continue;
            }
            pts.add(p);
        }

        if (!points.isEmpty() && pts.size() > 1) {
            double[] end = points.get(points.size() - 1);
            double[] last = pts.get(pts.size() - 1);
            if (end != last && end != null && end.length >= 3 && sq(end[0] - last[0]) + sq(end[2] - last[2]) > 0.04) {
                if (sq(end[0] - last[0]) + sq(end[2] - last[2]) < min2) pts.set(pts.size() - 1, end);
                else pts.add(end);
            }
        }
        if (pts.size() <= 2) return pts;
        boolean[] keep = new boolean[pts.size()];
        keep[0] = true;
        keep[pts.size() - 1] = true;
        rdp(pts, 0, pts.size() - 1, epsilon, keep);
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) if (keep[i]) out.add(pts.get(i));
        return out;
    }

    private static void rdp(List<double[]> pts, int first, int last, double epsilon, boolean[] keep) {
        java.util.ArrayDeque<int[]> work = new java.util.ArrayDeque<>();
        work.push(new int[]{first, last});
        while (!work.isEmpty()) {
            int[] span = work.pop();
            int a = span[0];
            int b = span[1];
            if (b - a < 2) continue;
            double[] pa = pts.get(a);
            double[] pb = pts.get(b);
            double best = -1.0;
            int index = -1;
            for (int i = a + 1; i < b; i++) {
                double d = distanceToSegment(pts.get(i), pa, pb);
                if (d > best) {
                    best = d;
                    index = i;
                }
            }
            if (best > epsilon && index > 0) {
                keep[index] = true;
                work.push(new int[]{a, index});
                work.push(new int[]{index, b});
            }
        }
    }

    static double distanceToSegment(double[] p, double[] a, double[] b) {
        double ex = b[0] - a[0];
        double ez = b[2] - a[2];
        double len2 = ex * ex + ez * ez;
        double t = len2 < 1.0e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, ((p[0] - a[0]) * ex + (p[2] - a[2]) * ez) / len2));
        return Math.sqrt(sq(a[0] + ex * t - p[0]) + sq(a[2] + ez * t - p[2]));
    }

    private static double sq(double v) {
        return v * v;
    }
}
