package com.createstats.security;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum SecurityMode {
    HOME("home"),

    AWAY("away"),

    CUSTOM("custom"),

    AWAY_PLUS("away_plus");

    private static final SecurityMode[] ORDER = values();

    private final String key;

    SecurityMode(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public boolean armsIntrusion(boolean sensorFlag) {
        return switch (this) {
            case HOME -> false;
            case AWAY, AWAY_PLUS -> true;
            case CUSTOM -> sensorFlag;
        };
    }

    public boolean usesPerSensorFlag() {
        return this == CUSTOM;
    }

    public boolean isAway() {
        return this == AWAY || this == AWAY_PLUS;
    }

    public boolean sealsHouse() {
        return this == AWAY_PLUS;
    }

    public Component label() {
        return Component.translatable("securitymode.create_stats." + this.key);
    }

    public Component blurb() {
        return Component.translatable("securitymode.create_stats." + this.key + ".blurb");
    }

    public String plain() {
        return this.key.toUpperCase(Locale.ROOT);
    }

    public SecurityMode next() {
        return ORDER[(ordinal() + 1) % ORDER.length];
    }

    public static SecurityMode byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < ORDER.length ? ORDER[ordinal] : HOME;
    }

    @org.jetbrains.annotations.Nullable
    public static SecurityMode byKey(String key) {
        for (SecurityMode m : ORDER) {
            if (m.key.equals(key)) {
                return m;
            }
        }
        return null;
    }

    public static SecurityMode[] all() {
        return ORDER;
    }
}
