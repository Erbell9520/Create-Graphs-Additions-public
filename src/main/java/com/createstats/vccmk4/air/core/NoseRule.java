package com.createstats.vccmk4.air.core;

import java.util.List;

// nose follows the engines, a placed block facing looked across the ship
public final class NoseRule {
    public static final double CLEAR = 1.5;

    private NoseRule() {
    }

    public static int proposeTurns(int facingX, int facingZ, List<double[]> pushes) {
        double alongX = 0.0;
        double alongZ = 0.0;
        double pointX = 0.0;
        double pointZ = 0.0;
        for (double[] p : pushes) {
            if (p == null || p.length < 4) continue;
            double w = p[3] > 0.0 && Double.isFinite(p[3]) ? p[3] : 0.0;
            double ax = Double.isFinite(p[0]) ? p[0] : 0.0;
            double az = Double.isFinite(p[2]) ? p[2] : 0.0;
            alongX += w * Math.abs(ax);
            alongZ += w * Math.abs(az);
            pointX += w * ax;
            pointZ += w * az;
        }
        boolean xAxis;
        if (alongX > CLEAR * alongZ && alongX > 1.0e-9) xAxis = true;
        else if (alongZ > CLEAR * alongX && alongZ > 1.0e-9) xAxis = false;
        else return 0;
        boolean facingOnAxis = xAxis ? facingX != 0 : facingZ != 0;
        if (facingOnAxis) return 0;
        double point = xAxis ? pointX : pointZ;
        double scale = xAxis ? alongX : alongZ;
        if (Math.abs(point) < 1.0e-6 * scale) return 1;
        int noseX = xAxis ? (point > 0.0 ? 1 : -1) : 0;
        int noseZ = xAxis ? 0 : (point > 0.0 ? 1 : -1);
        return Math.floorMod(quarter(noseX, noseZ) - quarter(facingX, facingZ), 4);
    }

    static int quarter(int x, int z) {
        if (z < 0) return 0;
        if (x > 0) return 1;
        if (z > 0) return 2;
        return 3;
    }
}
