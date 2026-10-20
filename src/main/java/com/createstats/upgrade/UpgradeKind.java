package com.createstats.upgrade;

import java.util.Locale;

// slots buy variety, depth buys bulk
public enum UpgradeKind {
    SLOTS("slot"),

    STACK("stack");

    private final String serializedName;

    UpgradeKind(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public String translationKey() {
        return "create_stats.upgrade.kind." + this.serializedName;
    }

    public static UpgradeKind byName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        final String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (final UpgradeKind kind : values()) {
            if (kind.name().equals(normalized) || kind.serializedName.equalsIgnoreCase(name)) {
                return kind;
            }
        }
        return null;
    }
}
