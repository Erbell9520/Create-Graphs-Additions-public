package com.createstats.chunkloader.core;

public final class CrownStuds {
    public static final double STUD_A_X = 5.6;
    public static final double STUD_B_X = 10.4;
    public static final double STUD_Y = 6.3;
    public static final double CAP_Z = 0.05;

    private CrownStuds() {
    }

    public static int turnsFor(String front) {
        return switch (front) {
            case "west" -> 1;
            case "north" -> 2;
            case "east" -> 3;
            default -> 0;
        };
    }

    public static double[] attachPoint(int turns, int stud) {
        return turn(stud == 1 ? STUD_B_X : STUD_A_X, STUD_Y, CAP_Z, turns);
    }

    public static double[] bayCentre(int turns) {
        return turn((STUD_A_X + STUD_B_X) / 2.0, STUD_Y, CAP_Z, turns);
    }

    private static double[] turn(double px, double py, double pz, int turns) {
        double dx = px / 16.0 - 0.5;
        double dz = pz / 16.0 - 0.5;
        final int t = Math.floorMod(turns, 4);
        for (int i = 0; i < t; i++) {
            final double nx = -dz;
            final double nz = dx;
            dx = nx;
            dz = nz;
        }
        return new double[] {0.5 + dx, py / 16.0, 0.5 + dz};
    }

    public static int nearerStud(int turns, double dx, double dz, boolean freeA, boolean freeB) {
        if (!freeA && !freeB) {
            return -1;
        }
        if (freeA != freeB) {
            return freeA ? 0 : 1;
        }
        final double[] a = attachPoint(turns, 0);
        final double[] b = attachPoint(turns, 1);
        final double da = sq(dx + 0.5 - a[0]) + sq(dz + 0.5 - a[2]);
        final double db = sq(dx + 0.5 - b[0]) + sq(dz + 0.5 - b[2]);
        return db < da ? 1 : 0;
    }

    private static double sq(double v) {
        return v * v;
    }

    public static int[] split(int intake, int ratingA, int ratingB, boolean wiredA, boolean wiredB) {
        int left = Math.max(0, intake);
        final int a = wiredA ? Math.min(left, Math.max(0, ratingA)) : 0;
        left -= a;
        final int b = wiredB ? Math.min(left, Math.max(0, ratingB)) : 0;
        return new int[] {a, b};
    }
}
