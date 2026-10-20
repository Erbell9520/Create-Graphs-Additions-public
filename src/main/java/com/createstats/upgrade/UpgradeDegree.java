package com.createstats.upgrade;

import java.util.Locale;

public enum UpgradeDegree {
    FIRST("first", 1.5),

    SECOND("second", 2.0),

    THIRD("third", 4.0);

    private final String serializedName;
    private final double multiplier;

    UpgradeDegree(String serializedName, double multiplier) {
        this.serializedName = serializedName;
        this.multiplier = multiplier;
    }

    public double multiplier() {
        try {
            return switch (this) {
                case FIRST -> com.createstats.ModConfiguration.COMMON.upgradeMultiplierFirst.get();
                case SECOND -> com.createstats.ModConfiguration.COMMON.upgradeMultiplierSecond.get();
                case THIRD -> com.createstats.ModConfiguration.COMMON.upgradeMultiplierThird.get();
            };
        } catch (RuntimeException ignored) {
            return this.multiplier;
        }
    }

    public double defaultMultiplier() {
        return this.multiplier;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public String translationKey() {
        return "create_stats.upgrade.degree." + this.serializedName;
    }

    public static UpgradeDegree byOrdinal(int ordinal) {
        final UpgradeDegree[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : FIRST;
    }

    public static UpgradeDegree byName(String name) {
        if (name == null || name.isBlank()) {
            return FIRST;
        }
        final String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (final UpgradeDegree degree : values()) {
            if (degree.name().equals(normalized) || degree.serializedName.equalsIgnoreCase(name)) {
                return degree;
            }
        }
        return FIRST;
    }
}
