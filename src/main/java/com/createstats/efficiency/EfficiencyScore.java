package com.createstats.efficiency;

public record EfficiencyScore(
        float compositeScore,
        String grade,
        int gradeColor,
        float[] dimensionScores,
        int knownMask,
        float observability,
        float warmup,
        float equilibrium,
        long computedAtTick) {
    public float getDimensionPercent(EfficiencyDimension dim) {
        if (this.dimensionScores == null || dim.ordinal() >= this.dimensionScores.length) {
            return 0.0f;
        }
        return this.dimensionScores[dim.ordinal()] * 100.0f;
    }

    // unmeasured is not 0%. an empty bar reads as "terrible" when it means "nothing bound"
    public boolean isKnown(EfficiencyDimension dim) {
        return (this.knownMask & (1 << dim.ordinal())) != 0;
    }

    public boolean isWarmingUp() {
        return this.warmup < 1.0f;
    }

    public boolean isPartial() {
        return this.observability < 0.999f;
    }

    public static EfficiencyScore noNetwork() {
        return new EfficiencyScore(0.0f, GradeScale.UNKNOWN_LETTER, GradeScale.UNKNOWN_COLOR,
                new float[EfficiencyDimension.values().length], 0, 0.0f, 0.0f, 0.0f, 0L);
    }
}
