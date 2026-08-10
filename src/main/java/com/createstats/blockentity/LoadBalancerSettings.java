package com.createstats.blockentity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class LoadBalancerSettings {

    private static final float SU_MIN = 1.0f;
    private static final float SU_MAX = 65536.0f;
    private static final float PCT_MIN = 1.0f;
    private static final float PCT_MAX = 100.0f;

    private LimitMode limitMode;
    private float lowerBound;
    private float upperBound;
    private float minimumRPM;
    private float maximumRPM;
    private float leftSharePct;
    private SharingMode sharingMode;
    private boolean enabled;

    public LoadBalancerSettings() {
        this.limitMode = LimitMode.PERCENTAGE;
        this.lowerBound = 70.0f;
        this.upperBound = 80.0f;
        this.minimumRPM = 16.0f;
        this.maximumRPM = 256.0f;
        this.leftSharePct = 50.0f;
        this.sharingMode = SharingMode.STRICT;
        this.enabled = true;
    }

    public LimitMode getLimitMode() {
        return this.limitMode;
    }

    public void setLimitMode(LimitMode limitMode) {
        this.limitMode = limitMode;
    }

    public void cycleLimitMode() {
        this.limitMode = this.limitMode.next();
        if (this.limitMode == LimitMode.SU) {
            this.lowerBound = 128.0f;
            this.upperBound = 256.0f;
        } else {
            this.lowerBound = 70.0f;
            this.upperBound = 80.0f;
        }
    }

    public float getLowerBound() {
        return this.lowerBound;
    }

    public void setLowerBound(float value) {
        float max = getMaxForMode();
        float min = getMinForMode();
        this.lowerBound = Math.max(min, Math.min(max, value));
        if (this.lowerBound > this.upperBound) {
            this.upperBound = this.lowerBound;
        }
    }

    public float getUpperBound() {
        return this.upperBound;
    }

    public void setUpperBound(float value) {
        float max = getMaxForMode();
        float min = getMinForMode();
        this.upperBound = Math.max(min, Math.min(max, value));
        if (this.upperBound < this.lowerBound) {
            this.lowerBound = this.upperBound;
        }
    }

    public float getMinimumRPM() {
        return this.minimumRPM;
    }

    public void setMinimumRPM(float value) {
        this.minimumRPM = Math.max(1.0f, Math.min(256.0f, value));
        if (this.minimumRPM > this.maximumRPM) {
            this.maximumRPM = this.minimumRPM;
        }
    }

    public float getMaximumRPM() {
        return this.maximumRPM;
    }

    public void setMaximumRPM(float value) {
        this.maximumRPM = Math.max(1.0f, Math.min(256.0f, value));
        if (this.maximumRPM < this.minimumRPM) {
            this.minimumRPM = this.maximumRPM;
        }
    }

    public float getLeftSharePct() {
        return this.leftSharePct;
    }

    public void setLeftSharePct(float value) {
        this.leftSharePct = Math.max(0.0f, Math.min(100.0f, value));
    }

    public float getRightSharePct() {
        return 100.0f - this.leftSharePct;
    }

    public SharingMode getSharingMode() {
        return this.sharingMode;
    }

    public void setSharingMode(SharingMode sharingMode) {
        this.sharingMode = sharingMode;
    }

    public void cycleSharingMode() {
        this.sharingMode = this.sharingMode.next();
    }

    public boolean isBorrowingEnabled() {
        return this.sharingMode == SharingMode.ELASTIC;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private float getMaxForMode() {
        return this.limitMode == LimitMode.PERCENTAGE ? PCT_MAX : SU_MAX;
    }

    private float getMinForMode() {
        return this.limitMode == LimitMode.PERCENTAGE ? PCT_MIN : SU_MIN;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("LimitMode", this.limitMode.name());
        tag.putFloat("LowerBound", this.lowerBound);
        tag.putFloat("UpperBound", this.upperBound);
        tag.putFloat("MinimumRPM", this.minimumRPM);
        tag.putFloat("MaximumRPM", this.maximumRPM);
        tag.putFloat("LeftSharePct", this.leftSharePct);
        tag.putString("SharingMode", this.sharingMode.name());
        tag.putBoolean("Enabled", this.enabled);
        return tag;
    }

    public static LoadBalancerSettings load(CompoundTag tag) {
        LoadBalancerSettings settings = new LoadBalancerSettings();
        try {
            settings.limitMode = LimitMode.valueOf(tag.getString("LimitMode"));
        } catch (Exception ignored) {
        }
        if (tag.contains("LowerBound")) {
            settings.setLowerBound(tag.getFloat("LowerBound"));
        }
        if (tag.contains("UpperBound")) {
            settings.setUpperBound(tag.getFloat("UpperBound"));
        }
        if (tag.contains("MinimumRPM")) {
            settings.setMinimumRPM(tag.getFloat("MinimumRPM"));
        }
        if (tag.contains("MaximumRPM")) {
            settings.setMaximumRPM(tag.getFloat("MaximumRPM"));
        }
        if (tag.contains("LeftSharePct")) {
            settings.setLeftSharePct(tag.getFloat("LeftSharePct"));
        }
        try {
            settings.sharingMode = SharingMode.valueOf(tag.getString("SharingMode"));
        } catch (Exception ignored) {
        }
        if (tag.contains("Enabled")) {
            settings.enabled = tag.getBoolean("Enabled");
        }
        return settings;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.save());
    }

    public static LoadBalancerSettings decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return tag != null ? load(tag) : new LoadBalancerSettings();
    }

    public enum LimitMode {
        SU("create_stats.limit_mode.su"),
        PERCENTAGE("create_stats.limit_mode.percentage");

        private final String translationKey;

        LimitMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getDisplayName() {
            return net.minecraft.network.chat.Component.translatable(this.translationKey).getString();
        }

        public LimitMode next() {
            LimitMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum SharingMode {
        STRICT("create_stats.load_balancer.sharing.strict"),
        ELASTIC("create_stats.load_balancer.sharing.elastic");

        private final String translationKey;

        SharingMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getDisplayName() {
            return net.minecraft.network.chat.Component.translatable(this.translationKey).getString();
        }

        public SharingMode next() {
            SharingMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }
}
