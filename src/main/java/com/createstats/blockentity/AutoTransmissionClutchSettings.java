package com.createstats.blockentity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class AutoTransmissionClutchSettings {

    private static final float SU_MIN = 1.0f;
    private static final float SU_MAX = 65536.0f;
    private static final float PCT_MIN = 1.0f;
    private static final float PCT_MAX = 100.0f;

    private LimitMode limitMode;
    private float suLowerBound;
    private float suUpperBound;
    private float percentageLowerBound;
    private float percentageUpperBound;
    private float minimumRPM;
    private float maximumRPM;
    private boolean enabled;

    public AutoTransmissionClutchSettings() {
        this.limitMode = LimitMode.SU;
        this.suLowerBound = 128.0f;
        this.suUpperBound = 256.0f;
        this.percentageLowerBound = 50.0f;
        this.percentageUpperBound = 80.0f;
        this.minimumRPM = 16.0f;
        this.maximumRPM = 256.0f;
        this.enabled = true;
    }

    public LimitMode getLimitMode() { return this.limitMode; }
    public void setLimitMode(LimitMode m) { this.limitMode = m; }

    public void cycleLimitMode() {
        this.limitMode = this.limitMode.next();
    }

    public float getLowerBound() {
        return this.limitMode == LimitMode.PERCENTAGE ? this.percentageLowerBound : this.suLowerBound;
    }
    public void setLowerBound(float v) {
        if (this.limitMode == LimitMode.PERCENTAGE) {
            this.percentageLowerBound = clampLower(v, LimitMode.PERCENTAGE);
            if (this.percentageLowerBound > this.percentageUpperBound) {
                this.percentageUpperBound = this.percentageLowerBound;
            }
        } else {
            this.suLowerBound = clampLower(v, LimitMode.SU);
            if (this.suLowerBound > this.suUpperBound) {
                this.suUpperBound = this.suLowerBound;
            }
        }
    }

    public float getUpperBound() {
        return this.limitMode == LimitMode.PERCENTAGE ? this.percentageUpperBound : this.suUpperBound;
    }
    public void setUpperBound(float v) {
        if (this.limitMode == LimitMode.PERCENTAGE) {
            this.percentageUpperBound = clampUpper(v, LimitMode.PERCENTAGE);
            if (this.percentageUpperBound < this.percentageLowerBound) {
                this.percentageLowerBound = this.percentageUpperBound;
            }
        } else {
            this.suUpperBound = clampUpper(v, LimitMode.SU);
            if (this.suUpperBound < this.suLowerBound) {
                this.suLowerBound = this.suUpperBound;
            }
        }
    }

    // Backwards compatibility: single "limit value" maps to upper bound
    public float getLimitValue() { return this.getUpperBound(); }
    public void setLimitValue(float v) { setUpperBound(v); }

    public float getMinimumRPM() { return this.minimumRPM; }
    public void setMinimumRPM(float v) {
        this.minimumRPM = Math.max(1.0f, Math.min(256.0f, v));
        if (this.minimumRPM > this.maximumRPM) {
            this.maximumRPM = this.minimumRPM;
        }
    }

    public float getMaximumRPM() { return this.maximumRPM; }
    public void setMaximumRPM(float v) {
        this.maximumRPM = Math.max(1.0f, Math.min(256.0f, v));
        if (this.maximumRPM < this.minimumRPM) {
            this.minimumRPM = this.maximumRPM;
        }
    }

    public boolean isEnabled() { return this.enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }

    private float getMaxForMode() {
        return this.limitMode == LimitMode.PERCENTAGE ? PCT_MAX : SU_MAX;
    }

    private float getMinForMode() {
        return this.limitMode == LimitMode.PERCENTAGE ? PCT_MIN : SU_MIN;
    }

    private static float clampLower(float value, LimitMode mode) {
        return Math.max(mode == LimitMode.PERCENTAGE ? PCT_MIN : SU_MIN,
                Math.min(mode == LimitMode.PERCENTAGE ? PCT_MAX : SU_MAX, value));
    }

    private static float clampUpper(float value, LimitMode mode) {
        return Math.max(mode == LimitMode.PERCENTAGE ? PCT_MIN : SU_MIN,
                Math.min(mode == LimitMode.PERCENTAGE ? PCT_MAX : SU_MAX, value));
    }

    private void setBoundsForMode(LimitMode mode, float lower, float upper) {
        if (mode == LimitMode.PERCENTAGE) {
            this.percentageLowerBound = clampLower(lower, mode);
            this.percentageUpperBound = clampUpper(upper, mode);
            if (this.percentageLowerBound > this.percentageUpperBound) {
                this.percentageUpperBound = this.percentageLowerBound;
            }
        } else {
            this.suLowerBound = clampLower(lower, mode);
            this.suUpperBound = clampUpper(upper, mode);
            if (this.suLowerBound > this.suUpperBound) {
                this.suUpperBound = this.suLowerBound;
            }
        }
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("LimitMode", this.limitMode.name());
        tag.putFloat("LowerBound", this.getLowerBound());
        tag.putFloat("UpperBound", this.getUpperBound());
        tag.putFloat("SULowerBound", this.suLowerBound);
        tag.putFloat("SUUpperBound", this.suUpperBound);
        tag.putFloat("PercentageLowerBound", this.percentageLowerBound);
        tag.putFloat("PercentageUpperBound", this.percentageUpperBound);
        tag.putFloat("MinimumRPM", this.minimumRPM);
        tag.putFloat("MaximumRPM", this.maximumRPM);
        tag.putBoolean("Enabled", this.enabled);
        return tag;
    }

    public static AutoTransmissionClutchSettings load(CompoundTag tag) {
        final AutoTransmissionClutchSettings s = new AutoTransmissionClutchSettings();
        try { s.limitMode = LimitMode.valueOf(tag.getString("LimitMode")); } catch (Exception ignored) {}
        boolean loadedModeSpecificBounds = false;
        if (tag.contains("SULowerBound") || tag.contains("SUUpperBound")
                || tag.contains("PercentageLowerBound") || tag.contains("PercentageUpperBound")) {
            if (tag.contains("SULowerBound")) {
                s.suLowerBound = clampLower(tag.getFloat("SULowerBound"), LimitMode.SU);
                loadedModeSpecificBounds = true;
            }
            if (tag.contains("SUUpperBound")) {
                s.suUpperBound = clampUpper(tag.getFloat("SUUpperBound"), LimitMode.SU);
                loadedModeSpecificBounds = true;
            }
            if (tag.contains("PercentageLowerBound")) {
                s.percentageLowerBound = clampLower(tag.getFloat("PercentageLowerBound"), LimitMode.PERCENTAGE);
                loadedModeSpecificBounds = true;
            }
            if (tag.contains("PercentageUpperBound")) {
                s.percentageUpperBound = clampUpper(tag.getFloat("PercentageUpperBound"), LimitMode.PERCENTAGE);
                loadedModeSpecificBounds = true;
            }
            if (s.suLowerBound > s.suUpperBound) {
                s.suUpperBound = s.suLowerBound;
            }
            if (s.percentageLowerBound > s.percentageUpperBound) {
                s.percentageUpperBound = s.percentageLowerBound;
            }
        }
        if (!loadedModeSpecificBounds) {
            if (tag.contains("LowerBound")) {
                s.setLowerBound(tag.getFloat("LowerBound"));
            } else if (tag.contains("LimitValue")) {
                // Legacy migration: single limit becomes upper bound
                s.setUpperBound(tag.getFloat("LimitValue"));
                s.setLowerBound(s.getUpperBound() * 0.75f);
            }
            if (tag.contains("UpperBound")) s.setUpperBound(tag.getFloat("UpperBound"));
        }
        if (tag.contains("MinimumRPM")) s.setMinimumRPM(tag.getFloat("MinimumRPM"));
        if (tag.contains("MaximumRPM")) s.setMaximumRPM(tag.getFloat("MaximumRPM"));
        if (tag.contains("Enabled")) s.enabled = tag.getBoolean("Enabled");
        return s;
    }

    public void encode(FriendlyByteBuf buf) { buf.writeNbt(this.save()); }

    public static AutoTransmissionClutchSettings decode(FriendlyByteBuf buf) {
        final CompoundTag tag = buf.readNbt();
        return (tag != null) ? load(tag) : new AutoTransmissionClutchSettings();
    }

    public enum LimitMode {
        SU("create_stats.limit_mode.su"),
        PERCENTAGE("create_stats.limit_mode.percentage");

        private final String translationKey;
        LimitMode(String translationKey) { this.translationKey = translationKey; }
        public String getDisplayName() { return net.minecraft.network.chat.Component.translatable(this.translationKey).getString(); }
        public LimitMode next() {
            final LimitMode[] v = values();
            return v[(this.ordinal() + 1) % v.length];
        }
    }
}
