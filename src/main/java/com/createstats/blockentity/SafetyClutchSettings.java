package com.createstats.blockentity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class SafetyClutchSettings {
    private float threshold;
    private float hysteresis;
    private ClutchMode mode;
    private boolean enabled;

    public SafetyClutchSettings() {
        this.threshold = 80.0f;
        this.hysteresis = 5.0f;
        this.mode = ClutchMode.LATCH;
        this.enabled = true;
    }

    public float getThreshold() {
        return this.threshold;
    }

    public void setThreshold(float t) {
        // NaN goes straight through max/min and never compares true. clutch would never trip again

        if (!Float.isFinite(t)) return;
        this.threshold = Math.max(5.0f, Math.min(100.0f, t));
    }

    public float getHysteresis() {
        return this.hysteresis;
    }

    public void setHysteresis(float h) {
        if (!Float.isFinite(h)) return;
        this.hysteresis = Math.max(0.0f, Math.min(20.0f, h));
    }

    public float getRearmBelow() {
        return this.threshold - this.hysteresis;
    }

    public ClutchMode getMode() {
        return this.mode;
    }

    public void setMode(ClutchMode m) {
        this.mode = m;
    }

    public void cycleMode() {
        this.mode = this.mode.next();
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean e) {
        this.enabled = e;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putFloat("Threshold", this.threshold);
        tag.putFloat("Hysteresis", this.hysteresis);
        tag.putString("Mode", this.mode.name());
        tag.putBoolean("Enabled", this.enabled);
        return tag;
    }

    public static SafetyClutchSettings load(CompoundTag tag) {
        final SafetyClutchSettings s = new SafetyClutchSettings();

        if (tag.contains("Threshold")) s.setThreshold(tag.getFloat("Threshold"));
        if (tag.contains("Hysteresis")) s.setHysteresis(tag.getFloat("Hysteresis"));
        try {
            s.mode = ClutchMode.valueOf(tag.getString("Mode"));
        } catch (Exception ignored) {
        }
        if (tag.contains("Enabled")) s.enabled = tag.getBoolean("Enabled");
        return s;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.save());
    }

    public static SafetyClutchSettings decode(FriendlyByteBuf buf) {
        final CompoundTag tag = buf.readNbt();
        return (tag != null) ? load(tag) : new SafetyClutchSettings();
    }

    public enum ClutchMode {
        AUTO_RESET("create_stats.safety_clutch.mode.auto_reset"),
        LATCH("create_stats.safety_clutch.mode.latch");

        private final String translationKey;

        ClutchMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getDisplayName() {
            return net.minecraft.network.chat.Component.translatable(this.translationKey).getString();
        }

        public ClutchMode next() {
            final ClutchMode[] v = values();
            return v[(this.ordinal() + 1) % v.length];
        }
    }
}
