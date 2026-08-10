package com.createstats.efficiency;

import net.minecraft.nbt.CompoundTag;

public record EfficiencyScore(float compositeScore, String grade, int gradeColor, float[] dimensionScores, long computedAtTick) {
    public float getDimensionPercent(EfficiencyDimension dim) {
        return this.dimensionScores[dim.ordinal()] * 100.0f;
    }
    
    public static EfficiencyScore noNetwork() {
        return new EfficiencyScore(0.0f, "?", -7829368, new float[EfficiencyDimension.values().length], 0L);
    }
    
    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putFloat("Score", this.compositeScore);
        tag.putString("Grade", this.grade);
        tag.putInt("Color", this.gradeColor);
        tag.putLong("Tick", this.computedAtTick);
        if (this.dimensionScores != null) {
            for (int i = 0; i < this.dimensionScores.length; ++i) {
                tag.putFloat("Dim" + i, this.dimensionScores[i]);
            }
        }
        return tag;
    }
    
    public static EfficiencyScore load(CompoundTag tag) {
        final float score = tag.getFloat("Score");
        final String grade = tag.getString("Grade");
        final int color = tag.getInt("Color");
        final long tick = tag.getLong("Tick");
        final int n = EfficiencyDimension.values().length;
        final float[] dims = new float[n];
        for (int i = 0; i < n; ++i) {
            dims[i] = tag.getFloat("Dim" + i);
        }
        return new EfficiencyScore(score, grade, color, dims, tick);
    }
}
