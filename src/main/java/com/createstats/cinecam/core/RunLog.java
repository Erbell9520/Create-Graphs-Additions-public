package com.createstats.cinecam.core;

// speed over a window, focus jitter / half a second = a speed that never happened
public final class RunLog {
    public static final int CAPACITY = 1200;
    public static final double SPEED_WINDOW = 1.0;

    private final double[] t = new double[CAPACITY];
    private final double[] x = new double[CAPACITY];
    private final double[] y = new double[CAPACITY];
    private final double[] z = new double[CAPACITY];
    private int head;
    private int count;
    private double start = Double.NaN;
    private double distance;
    private double topSpeed;
    private double topHeight = Double.NEGATIVE_INFINITY;
    private double bestClimb;
    private int windowFrom;

    public record Summary(double seconds, double distance, double topSpeed, double topHeight, double bestClimb,
                          int samples) {
    }

    public void add(double time, double px, double py, double pz) {
        if (Double.isNaN(start)) start = time;
        if (count > 0) {
            int last = index(count - 1);
            if (time <= t[last]) return;
            distance += Math.sqrt(sq(px - x[last]) + sq(py - y[last]) + sq(pz - z[last]));
        }
        int i = head;
        t[i] = time;
        x[i] = px;
        y[i] = py;
        z[i] = pz;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) count++;
        else windowFrom = Math.max(0, windowFrom - 1);
        if (py > topHeight) topHeight = py;

        while (windowFrom < count - 2 && time - t[index(windowFrom + 1)] >= SPEED_WINDOW) windowFrom++;
        int w = index(windowFrom);
        double dt = time - t[w];
        if (count >= 2 && dt >= SPEED_WINDOW) {
            double speed = Math.sqrt(sq(px - x[w]) + sq(py - y[w]) + sq(pz - z[w])) / dt;
            if (speed > topSpeed) topSpeed = speed;
            double climb = (py - y[w]) / dt;
            if (climb > bestClimb) bestClimb = climb;
        }
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public Summary summary() {
        if (count == 0) return new Summary(0, 0, 0, 0, 0, 0);
        double end = t[index(count - 1)];
        return new Summary(end - start, distance, topSpeed, topHeight, bestClimb, count);
    }

    private int index(int k) {
        int oldest = count < CAPACITY ? 0 : head;
        return (oldest + k) % CAPACITY;
    }

    private static double sq(double v) {
        return v * v;
    }
}
