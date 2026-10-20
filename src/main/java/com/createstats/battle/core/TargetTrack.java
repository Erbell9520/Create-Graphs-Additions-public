package com.createstats.battle.core;

// velocity from our own positions. sable's velocity on a sub-level swings way too much to lead with. sable tax #1
public final class TargetTrack {
    public static final int WINDOW = 4;

    public static final double SMOOTH = 0.65;

    public static final double MAX_STEP = 8.0;

    public static final int MAX_GAP = 10;

    public static final int LOST_CLEAR = 1200;

    private static final int CAPACITY = WINDOW + 1;

    private final long[] ticks = new long[CAPACITY];
    private final double[] xs = new double[CAPACITY];
    private final double[] ys = new double[CAPACITY];
    private final double[] zs = new double[CAPACITY];

    private int tail;
    private int count;

    private boolean fix;
    private long lastSeen = Long.MIN_VALUE;
    private double px;
    private double py;
    private double pz;

    private boolean moving;
    private boolean reseed;
    private double vx;
    private double vy;
    private double vz;

    private boolean clock;
    private long since;

    public void observe(long tick, double x, double y, double z) {
        startClock(tick);
        if (fix) {
            if (tick < lastSeen) return;
            if (tick == lastSeen) {
                int newest = (tail + count - 1) % CAPACITY;
                xs[newest] = x;
                ys[newest] = y;
                zs[newest] = z;
                px = x;
                py = y;
                pz = z;
                return;
            }
            long dt = tick - lastSeen;
            double sx = x - px;
            double sy = y - py;
            double sz = z - pz;
            double limit = MAX_STEP * dt;
            if (dt > MAX_GAP || sx * sx + sy * sy + sz * sz > limit * limit) {
                count = 0;
                reseed = moving;
            } else {
                while (count > 1 && ticks[tail] < tick - WINDOW) {
                    tail = (tail + 1) % CAPACITY;
                    count--;
                }
            }
        }
        int slot = (tail + count) % CAPACITY;
        ticks[slot] = tick;
        xs[slot] = x;
        ys[slot] = y;
        zs[slot] = z;
        count++;
        fix = true;
        lastSeen = tick;
        px = x;
        py = y;
        pz = z;
        if (count >= 2) {
            double span = tick - ticks[tail];
            double rx = (x - xs[tail]) / span;
            double ry = (y - ys[tail]) / span;
            double rz = (z - zs[tail]) / span;
            if (!moving || reseed) {
                vx = rx;
                vy = ry;
                vz = rz;
            } else {
                vx = SMOOTH * rx + (1.0 - SMOOTH) * vx;
                vy = SMOOTH * ry + (1.0 - SMOOTH) * vy;
                vz = SMOOTH * rz + (1.0 - SMOOTH) * vz;
            }
            moving = true;
            reseed = false;
        }
    }

    public void unseen(long tick) {
        startClock(tick);
    }

    public boolean hasFix() {
        return fix;
    }

    public boolean hasVelocity() {
        return moving;
    }

    public double[] position() {
        return new double[]{px, py, pz};
    }

    public double[] velocity() {
        return new double[]{vx, vy, vz};
    }

    public double[] predict(long now, double ticksAhead) {
        if (!fix) return position();
        double dt = (double) (now - lastSeen) + ticksAhead;
        return new double[]{px + vx * dt, py + vy * dt, pz + vz * dt};
    }

    public long lastSeen() {
        return lastSeen;
    }

    public boolean lostContact(long now) {
        if (fix) return now - lastSeen > MAX_GAP;
        return clock && now - since > MAX_GAP;
    }

    public boolean expired(long now) {
        if (fix) return now - lastSeen >= LOST_CLEAR;
        return clock && now - since >= LOST_CLEAR;
    }

    public void reset() {
        tail = 0;
        count = 0;
        fix = false;
        lastSeen = Long.MIN_VALUE;
        px = 0.0;
        py = 0.0;
        pz = 0.0;
        moving = false;
        reseed = false;
        vx = 0.0;
        vy = 0.0;
        vz = 0.0;
        clock = false;
        since = 0L;
    }

    private void startClock(long tick) {
        if (!clock) {
            clock = true;
            since = tick;
        }
    }
}
