package com.createstats.efficiency;

public record EfficiencyInputs(
        float capacitySU,
        float stressSU,
        float peakStressSU,
        float stressCv,
        boolean stressCvKnown,
        float rpmCv,
        boolean rpmKnown,
        boolean overstressed,
        float longestOverstressRunSeconds,
        boolean resilienceKnown,
        float overstressRate,
        boolean overstressRateKnown,
        long computedAtTick) {
    public float utilization() {
        return this.capacitySU > 0.0f ? this.stressSU / this.capacitySU : 0.0f;
    }

    public float peakUtilization() {
        if (this.capacitySU <= 0.0f) {
            return 0.0f;
        }
        return Math.max(this.utilization(), this.peakStressSU / this.capacitySU);
    }

    public boolean hasNetwork() {
        return this.capacitySU > 0.0f;
    }
}
