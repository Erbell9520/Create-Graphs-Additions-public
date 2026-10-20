package com.createstats.battle.core;

public final class ShipFrame {
    private static final int[] BX = {1, 0, -1, 0};
    private static final int[] BZ = {0, 1, 0, -1};

    private final int bow;

    public ShipFrame(int bowQuarterTurns) {
        this.bow = Math.floorMod(bowQuarterTurns, 4);
    }

    public static ShipFrame ground() {
        return new ShipFrame(0);
    }

    public int bow() {
        return bow;
    }

    public ShipFrame turned() {
        return new ShipFrame(bow + 1);
    }

    public int fore(int dx, int dz) {
        return dx * BX[bow] + dz * BZ[bow];
    }

    public int stbd(int dx, int dz) {
        int s = (bow + 1) & 3;
        return dx * BX[s] + dz * BZ[s];
    }

    public int dx(int fore, int stbd) {
        return fore * BX[bow] + stbd * BX[(bow + 1) & 3];
    }

    public int dz(int fore, int stbd) {
        return fore * BZ[bow] + stbd * BZ[(bow + 1) & 3];
    }

    public int bowX() {
        return BX[bow];
    }

    public int bowZ() {
        return BZ[bow];
    }

    public static int quarterOf(double x, double z) {
        int best = 0;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (int q = 0; q < 4; q++) {
            double dot = x * BX[q] + z * BZ[q];
            if (dot > bestDot + 1.0e-12) {
                bestDot = dot;
                best = q;
            }
        }
        return best;
    }

    public static int[] rotate(int dx, int dz, int quarterTurns) {
        int x = dx;
        int z = dz;
        for (int i = 0; i < Math.floorMod(quarterTurns, 4); i++) {
            int t = x;
            x = -z;
            z = t;
        }
        return new int[]{x, z};
    }
}
