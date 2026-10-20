package com.createstats.surveyor.core;

import java.util.ArrayList;
import java.util.List;

public final class RecordTrack {
    public static final double SPACING = 1.0;
    public static final int STILL_TICKS = 60;
    public static final double STILL_SPEED = 0.3;
    public static final double CLOSE_REACH = 6.0;
    public static final double CLOSE_DEGREES = 45.0;

    public static final int MAX_POINTS = (int) PathSpline.MAX_LENGTH;

    private final List<double[]> points = new ArrayList<>();

    private final List<double[]> stops = new ArrayList<>();
    private double startHx;
    private double startHz;
    private double lastX = Double.NaN;
    private double lastZ;
    private double peak;
    private int stillTicks;
    private boolean stopMarked;
    private boolean full;

    public void sample(double x, double y, double z, double hx, double hz, double speed) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) return;
        double v = Math.abs(speed);
        peak = Math.max(peak, v);
        if (points.isEmpty()) {
            points.add(new double[]{x, y, z, v});
            startHx = hx;
            startHz = hz;
            lastX = x;
            lastZ = z;
            return;
        }
        if (v < STILL_SPEED) {
            if (++stillTicks >= STILL_TICKS && !stopMarked) {
                stops.add(new double[]{x, y, z, points.size() - 1});
                stopMarked = true;
            }
        } else {
            stillTicks = 0;
            stopMarked = false;
        }
        double d2 = (x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ);
        if (d2 >= SPACING * SPACING) {
            if (points.size() >= MAX_POINTS) {
                full = true;
                return;
            }
            points.add(new double[]{x, y, z, peak});
            peak = v;
            lastX = x;
            lastZ = z;
        }
    }

    public int size() {
        return points.size();
    }

    public List<double[]> points() {
        return points;
    }

    public int stopCount() {
        return stops.size();
    }

    public boolean full() {
        return full;
    }

    public boolean nearStart(double x, double z, double hx, double hz) {
        if (points.size() < 12) return false;
        double[] a = points.get(0);
        double d2 = (x - a[0]) * (x - a[0]) + (z - a[2]) * (z - a[2]);
        if (d2 > CLOSE_REACH * CLOSE_REACH) return false;
        double dot = hx * startHx + hz * startHz;
        return dot >= Math.cos(Math.toRadians(CLOSE_DEGREES));
    }

    public double cruise() {
        double top = 0.0;
        for (double[] p : points) top = Math.max(top, p[3]);
        return Math.max(Pathway.MIN_CRUISE, Math.min(PathNode.MAX_SPEED, Math.ceil(top * 2.0) / 2.0));
    }

    public List<PathNode> finish(boolean loop) {
        List<double[]> tagged = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            double[] p = points.get(i);
            tagged.add(new double[]{p[0], p[1], p[2], p[3], i});
        }
        List<double[]> kept = StrokeSimplify.simplify(tagged, StrokeSimplify.EPSILON, SPACING);
        if (loop && kept.size() > 3) {
            double[] a = kept.get(0);
            double[] b = kept.get(kept.size() - 1);
            if ((a[0] - b[0]) * (a[0] - b[0]) + (a[2] - b[2]) * (a[2] - b[2]) < 9.0) kept.remove(kept.size() - 1);
        }

        List<double[]> withStops = new ArrayList<>(kept);
        List<Boolean> isStop = new ArrayList<>();
        for (int i = 0; i < withStops.size(); i++) isStop.add(false);
        for (double[] st : stops) {
            int near = -1;
            double best = 1.5 * 1.5;
            for (int i = 0; i < withStops.size(); i++) {
                double[] k = withStops.get(i);
                double d2 = (k[0] - st[0]) * (k[0] - st[0]) + (k[2] - st[2]) * (k[2] - st[2]);
                if (d2 <= best) {
                    best = d2;
                    near = i;
                }
            }
            if (near >= 0) {
                isStop.set(near, true);
                continue;
            }
            int at = withStops.size();
            for (int i = 0; i < withStops.size(); i++) {
                if (withStops.get(i)[4] > st[3]) {
                    at = i;
                    break;
                }
            }
            withStops.add(at, new double[]{st[0], st[1], st[2], 0.0, st[3]});
            isStop.add(at, true);
        }
        List<PathNode> out = new ArrayList<>(withStops.size());
        for (int i = 0; i < withStops.size(); i++) {
            double[] k = withStops.get(i);
            int from = (int) k[4];
            int to = i + 1 < withStops.size() ? (int) withStops.get(i + 1)[4] : points.size() - 1;
            double top = 0.0;
            for (int j = Math.max(0, from); j <= Math.min(points.size() - 1, Math.max(from, to)); j++) {
                top = Math.max(top, points.get(j)[3]);
            }
            double limit = top < 0.5 ? 0.0 : Math.max(1.5, Math.ceil(top * 2.0) / 2.0);
            out.add(new PathNode(i, k[0], k[1], k[2], "", Math.min(PathNode.MAX_SPEED, limit), Corner.SMOOTH,
                    isStop.get(i) ? Stop.DEFAULT : null));
        }
        return out;
    }
}
