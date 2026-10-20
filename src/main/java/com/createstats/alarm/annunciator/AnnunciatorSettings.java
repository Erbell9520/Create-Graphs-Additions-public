package com.createstats.alarm.annunciator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class AnnunciatorSettings {
    public static final int DEFAULT_SCAN_RADIUS = 16;

    public static final int MIN_SCAN_RADIUS = 4;

    public static final int MAX_SCAN_RADIUS = 32;

    public static final int MAX_AERIAL_RADIUS = 64;

    private static final int LEGACY_SCAN_RADIUS_CEILING = MIN_SCAN_RADIUS;

    public enum Horn {
        ALARM_RING("Alarm Ring"),
        BELL("Bell"),
        NOTE_PLING("Note Pling"),
        ANVIL("Anvil"),
        RAID_HORN("Raid Horn"),
        WITHER("Wither"),
        SILENT("Silent");

        private final String displayName;

        Horn(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public net.minecraft.sounds.SoundEvent soundEvent() {
            return switch (this) {
                case ALARM_RING, SILENT -> com.createstats.registry.ModSounds.ALARM_RING.get();
                case BELL -> net.minecraft.sounds.SoundEvents.BELL_BLOCK;
                case NOTE_PLING -> net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value();
                case ANVIL -> net.minecraft.sounds.SoundEvents.ANVIL_LAND;
                case RAID_HORN -> net.minecraft.sounds.SoundEvents.RAID_HORN.value();
                case WITHER -> net.minecraft.sounds.SoundEvents.WITHER_SPAWN;
            };
        }

        public boolean isSilent() {
            return this == SILENT;
        }

        public Horn next() {
            final Horn[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum Particle {
        NONE("None"),
        SMOKE("Smoke"),
        FLAME("Flame"),
        SOUL_FIRE("Soul Fire"),
        REDSTONE("Redstone"),
        SPARK("Electric Spark");

        private final String displayName;

        Particle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public Particle next() {
            final Particle[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum Audience {
        ALL_PLAYERS("All Players"),
        NEARBY_ONLY("Nearby Only"),
        OWNER_ONLY("Owner Only"),
        NOBODY("Nobody");

        private final String displayName;

        Audience(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public Audience next() {
            final Audience[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    private Horn horn = Horn.ALARM_RING;
    private Particle particle = Particle.SMOKE;
    private float volume = 0.7f;
    private float pitch = 1.0f;
    private int hornIntervalTicks = 40;
    private int hearingRange = 32;
    private int particleCount = 3;
    private Audience hornAudience = Audience.ALL_PLAYERS;
    private boolean hudAlert = true;
    private Audience hudAudience = Audience.NEARBY_ONLY;
    private int hudRange = 48;
    private String message = "";
    private boolean redstoneEnabled = true;
    private int scanRadius = DEFAULT_SCAN_RADIUS;

    public Horn getHorn() {
        return this.horn;
    }

    public void setHorn(Horn horn) {
        this.horn = horn == null ? Horn.ALARM_RING : horn;
    }

    public Particle getParticle() {
        return this.particle;
    }

    public void setParticle(Particle particle) {
        this.particle = particle == null ? Particle.NONE : particle;
    }

    public float getVolume() {
        return this.volume;
    }

    public void setVolume(float volume) {
        this.volume = clamp(volume, 0.0f, 1.0f);
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = clamp(pitch, 0.5f, 2.0f);
    }

    public int getHornIntervalTicks() {
        return this.hornIntervalTicks;
    }

    public void setHornIntervalTicks(int ticks) {
        this.hornIntervalTicks = (int) clamp(ticks, 20, 600);
    }

    public float getHornIntervalSeconds() {
        return this.hornIntervalTicks / 20.0f;
    }

    public int getHearingRange() {
        return this.hearingRange;
    }

    public void setHearingRange(int range) {
        this.hearingRange = (int) clamp(range, 8, 128);
    }

    public int getParticleCount() {
        return this.particleCount;
    }

    public void setParticleCount(int count) {
        this.particleCount = (int) clamp(count, 0, 20);
    }

    public Audience getHornAudience() {
        return this.hornAudience;
    }

    public void setHornAudience(Audience audience) {
        this.hornAudience = audience == null ? Audience.ALL_PLAYERS : audience;
    }

    public boolean isHudAlert() {
        return this.hudAlert;
    }

    public void setHudAlert(boolean hudAlert) {
        this.hudAlert = hudAlert;
    }

    public Audience getHudAudience() {
        return this.hudAudience;
    }

    public void setHudAudience(Audience audience) {
        this.hudAudience = audience == null ? Audience.NEARBY_ONLY : audience;
    }

    public int getHudRange() {
        return this.hudRange;
    }

    public void setHudRange(int range) {
        this.hudRange = (int) clamp(range, 8, 256);
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }

    public boolean isRedstoneEnabled() {
        return this.redstoneEnabled;
    }

    public void setRedstoneEnabled(boolean enabled) {
        this.redstoneEnabled = enabled;
    }

    public int getScanRadius() {
        return this.scanRadius;
    }

    public void setScanRadius(int radius) {
        this.scanRadius = (int) clamp(radius, MIN_SCAN_RADIUS, MAX_SCAN_RADIUS);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public AnnunciatorSettings copy() {
        return load(this.save());
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Horn", this.horn.name());
        tag.putString("Particle", this.particle.name());
        tag.putFloat("Volume", this.volume);
        tag.putFloat("Pitch", this.pitch);
        tag.putInt("HornInterval", this.hornIntervalTicks);
        tag.putInt("HearRange", this.hearingRange);
        tag.putInt("ParticleCount", this.particleCount);
        tag.putString("HornAudience", this.hornAudience.name());
        tag.putBoolean("Hud", this.hudAlert);
        tag.putString("HudAudience", this.hudAudience.name());
        tag.putInt("HudRange", this.hudRange);
        tag.putString("Message", this.message);
        tag.putBoolean("Redstone", this.redstoneEnabled);
        tag.putInt("ScanRadius", this.scanRadius);
        return tag;
    }

    public static AnnunciatorSettings load(CompoundTag tag) {
        final AnnunciatorSettings settings = new AnnunciatorSettings();
        settings.horn = enumOr(Horn.class, tag.getString("Horn"), Horn.ALARM_RING);
        settings.particle = enumOr(Particle.class, tag.getString("Particle"), Particle.SMOKE);
        if (tag.contains("Volume")) {
            settings.setVolume(tag.getFloat("Volume"));
        }
        if (tag.contains("Pitch")) {
            settings.setPitch(tag.getFloat("Pitch"));
        }
        if (tag.contains("HornInterval")) {
            settings.setHornIntervalTicks(tag.getInt("HornInterval"));
        }
        if (tag.contains("HearRange")) {
            settings.setHearingRange(tag.getInt("HearRange"));
        }
        if (tag.contains("ParticleCount")) {
            settings.setParticleCount(tag.getInt("ParticleCount"));
        }
        settings.hornAudience = enumOr(Audience.class, tag.getString("HornAudience"),
                Audience.ALL_PLAYERS);
        if (tag.contains("Hud")) {
            settings.hudAlert = tag.getBoolean("Hud");
        }
        settings.hudAudience = enumOr(Audience.class, tag.getString("HudAudience"),
                Audience.NEARBY_ONLY);
        if (tag.contains("HudRange")) {
            settings.setHudRange(tag.getInt("HudRange"));
        }
        settings.message = tag.getString("Message");
        if (tag.contains("Redstone")) {
            settings.redstoneEnabled = tag.getBoolean("Redstone");
        }
        if (tag.contains("ScanRadius")) {
            final int stored = tag.getInt("ScanRadius");
            settings.setScanRadius(stored < LEGACY_SCAN_RADIUS_CEILING ? DEFAULT_SCAN_RADIUS : stored);
        }
        return settings;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        for (E value : type.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return fallback;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.save());
    }

    public static AnnunciatorSettings decode(FriendlyByteBuf buf) {
        final CompoundTag tag = buf.readNbt();
        return tag == null ? new AnnunciatorSettings() : load(tag);
    }
}
