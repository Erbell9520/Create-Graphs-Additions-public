package com.createstats.stormcoil;

public final class CoilCabinet {
    public static final int JARS = CoilLaw.CAPACITOR_VOLLEYS;

    public static final int PER_SHELF = 4;

    public static final double BAY_X0 = 4.6;
    public static final double BAY_X1 = 15.4;
    public static final double BAY_TOP = 6.3;
    public static final double BAY_DEPTH = 3.4;

    public static final double[] SHELF_Y = {0.9, 3.75};
    public static final double SHELF_THICK = 0.25;

    public static final double[] JAR_X = {6.05, 8.7, 11.35, 14.0};

    public static final double[] JAR_Z = {1.35, 2.14};

    public static final double UPPER_SHELF_Z = 1.0;

    public static final double JAR_R = 1.05;
    public static final double FOIL_PROUD = 0.04;

    public static final double FOIL_TOP = 0.62;
    public static final double GLASS_TOP = 1.6;
    public static final double CAP_TOP = 1.8;

    public static final double CAP_PROUD = 0.05;
    public static final double ROD_TOP = 1.98;

    public static final double KNOB_Y = 2.13;
    public static final double KNOB_R = 0.26;

    public static final double RISER_X0 = 4.6;
    public static final double RISER_X1 = 4.9;

    public static final double GAP_X = 8.0;
    public static final double GAP_Z = 5.0;
    public static final double DISC_Y0 = 8.0;
    public static final double DISC_Y1 = 8.4;
    public static final double DISC_R = 2.9;

    public static final double STUD_IN = 2.6;
    public static final double STUD_OUT = 3.9;
    public static final double STUD_HALF = 0.27;

    public static final double SPARK_GAP = 0.35;

    public static final double[] ELECTRODE_X = {GAP_X - STUD_OUT - SPARK_GAP, GAP_X + STUD_OUT + SPARK_GAP};
    public static final double ELECTRODE_Y = (DISC_Y0 + DISC_Y1) / 2;

    public static final double WINDOW_X0 = 3.0;
    public static final double WINDOW_X1 = 13.0;
    public static final double GLASS_Z = 0.55;

    public static final double HOUSE_FLOOR = 7.05;
    public static final double HOUSE_CEILING = 10.0;
    public static final double HOUSE_X0 = 1.0;
    public static final double HOUSE_X1 = 15.0;

    public static final int MARKS = CoilLaw.STRIKES_HELD;

    public static final double MARK_Y0 = 7.6;
    public static final double MARK_Y1 = 9.4;
    public static final double MARK_Z0 = 2.1;
    public static final double MARK_PITCH = 1.2;
    public static final double MARK_W = 0.9;

    public static final double[] SECURITY_LAMP = {14.5, 8.5, 0.0};

    public static final double XF_X0 = 4.6;
    public static final double XF_X1 = 15.4;
    public static final double XF_FLOOR = 0.9;
    public static final double XF_TOP = 6.3;

    public static final double XF_BACK = 12.35;

    public static final double[] LEG_X = {7.0, 13.0};
    public static final double LEG_Z = 14.2;
    public static final double LEG_HALF_X = 0.7;
    public static final double LEG_HALF_Z = 0.9;

    public static final double YOKE_Y0 = 1.2;
    public static final double YOKE_LOW = 2.0;
    public static final double YOKE_HIGH = 5.0;
    public static final double YOKE_Y1 = 5.8;

    public static final double WIND_R = 1.45;
    public static final double WIND_W = 0.3;
    public static final double WIND_Y0 = 2.35;
    public static final double WIND_Y1 = 4.65;
    public static final int WIND_TURNS = 3;

    public static final double[] LOUVRE_Y = {1.35, 2.2, 3.05};

    public static final double DIAL_X = 2.0;
    public static final double DIAL_Y = 3.4;
    public static final double DIAL_R = 1.6;
    public static final double NEEDLE_Y = 2.45;
    public static final double NEEDLE_LEN = 1.75;
    public static final double NEEDLE_SWEEP = 55.0;

    public static final double BUS_OUT = 0.3;
    public static final double BUS_Y0 = 0.15;
    public static final double BUS_Y1 = 0.75;

    public static final double TERMINAL_X = 10.0;

    public static final double RETURN_STRAP = 4.8;

    public static final double RETURN_BUS = 5.0;

