package com.createstats.chunkloader.look;

import static com.createstats.chunkloader.look.CrownGeometry.MAX;
import static com.createstats.chunkloader.look.CrownGeometry.TIERS;

public final class CrownAnchors {
    public int pods;
    public final int[] k = new int[MAX];
    public final int[] tier = new int[MAX];
    public final int[] ink = new int[MAX];
    public final double[] x = new double[MAX];
    public final double[] y = new double[MAX];
    public final double[] z = new double[MAX];
    public final double[] size = new double[MAX];
    public final double[] glow = new double[MAX];
    public final double[] mount = new double[MAX];

    public final double[] az = new double[MAX];

    public final double[] r = new double[MAX];
    public final double[] a0 = new double[MAX];
    public final double[] a1 = new double[MAX];

    public final boolean[] knob = new boolean[TIERS * 2];
    public final double[] knobX = new double[TIERS * 2];
    public final double[] knobY = new double[TIERS * 2];
    public final double[] knobZ = new double[TIERS * 2];

    public final boolean[] yokeEnd = new boolean[2];
    public final double[] yokeX = new double[2];
    public final double[] yokeY = new double[2];
    public final double[] yokeZ = new double[2];

    public double yoke;

    public double heartX;
    public double heartY;
    public double heartZ;
    public double heartSize;
    public double capOpen;

    public void clear() {
        pods = 0;
        java.util.Arrays.fill(knob, false);
        yokeEnd[0] = false;
        yokeEnd[1] = false;
        yoke = 0.0;
        heartSize = 0.0;
        capOpen = 0.0;
    }

    void knob(int t, int side, double kx, double ky, double kz) {
        final int i = t * 2 + side;
        knob[i] = true;
        knobX[i] = kx;
        knobY[i] = ky;
        knobZ[i] = kz;
    }
}
