package com.createstats.vccmk4.core;

public final class ConsoleShape {
    public static final double[][] NORTH = {
            {1, 0, 1, 15, 5.5, 15},
            {1, 5.5, 1.5, 15, 16, 5.5},
            {1, 5.5, 5.5, 15, 12.5, 6.5},
            {1, 5.5, 6.5, 15, 10.5, 7.5},
            {1, 5.5, 7.5, 15, 8.5, 8.5},
    };

    private ConsoleShape() {
    }

    public static double[] turn(double[] box, int quarterTurns) {
        double x0 = box[0], z0 = box[2], x1 = box[3], z1 = box[5];
        for (int i = 0; i < Math.floorMod(quarterTurns, 4); i++) {
            double nx0 = 16.0 - z1, nx1 = 16.0 - z0, nz0 = x0, nz1 = x1;
            x0 = nx0;
            x1 = nx1;
            z0 = nz0;
            z1 = nz1;
        }
        return new double[]{x0, box[1], z0, x1, box[4], z1};
    }

    public static double[][] boxes(int quarterTurns) {
        double[][] out = new double[NORTH.length][];
        for (int i = 0; i < NORTH.length; i++) out[i] = turn(NORTH[i], quarterTurns);
        return out;
    }
}