    public static final double RETURN_TICKS = RETURN_STRAP + RETURN_BUS + 2.5;

    public static final double BOX_Y = 5.5;
    public static final double TARGETS_Z = 4.5;
    public static final double HEIGHT_Z = 11.5;
    public static final double BOX_SCALE = 0.35;
    public static final double BOX_HALF = BOX_SCALE * 16 / 2;

    public static final double ICON_HALF = BOX_HALF / 2;

    public static final double BEZEL_IN = 2.1;
    public static final double BEZEL_OUT = 2.7;

    public static final double PLATE_Y0 = 0.8;
    public static final double PLATE_Y1 = 9.8;
    public static final double PLATE_Z0 = 0.3;
    public static final double PLATE_Z1 = 15.7;

    public static final double LAMP_Z0 = 0.6;
    public static final double LAMP_Z1 = 1.5;
    public static final double[] LAMP_Y = {3.2, 4.9, 6.6};
    public static final double LAMP_H = 1.2;

    public static final double LADDER_Z0 = 14.5;
    public static final double LADDER_Z1 = 15.4;
    public static final double[] NOTCH_Y = {2.9, 4.2, 5.5, 6.8};
    public static final double NOTCH_H = 1.0;

    public static final int BLINK_TICKS = 16;

    public enum Notch {
        DARK, LIT, BLOCKED_ON, BLOCKED_OFF
    }

    private CoilCabinet() {
    }

    public static boolean[] targetLamps(CoilRules.Policy policy) {
        if (policy == null) {
            return new boolean[3];
        }
        final CoilRules.Rule rule = policy.rule() != null ? policy.rule() : CoilRules.ruleOf(policy.mode());
        return new boolean[] {rule.hostile(), rule.transform(), rule.arcsAtPlayers()};
    }

    public static Notch notch(int k, double shownHeight, int requested, boolean blocked, long tick) {
        if (shownHeight >= k - 0.5) {
            return Notch.LIT;
        }
        if (blocked && k == requested) {
            return Math.floorMod(tick, (long) BLINK_TICKS) < BLINK_TICKS / 2 ? Notch.BLOCKED_ON : Notch.BLOCKED_OFF;
        }
        return Notch.DARK;
    }

    public static double[] lug(int corner) {
        final double lo = -BUS_OUT;
        final double hi = 16 + BUS_OUT;
        return switch (Math.floorMod(corner, 4)) {
            case 0 -> new double[] {lo, lo};
            case 1 -> new double[] {hi, lo};
            case 2 -> new double[] {hi, hi};
            default -> new double[] {lo, hi};
        };
    }

    public static double[][] busPath(int corner) {
        final double hi = 16 + BUS_OUT;
        final double[] at = lug(corner);
        final double[] terminal = {TERMINAL_X, hi};
        return switch (Math.floorMod(corner, 4)) {
            case 0, 1 -> new double[][] {at, {at[0], hi}, terminal};
            default -> new double[][] {at, terminal};
        };
    }

    public static double strapHead(double sinceZap) {
        return 1.0 - sinceZap / RETURN_STRAP;
    }

    public static double busHead(double sinceZap) {
        return (sinceZap - RETURN_STRAP) / RETURN_BUS;
    }

    public static double terminalFlash(double sinceZap) {
        final double after = sinceZap - RETURN_STRAP - RETURN_BUS;
        return after < -0.5 || after > RETURN_TICKS - RETURN_STRAP - RETURN_BUS ? 0.0
                : Math.max(0.0, 1.0 - Math.abs(after) / 2.5);
    }

    public static double jarX(int k) {
        return JAR_X[Math.floorMod(k, PER_SHELF)];
    }

    public static double jarY(int k) {
        return SHELF_Y[shelf(k)];
    }

    public static double jarZ(int k) {
        return JAR_Z[shelf(k)];
    }

    private static int shelf(int k) {
        return Math.max(0, Math.min(SHELF_Y.length - 1, k / PER_SHELF));
    }

    public static double markZ(int k) {
        return MARK_Z0 + k * MARK_PITCH;
    }

    public static double level(double fraction, int k, int lamps) {
        return Math.max(0.0, Math.min(1.0, lamps * fraction - k));
    }

    public static int lit(double fraction, int lamps) {
        int n = 0;
        for (int k = 0; k < lamps; k++) {
            if (level(fraction, k, lamps) > 0) {
                n++;
            }
        }
        return n;
    }
}
