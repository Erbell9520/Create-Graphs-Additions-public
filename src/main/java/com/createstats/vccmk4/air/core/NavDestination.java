package com.createstats.vccmk4.air.core;

import javax.annotation.Nullable;

public record NavDestination(Source source, double x, double y, double z, boolean tableElsewhere, String problem) {
    public enum Source {
        NONE, LODESTONE, TABLE
    }

    public static final double AGREE = 4.0;

    public static final NavDestination NONE = new NavDestination(Source.NONE, Double.NaN, Double.NaN, Double.NaN, false, "");

    public NavDestination {
        source = source == null ? Source.NONE : source;
        problem = problem == null ? "" : problem;
    }

    public boolean found() {
        return source != Source.NONE;
    }

    @Nullable
    public double[] xz() {
        return found() ? new double[] {x, z} : null;
    }

    public static NavDestination choose(boolean table, @Nullable double[] lodestone, boolean lodestoneLost,
                                        boolean otherDimension, @Nullable double[] tableTarget) {
        if (!table) return none("No Navigation Table on this ship: Go to has nowhere to go.");
        if (valid(lodestone)) {
            boolean elsewhere = valid(tableTarget) && distance(lodestone, tableTarget) > AGREE;
            return new NavDestination(Source.LODESTONE, lodestone[0], lodestone[1], lodestone[2], elsewhere, "");
        }
        if (lodestoneLost) {
            return none("The compass on the Navigation Table has lost its lodestone: lock it on one again for Go to.");
        }
        if (otherDimension) {
            return none("The compass on the Navigation Table points at a lodestone in another dimension.");
        }
        if (valid(tableTarget)) {
            return new NavDestination(Source.TABLE, tableTarget[0], tableTarget[1], tableTarget[2], false, "");
        }
        return none("Nothing on the Navigation Table to go to: put an anchored compass or a map on it.");
    }

    private static NavDestination none(String why) {
        return new NavDestination(Source.NONE, Double.NaN, Double.NaN, Double.NaN, false, why);
    }

    private static boolean valid(@Nullable double[] p) {
        return p != null && p.length >= 3 && Double.isFinite(p[0]) && Double.isFinite(p[1]) && Double.isFinite(p[2]);
    }

    private static double distance(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }
}
