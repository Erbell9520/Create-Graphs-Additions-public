package com.createstats.power;

public final class PduExportBudget {
    private long tick = Long.MIN_VALUE;

    private int allowance;

    private int left;

    public int remaining(int allowance, long now) {
        refill(allowance, now);
        return this.allowance >= FeGridSolver.NO_CEILING
                ? FeGridSolver.NO_CEILING : Math.max(0, this.allowance - spent());
    }

    public void refill(int allowance, long now) {
        if (now == tick) {
            return;
        }
        tick = now;
        this.allowance = Math.max(0, allowance);
        this.left = this.allowance;
    }

    public void spend(int allowance, long now, int fe) {
        if (fe <= 0) {
            return;
        }
        refill(allowance, now);
        if (this.allowance >= FeGridSolver.NO_CEILING) {
            return;
        }
        left = Math.max(0, left - fe);
    }

    public int allowance() {
        return allowance;
    }

    public int spent() {
        return allowance >= FeGridSolver.NO_CEILING ? 0 : Math.max(0, allowance - left);
    }

    public boolean isHeld(int widestConductor) {
        return allowance < FeGridSolver.NO_CEILING && widestConductor > 0
                && allowance < widestConductor && left <= 0;
    }
}
