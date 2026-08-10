package com.createstats.prediction;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;

public class PredictionConfig
{
    private boolean enabled;
    private int windowSamples;
    private float smoothingAlpha;
    private float confidenceLevel;
    private int maxPredictionSeconds;
    
    public PredictionConfig() {
        this.enabled = false;
        this.windowSamples = 0;
        this.smoothingAlpha = 0.5f;
        this.confidenceLevel = 0.9f;
        this.maxPredictionSeconds = 600;
    }
    
    public boolean isEnabled() {
        return this.enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getWindowSamples() {
        return this.windowSamples;
    }
    
    public void setWindowSamples(int windowSamples) {
        this.windowSamples = Math.max(0, windowSamples);
    }
    
    public float getSmoothingAlpha() {
        return this.smoothingAlpha;
    }
    
    public void setSmoothingAlpha(float alpha) {
        this.smoothingAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }
    
    public float getConfidenceLevel() {
        return this.confidenceLevel;
    }
    
    public void setConfidenceLevel(float level) {
        this.confidenceLevel = Math.max(0.5f, Math.min(0.99f, level));
    }
    
    public int getMaxPredictionSeconds() {
        return this.maxPredictionSeconds;
    }
    
    public void setMaxPredictionSeconds(int seconds) {
        this.maxPredictionSeconds = Math.max(60, Math.min(3600, seconds));
    }
    
    public double getTCritical() {
        if (this.confidenceLevel >= 0.95f) {
            return 1.96;
        }
        if (this.confidenceLevel >= 0.9f) {
            return 1.645;
        }
        return 1.0;
    }
    
    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("PredEnabled", this.enabled);
        tag.putInt("PredWindow", this.windowSamples);
        tag.putFloat("PredAlpha", this.smoothingAlpha);
        tag.putFloat("PredConfidence", this.confidenceLevel);
        tag.putInt("PredMaxSeconds", this.maxPredictionSeconds);
        return tag;
    }
    
    public static PredictionConfig load(CompoundTag tag) {
        final PredictionConfig cfg = new PredictionConfig();
        if (tag.contains("PredEnabled")) {
            cfg.enabled = tag.getBoolean("PredEnabled");
        }
        if (tag.contains("PredWindow")) {
            cfg.windowSamples = tag.getInt("PredWindow");
        }
        if (tag.contains("PredAlpha")) {
            cfg.smoothingAlpha = tag.getFloat("PredAlpha");
        }
        if (tag.contains("PredConfidence")) {
            cfg.confidenceLevel = tag.getFloat("PredConfidence");
        }
        if (tag.contains("PredMaxSeconds")) {
            cfg.maxPredictionSeconds = tag.getInt("PredMaxSeconds");
        }
        return cfg;
    }
    
    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeBoolean(this.enabled);
        buf.writeVarInt(this.windowSamples);
        buf.writeFloat(this.smoothingAlpha);
        buf.writeFloat(this.confidenceLevel);
        buf.writeVarInt(this.maxPredictionSeconds);
    }
    
    public static PredictionConfig readFromBuf(FriendlyByteBuf buf) {
        final PredictionConfig cfg = new PredictionConfig();
        cfg.enabled = buf.readBoolean();
        cfg.windowSamples = buf.readVarInt();
        cfg.smoothingAlpha = buf.readFloat();
        cfg.confidenceLevel = buf.readFloat();
        cfg.maxPredictionSeconds = buf.readVarInt();
        return cfg;
    }
}
