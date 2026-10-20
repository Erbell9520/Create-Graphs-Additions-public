package com.createstats.efficiency;

// the old one imported CompoundTag so the grader couldn't even load in a test
public final class ODEState {
    public static final float NO_PREVIOUS_U = -1.0f;

    public static final int NO_GRADE = -1;

    public static final float INITIAL_E = 50.0f;

    public float currentE = INITIAL_E;

    public float previousU = NO_PREVIOUS_U;

    public float uptimeMemory = 1.0f;

    public int observations = 0;

    public int gradeIndex = NO_GRADE;

    public boolean uptimeSeeded = false;

    public ODEState() {
    }

    public ODEState copy() {
        final ODEState s = new ODEState();
        s.currentE = this.currentE;
        s.previousU = this.previousU;
        s.uptimeMemory = this.uptimeMemory;
        s.observations = this.observations;
        s.gradeIndex = this.gradeIndex;
        s.uptimeSeeded = this.uptimeSeeded;
        return s;
    }

    public boolean isWarmingUp() {
        return this.observations < EfficiencyGrader.WARMUP_OBSERVATIONS;
    }

    public float warmupProgress() {
        return Math.min(1.0f, this.observations / (float) EfficiencyGrader.WARMUP_OBSERVATIONS);
    }
}
