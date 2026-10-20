package com.createstats.pa;

import com.createstats.pa.PaSubscription.Verbosity;
import net.minecraft.nbt.CompoundTag;

// scan range and hearing range stay separate, speakers extend only hearing
public class PaSettings {
    public enum Audience {
        EVERYONE("everyone", "Everyone"),
        NEARBY("nearby", "Nearby"),
        OWNER("owner", "Owner only");

        private final String serializedName;
        private final String displayName;

        Audience(String serializedName, String displayName) {
            this.serializedName = serializedName;
            this.displayName = displayName;
        }

        public String getSerializedName() {
            return this.serializedName;
        }

        public String displayName() {
            return this.displayName;
        }

        public static Audience byName(String name) {
            for (Audience value : values()) {
                if (value.serializedName.equals(name)) {
                    return value;
                }
            }
            return NEARBY;
        }
    }

    public static final int MIN_RANGE = 4;
    public static final int MAX_RANGE = 32;

    private int scanRadius = 32;
    private int hearingRange = 32;
    private Audience audience = Audience.NEARBY;
    private float volume = 0.8f;
    private float pitch = 1.0f;
    private boolean bulletinEnabled = true;
    private int bulletinIntervalTicks = 6000;
    private int bulletinFacts = 3;
    private Verbosity bulletinVerbosity = Verbosity.TERSE;
    private boolean muted;

    public int scanRadius() {
        return this.scanRadius;
    }

    public void setScanRadius(int value) {
        this.scanRadius = Math.max(MIN_RANGE, Math.min(MAX_RANGE, value));
    }

    public int hearingRange() {
        return this.hearingRange;
    }

    public void setHearingRange(int value) {
        this.hearingRange = Math.max(MIN_RANGE, Math.min(MAX_RANGE, value));
    }

    public Audience audience() {
        return this.audience;
    }

    public void setAudience(Audience value) {
        this.audience = value == null ? Audience.NEARBY : value;
    }

    public float volume() {
        return this.volume;
    }

    public void setVolume(float value) {
        this.volume = Math.max(0.0f, Math.min(1.0f, value));
    }

    public float pitch() {
        return this.pitch;
    }

    public void setPitch(float value) {
        this.pitch = Math.max(0.5f, Math.min(1.5f, value));
    }

    public boolean bulletinEnabled() {
        return this.bulletinEnabled;
    }

    public void setBulletinEnabled(boolean value) {
        this.bulletinEnabled = value;
    }

    public int bulletinIntervalTicks() {
        return this.bulletinIntervalTicks;
    }

    public void setBulletinIntervalTicks(int value) {
        this.bulletinIntervalTicks = Math.max(1200, Math.min(72000, value));
    }

    public int bulletinFacts() {
        return this.bulletinFacts;
    }

    public void setBulletinFacts(int value) {
        this.bulletinFacts = Math.max(1, Math.min(6, value));
    }

    public Verbosity bulletinVerbosity() {
        return this.bulletinVerbosity;
    }

    public void setBulletinVerbosity(Verbosity value) {
        this.bulletinVerbosity = value == null ? Verbosity.TERSE : value;
    }

    public boolean muted() {
        return this.muted;
    }

    public void setMuted(boolean value) {
        this.muted = value;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Scan", this.scanRadius);
        tag.putInt("Hear", this.hearingRange);
        tag.putString("Aud", this.audience.getSerializedName());
        tag.putFloat("Vol", this.volume);
        tag.putFloat("Pit", this.pitch);
        tag.putBoolean("BulOn", this.bulletinEnabled);
        tag.putInt("BulEvery", this.bulletinIntervalTicks);
        tag.putInt("BulFacts", this.bulletinFacts);
        tag.putString("BulV", this.bulletinVerbosity.getSerializedName());
        tag.putBoolean("Mute", this.muted);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        this.setScanRadius(tag.getInt("Scan"));
        this.setHearingRange(tag.getInt("Hear"));
        this.audience = Audience.byName(tag.getString("Aud"));
        this.setVolume(tag.getFloat("Vol"));
        this.setPitch(tag.contains("Pit") ? tag.getFloat("Pit") : 1.0f);
        this.bulletinEnabled = !tag.contains("BulOn") || tag.getBoolean("BulOn");
        this.setBulletinIntervalTicks(tag.getInt("BulEvery"));
        this.setBulletinFacts(tag.getInt("BulFacts"));
        this.bulletinVerbosity = PaSubscription.verbosityByName(tag.getString("BulV"));
        this.muted = tag.getBoolean("Mute");
    }

    public PaSettings copy() {
        final PaSettings out = new PaSettings();
        out.load(this.save());
        return out;
    }
}
