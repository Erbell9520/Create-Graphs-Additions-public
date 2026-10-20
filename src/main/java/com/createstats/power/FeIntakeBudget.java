package com.createstats.power;

public final class FeIntakeBudget {
    private long tick = Long.MIN_VALUE;
    private int left;

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
        if (fe <= 0 || remaining(ceiling, now) >= FeGridSolver.NO_CEILING) {
            return;
        }
        this.left = Math.max(0, this.left - fe);
    }
}
