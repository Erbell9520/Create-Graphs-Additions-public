package com.createstats.repeller;

// refusing spawns alone did nothing you could see. this walks them out
public final class RepelCourse {
    public static final double EXIT_MARGIN = 3.0;

    public static final double STEER_BEYOND = 8.0;

    public static final double ANCHOR_BEYOND = 20.0;

    public static final double PUSH_PER_TICK = 0.1;

    public static final double PUSH_SPEED = 0.3;

    public static final double PROGRESS_STEP = 0.5;

    public static final int STALL_TICKS = 40;

    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    public record Heading(double x, double z) {
    }

    public record Spot(double x, double z) {
    }

    private RepelCourse() {
    }

    public static double depth(RepellerShape shape, double cx, double cy, double cz, double r,
                               double x, double y, double z) {
        final double dx = x - cx;
        final double dz = z - cz;
        final double flat = dx * dx + dz * dz;
        if (shape == RepellerShape.COLUMN) {
            return r - Math.sqrt(flat);
        }
        final double dy = y - cy;
        return r - Math.sqrt(flat + dy * dy);
    }

    public static boolean stillLeaving(double depth) {
        return depth > -EXIT_MARGIN;
    }

    public static boolean worthTaking(double depthNow, double depthThere) {
        return depthThere <= depthNow - PROGRESS_STEP;
    }

    public static double fallbackAngle(int seed) {
        return (Math.floorMod(seed, 1 << 20) * GOLDEN_ANGLE) % (Math.PI * 2.0);
    }

    public static Heading outward(double cx, double cz, double x, double z, double fallbackAngle) {
        final double dx = x - cx;
        final double dz = z - cz;
        final double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-6) {
            return new Heading(Math.cos(fallbackAngle), Math.sin(fallbackAngle));
        }
        return new Heading(dx / len, dz / len);
    }

    public static Spot beyond(double cx, double cz, double r, double x, double z, double beyond,
                              double fallbackAngle) {
        final Heading out = outward(cx, cz, x, z, fallbackAngle);
        final double at = r + beyond;
        return new Spot(cx + out.x() * at, cz + out.z() * at);
    }

    public static double pushStep(double outwardSpeed) {
        return Math.max(0.0, Math.min(PUSH_PER_TICK, PUSH_SPEED - outwardSpeed));
    }

    public static final class Watch {
        private double best = Double.POSITIVE_INFINITY;
        private long since;

        public void start(long now, double depth) {
            best = depth;
            since = now;
        }

        public void observe(long now, double depth) {
            if (depth <= best - PROGRESS_STEP) {
                best = depth;
                since = now;
            }
        }

        public boolean stalled(long now) {
            return now - since > STALL_TICKS;
        }
    }
}
