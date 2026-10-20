package com.createstats.stormcoil;

// nearest stud within reach, same as the breaker lugs
public final class StormCoilStuds {
    public static final int IN = 0;
    public static final int OUT = 1;
    public static final int COUNT = 2;

    public static final double EYE_X = 0.7;
    public static final double EYE_Y = 3.6;
    public static final double[] STUD_Z = {4.5, 11.5};

    private StormCoilStuds() {
    }

    public static boolean isStud(int stud) {
        return stud == IN || stud == OUT;
    }

    public static double[] anchor(int turns, int stud) {
        final double[] p = turn(turns, EYE_X, STUD_Z[stud == OUT ? OUT : IN]);
        return new double[] {p[0] / 16.0, EYE_Y / 16.0, p[1] / 16.0};
    }

    public static int pick(int turns, double x, double y, double z) {
        final double[] p = turn((4 - Math.floorMod(turns, 4)) % 4, x * 16.0, z * 16.0);
        final double py = y * 16.0;
        if (p[0] > 5.0 || py < 0.5 || py > 7.0) {
            return -1;
        }
        int best = -1;
        double bestD = 3.6;
        for (int s = 0; s < COUNT; s++) {
            final double d = Math.abs(p[1] - STUD_Z[s]);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    public static double[] turn(int turns, double x, double z) {
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> new double[] {16 - z, x};
            case 2 -> new double[] {16 - x, 16 - z};
            case 3 -> new double[] {z, 16 - x};
            default -> new double[] {x, z};
        };
    }
}
