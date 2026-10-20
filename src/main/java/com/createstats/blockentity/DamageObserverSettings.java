package com.createstats.blockentity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class DamageObserverSettings {
    private String observerName;
    private WatchMode watchMode;
    private ObserverCategory category;
    private int areaRadius;
    private WeaponGovernorMapping weaponGovernorMapping;
    private boolean weaponYawInverted;
    private boolean weaponPitchInverted;

    public DamageObserverSettings() {
        this.observerName = "Hull Zone";
        this.watchMode = WatchMode.SINGLE_BLOCK;
        this.category = ObserverCategory.HULL;
        this.areaRadius = 1;
        this.weaponGovernorMapping = WeaponGovernorMapping.A_YAW_B_PITCH;
        this.weaponYawInverted = false;
        this.weaponPitchInverted = false;
    }

    public DamageObserverSettings copy() {
        DamageObserverSettings copy = new DamageObserverSettings();
        copy.observerName = this.observerName;
        copy.watchMode = this.watchMode;
        copy.category = this.category;
        copy.areaRadius = this.areaRadius;
        copy.weaponGovernorMapping = this.weaponGovernorMapping;
        copy.weaponYawInverted = this.weaponYawInverted;
        copy.weaponPitchInverted = this.weaponPitchInverted;
        return copy;
    }

    public String getObserverName() {
        return this.observerName;
    }

    public void setObserverName(String observerName) {
        String trimmed = observerName == null ? "" : observerName.trim();
        this.observerName = trimmed.isEmpty() ? "Hull Zone" : trimmed;
    }

    public WatchMode getWatchMode() {
        return this.watchMode;
    }

    public void cycleWatchMode() {
        this.watchMode = this.watchMode.next();
    }

    public ObserverCategory getCategory() {
        return this.category;
    }

    public void cycleCategory() {
        this.category = this.category.next();
    }

    public int getAreaRadius() {
        return this.areaRadius;
    }

    public void setAreaRadius(int areaRadius) {
        this.areaRadius = Math.max(1, Math.min(6, areaRadius));
    }

    public WeaponGovernorMapping getWeaponGovernorMapping() {
        return this.weaponGovernorMapping;
    }

    public void cycleWeaponGovernorMapping() {
        this.weaponGovernorMapping = this.weaponGovernorMapping.next();
    }

    public boolean isWeaponYawInverted() {
        return this.weaponYawInverted;
    }

    public void toggleWeaponYawInverted() {
        this.weaponYawInverted = !this.weaponYawInverted;
    }

    public boolean isWeaponPitchInverted() {
        return this.weaponPitchInverted;
    }

    public void toggleWeaponPitchInverted() {
        this.weaponPitchInverted = !this.weaponPitchInverted;
    }

    public int mapYawGovernorRpm(int logicalRpm) {
        int mapped = -logicalRpm;
        return this.weaponYawInverted ? -mapped : mapped;
    }

    public int mapPitchGovernorRpm(int logicalRpm) {
        int mapped = logicalRpm;
        return this.weaponPitchInverted ? -mapped : mapped;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ObserverName", this.observerName);
        tag.putString("WatchMode", this.watchMode.name());
        tag.putString("Category", this.category.name());
        tag.putInt("AreaRadius", this.areaRadius);
        tag.putString("WeaponGovernorMapping", this.weaponGovernorMapping.name());
        tag.putBoolean("WeaponYawInverted", this.weaponYawInverted);
        tag.putBoolean("WeaponPitchInverted", this.weaponPitchInverted);
        return tag;
    }

    public static DamageObserverSettings load(CompoundTag tag) {
        DamageObserverSettings settings = new DamageObserverSettings();
        settings.setObserverName(tag.getString("ObserverName"));
        try {
            settings.watchMode = WatchMode.valueOf(tag.getString("WatchMode"));
        } catch (Exception ignored) {
        }
        try {
            settings.category = ObserverCategory.valueOf(tag.getString("Category"));
        } catch (Exception ignored) {
        }
        if (tag.contains("AreaRadius")) {
            settings.setAreaRadius(tag.getInt("AreaRadius"));
        }
        try {
            settings.weaponGovernorMapping = WeaponGovernorMapping.valueOf(tag.getString("WeaponGovernorMapping"));
        } catch (Exception ignored) {
        }
        if (tag.contains("WeaponYawInverted")) {
            settings.weaponYawInverted = tag.getBoolean("WeaponYawInverted");
        }
        if (tag.contains("WeaponPitchInverted")) {
            settings.weaponPitchInverted = tag.getBoolean("WeaponPitchInverted");
        }
        return settings;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.observerName, 48);
        buf.writeEnum(this.watchMode);
        buf.writeEnum(this.category);
        buf.writeVarInt(this.areaRadius);
        buf.writeEnum(this.weaponGovernorMapping);
        buf.writeBoolean(this.weaponYawInverted);
        buf.writeBoolean(this.weaponPitchInverted);
    }

    public static DamageObserverSettings decode(FriendlyByteBuf buf) {
        DamageObserverSettings settings = new DamageObserverSettings();
        settings.setObserverName(buf.readUtf(48));
        settings.watchMode = buf.readEnum(WatchMode.class);
        settings.category = buf.readEnum(ObserverCategory.class);
        settings.setAreaRadius(buf.readVarInt());
        settings.weaponGovernorMapping = buf.readEnum(WeaponGovernorMapping.class);
        settings.weaponYawInverted = buf.readBoolean();
        settings.weaponPitchInverted = buf.readBoolean();
        return settings;
    }

    public enum WatchMode {
        SINGLE_BLOCK("create_stats.damage_observer.watch_mode.single_block"),
        LOCAL_AREA("create_stats.damage_observer.watch_mode.local_area"),
        BEHIND_BOX("create_stats.damage_observer.watch_mode.behind_box");

        private final String translationKey;

        WatchMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getDisplayName() {
            return net.minecraft.network.chat.Component.translatable(this.translationKey).getString();
        }

        public WatchMode next() {
            WatchMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum ObserverCategory {
        HULL("create_stats.damage_observer.category.hull"),
        LIFT("create_stats.damage_observer.category.lift"),
        POWER("create_stats.damage_observer.category.power"),
        WEAPONS("create_stats.damage_observer.category.weapons"),
        CONTROL("create_stats.damage_observer.category.control"),
        CARGO("create_stats.damage_observer.category.cargo");

        private final String translationKey;

        ObserverCategory(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getDisplayName() {
            return net.minecraft.network.chat.Component.translatable(this.translationKey).getString();
        }

        public ObserverCategory next() {
            ObserverCategory[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum WeaponGovernorMapping {
        A_YAW_B_PITCH("create_stats.damage_observer.gov_map.yaw_pitch"),
        A_PITCH_B_YAW("create_stats.damage_observer.gov_map.pitch_yaw");

        private final String translationKey;

        WeaponGovernorMapping(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getDisplayName() {
            return net.minecraft.network.chat.Component.translatable(this.translationKey).getString();
        }

        public WeaponGovernorMapping next() {
            WeaponGovernorMapping[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }
}
