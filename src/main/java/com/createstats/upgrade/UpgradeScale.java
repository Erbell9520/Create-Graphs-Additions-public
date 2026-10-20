package com.createstats.upgrade;

public final class UpgradeScale {
    public static final double FULL_SET = 1.5 * 2.0 * 4.0;

    private UpgradeScale() {
    }

    public static double product(double... multipliers) {
        double out = 1.0;
        for (final double m : multipliers) {
            if (m > 0.0) {
                out *= m;
            }
        }
        return out;
    }

    public static int rows(int baseRows, double multiplier, int maxRows) {
        final int ceiling = Math.max(baseRows, maxRows);
        if (!(multiplier > 1.0)) {
            return baseRows;
        }
        final long scaled = Math.round(baseRows * multiplier);
        return (int) Math.max(baseRows, Math.min(ceiling, scaled));
    }

    public static int stackCap(int baseCap, double multiplier, int maxCap) {
        final int ceiling = Math.max(baseCap, maxCap);
        if (baseCap <= 1) {
            return baseCap;
        }
        if (!(multiplier > 1.0)) {
            return baseCap;
        }
        final long scaled = Math.round((long) baseCap * multiplier);
        return (int) Math.max(baseCap, Math.min(ceiling, scaled));
    }

    public static boolean stranded(int highestOccupied, int columns, int baseRows,
                                   double multiplierAfter, int maxRows) {
        return rowsToHold(highestOccupied, columns) > rows(baseRows, multiplierAfter, maxRows);
    }

    public static boolean tooDeep(int deepestStack, int deepestBase, double multiplierAfter,
                                  int maxCap) {
        if (deepestStack <= 0 || deepestBase <= 0) {
            return false;
        }
        return deepestStack > stackCap(deepestBase, multiplierAfter, maxCap);
    }

    public static int rowsToHold(int highestOccupied, int columns) {
        if (highestOccupied < 0 || columns <= 0) {
            return 0;
        }
        return (highestOccupied / columns) + 1;
    }
}
