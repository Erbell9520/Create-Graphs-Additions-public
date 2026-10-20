package com.createstats.stormcoil;

public final class CoilForm {
    public static final double DECK = 10.4;

    public static final double SUNK = 0.3;

    static final double[] MAJOR = {4.6, 6.2, 7.8, 9.6};

    static final double[] TUBE = {1.8, 2.3, 2.8, 3.4};

    static final double[] ORB = {2.4, 3.0, 3.8, 4.8};

    public static final double COLUMN = 2.4;

    public final double height;
    public final double top;
    public final double toroidY;
    public final double major;
    public final double tube;

    public final double rods;

    public final double orb;
    public final double orbY;

    private CoilForm(double height) {
        this.height = height;
        final double h = Math.max(1.0, Math.min(4.0, height));
        final int lo = (int) Math.floor(h);
        final int hi = Math.min(4, lo + 1);
        final double f = smooth(h - lo);
        this.major = MAJOR[lo - 1] + (MAJOR[hi - 1] - MAJOR[lo - 1]) * f;
        this.tube = TUBE[lo - 1] + (TUBE[hi - 1] - TUBE[lo - 1]) * f;
        this.top = 16.0 * h - 0.6;
        this.toroidY = top - tube;
        this.rods = smooth(Math.max(0.0, Math.min(1.0, h - 3.0)));
        this.orb = ORB[lo - 1] + (ORB[hi - 1] - ORB[lo - 1]) * f;
        this.orbY = toroidY + tube * 0.5 + orb;
    }

    public static CoilForm at(double height) {
        return new CoilForm(height);
    }

    public double[] section(int k) {
        final double length = Math.max(1.0, toroidY - DECK);
        final double each = length / 3.0;
        final double start = DECK + each * k - (k > 0 ? 0.6 : SUNK);
        return new double[] {start, DECK + each * (k + 1)};
    }

    public double eyeY(double duck) {
        final double ceiling = 16.0 * height - 0.25;
        final double sink = Math.max(0.0, orbY + orb - ceiling);
        return orbY - Math.max(0.0, Math.min(1.0, duck)) * sink;
    }

    public double sectionRadius(int k) {
        return COLUMN - 0.3 * k;
    }

    static double smooth(double t) {
        final double u = Math.max(0.0, Math.min(1.0, t));
        return u * u * (3 - 2 * u);
    }
}
