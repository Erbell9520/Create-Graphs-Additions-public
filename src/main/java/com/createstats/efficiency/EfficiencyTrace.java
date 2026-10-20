package com.createstats.efficiency;

public final class EfficiencyTrace {
    public float capacitySU;
    public float stressSU;
    public float peakStressSU;

    public float u;

    public float peakU;
    public float rpmCv;
    public boolean rpmKnown;
    public float stressCv;
    public boolean stressCvKnown;
    public boolean overstressed;
    public float longestOverstressRunSeconds;
    public boolean resilienceKnown;

    public float overstressRate;

    public float[] phi = new float[EfficiencyDimension.values().length];

    public float[] weightApplied = new float[EfficiencyDimension.values().length];

    public float[] term = new float[EfficiencyDimension.values().length];

    public int knownMask;

    public float totalWeight;

    public float meanSum;
    public float equilibrium;
    public float observability;

    public float previousU;
    public float duDt;

    public float swingRaw;
    public float swingApplied;

    public float severity;
    public float overstressPenalty;

    public float psi;

    public float penaltyPoints;

    public int elapsedTicks;

    public float dtSeconds;
    public boolean stepClamped;
    public float alpha;

    public boolean rising;

    public float relaxBlend;

    public boolean warmStart;
    public float ePrev;

    public float eRelaxed;

    public float eBeforeClamp;
    public float eNew;
    public int observations;
    public float uptimeMemoryBefore;
    public float uptimeMemoryAfter;
    public long computedAtTick;

    public float safeDt() {
        return Math.max(1.0E-4f, this.dtSeconds);
    }

    public boolean hasPreviousU() {
        return this.previousU >= 0.0f;
    }

    public boolean isKnown(EfficiencyDimension dim) {
        return (this.knownMask & (1 << dim.ordinal())) != 0;
    }
}
