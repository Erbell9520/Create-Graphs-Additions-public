package com.createstats.power;

public final class FeSupplyBudget {
    private long tick = Long.MIN_VALUE;
    private int left;

    public static int ceilingOf(boolean hasUncappedSupply, int... caps) {
        if (hasUncappedSupply || caps.length == 0) {
            return FeGridSolver.NO_CEILING;
        }
        int total = 0;
        for (int cap : caps) {
            total = FeRates.plus(total, Math.max(0, cap));
        }
        return Math.min(total, FeGridSolver.NO_CEILING);
    }

    public int remaining(int ceiling, long now) {
        if (ceiling >= FeGridSolver.NO_CEILING) {
            return FeGridSolver.NO_CEILING;
        }
        if (now != this.tick) {
            this.tick = now;
            this.left = Math.max(0, ceiling);
        }
        return this.left;
    }

    public void spend(int ceiling, long now, int fe) {
        if (fe <= 0 || this.remaining(ceiling, now) >= FeGridSolver.NO_CEILING) {
            return;
        }
        this.left = Math.max(0, this.left - fe);
    }

    public void credit(int ceiling, long now, int fe) {
        if (fe <= 0 || this.remaining(ceiling, now) >= FeGridSolver.NO_CEILING) {
            return;
        }
        this.left = FeRates.plus(this.left, fe);
    }
}
