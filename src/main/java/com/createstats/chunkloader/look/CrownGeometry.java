package com.createstats.chunkloader.look;

// ported number for number from the concept model
public final class CrownGeometry {
    public static final int TIERS = 5;
    public static final int PER_TIER = 5;
    public static final int MAX = TIERS * PER_TIER;

    public static final double[] RING_Y = {14.0, 20.6, 28.0, 35.4, 42.2};

    public static final double RING0_LOW = 12.8;
    public static final double RING0_HIGH = 14.0;

    public static final double[] RING_R = {4.2, 6.9, 9.3, 11.9, 14.6};
    public static final double[] RIM_W = {1.0, 1.2, 1.4, 1.7, 2.0};
    public static final double[] RIM_H = {1.5, 1.5, 1.8, 2.1, 2.4};

    public static final double[] POD = {1.9, 1.8, 2.1, 2.4, 2.7};
    public static final double[] HUB_R = {1.9, 2.2, 2.5, 2.8, 3.1};

    public static final double[] ARC_GAP = {0.15, 2.2, 2.4, 2.5, 2.6};
    public static final double[] KNOB = {1.0, 2.2, 2.5, 2.8, 3.1};
    public static final double WELL_STUD_Y = 13.4;

    public static final double[] HORN_AZ = {0.0, 180.0};

    public static final double HORN_ROOT_R = 7.6;
    public static final double HORN_ROOT_Y = 11.0;

    public static final double SECTOR = 72.0;

    public static final double YOKE_END_R = 2.6;
    public static final double YOKE_END_Y = 46.6;

    public static final double YOKE_STUB = 2.0;

    public static final double YOKE_SHOULDER = 5.0;

    public static final double[] BAY = {3.5, 2.6, 12.5, 10.0, 6.6};
    public static final double[] STUD_X = {5.6, 10.4};
    public static final double STUD_Y = 6.3;

    public static final double OMEGA = 9.0;

    public static final double OMEGA_MIN = 0.30;

    private CrownGeometry() {
    }

    public static int tierOf(int k) {
        return (k - 1) / PER_TIER;
    }

    public static int slotOf(int k) {
        return (k - 1) % PER_TIER;
    }

    public static int dir(int tier) {
        return tier % 2 == 0 ? 1 : -1;
    }

    public static double phase0(int tier) {
        return tier * 17.0;
    }

    public static double podOuter(int t) {
        return RING_R[t] + RIM_W[t] / 2 + POD[t];
    }

    public static double knobInner(int t) {
        return podOuter(t) + ARC_GAP[t];
    }

    public static double knobCentre(int t) {
        return knobInner(t) + KNOB[t] / 2;
    }

    public static double knobHeight(int t) {
        return t == 0 ? WELL_STUD_Y : RING_Y[t];
    }

    public static double ring0YFor(int n) {
        final int c = Math.max(0, Math.min(PER_TIER, n));
        return RING0_LOW + (RING0_HIGH - RING0_LOW) * c / PER_TIER;
    }

    public static int tiersFor(int n) {
        return n <= 0 ? 1 : tierOf(n) + 1;
    }

    public static double stemTopFor(int tp, double ring0Y) {
        if (tp <= 1) {
            return ring0Y - 0.2;
        }
        return RING_Y[tp - 1] + 1.4;
    }

    public static double hornTopFor(int n) {
        final int tp = n <= 0 ? 0 : tierOf(n) + 1;
        return tp >= 2 ? knobHeight(tp - 1) + 0.01 : HORN_ROOT_Y;
    }

    public static double yokePivotR() {
        return knobCentre(TIERS - 1);
    }

    public static double yokePivotY() {
        return RING_Y[TIERS - 1] + KNOB[TIERS - 1] / 2;
    }

    public static double[][] hornProfile() {
        final double[][] pts = new double[TIERS][];
        pts[0] = new double[] {HORN_ROOT_R, HORN_ROOT_Y};
        for (int t = 1; t < TIERS; t++) {
            pts[t] = new double[] {knobCentre(t), knobHeight(t)};
        }
        return pts;
    }

    public static void yokeArm(double stub, double swing, double ext, double[] out) {
        final double px = yokePivotR();
        final double py = yokePivotY();
        final double ex = YOKE_END_R;
        final double ey = YOKE_END_Y;
        final double sx = px - YOKE_SHOULDER;
        final double sy = ey - 0.2;
        final double aUp = 90.0;
        final double aSh = Math.toDegrees(Math.atan2(sy - py, sx - px));
        final double ang = Math.toRadians(aUp + (aSh - aUp) * swing);
        final double l1 = YOKE_STUB * Math.max(0.0, stub)
                + (Math.hypot(sx - px, sy - py) - YOKE_STUB) * Math.max(0.0, Math.min(1.0, swing));
        final double shr = px + l1 * Math.cos(ang);
        final double shy = py + l1 * Math.sin(ang);
        final double l2 = (sx - ex) * Math.max(0.0, ext);
        final double tilt = Math.toRadians(180.0 - 0.8 * Math.max(0.0, Math.min(1.0, swing)));
        out[0] = px;
        out[1] = py;
        out[2] = shr;
        out[3] = shy;
        out[4] = shr + l2 * Math.cos(tilt);
        out[5] = shy + l2 * Math.sin(tilt);
    }

    public static double chunkCost(int k) {
        return 1024.0 * Math.pow(1.10, k - 1);
    }

    public static double drawLevel(double drawFePerTick) {
        if (drawFePerTick <= 1024.0) {
            return 0.0;
        }
        final double k = Math.log(drawFePerTick / 1024.0) / Math.log(100_707.0 / 1024.0);
        return Math.max(0.0, Math.min(1.0, k));
    }

    public static double atX(double r, double az) {
        return 8.0 + r * Math.cos(Math.toRadians(az));
    }

    public static double atZ(double r, double az) {
        return 8.0 - r * Math.sin(Math.toRadians(az));
    }

    public static double angDiff(double a, double b) {
        double d = (a - b + 180.0) % 360.0;
        if (d < 0) {
            d += 360.0;
        }
        return d - 180.0;
    }
}
