package com.createstats.security;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum SensorKind {
    MOTION("motion", Family.INTRUSION, 6),

    TAMPER("tamper", Family.INTRUSION, 8),

    FIRE("fire", Family.HAZARD, 5),

    WATER("water", Family.HAZARD, 5);

    public enum Family {
        INTRUSION,

        HAZARD;

        public boolean armable() {
            return this == INTRUSION;
        }

        public boolean locksDown() {
            return this == INTRUSION;
        }
    }

    private static final SensorKind[] ORDER = values();

    private final String key;
    private final Family family;
    private final int defaultRadius;

    SensorKind(String key, Family family, int defaultRadius) {
        this.key = key;
        this.family = family;
        this.defaultRadius = defaultRadius;
    }

    public String key() {
        return this.key;
    }

    public Family family() {
        return this.family;
    }

    public String blockId() {
        return this.key + "_sensor";
    }

    public int defaultRadius() {
        return this.defaultRadius;
    }

    public static int maxRadius() {
        return 16;
    }

    public static int minRadius() {
        return 2;
    }

    public static int clampRadius(int wanted) {
        return wanted < minRadius() ? minRadius() : Math.min(wanted, maxRadius());
    }

    public boolean isHazard() {
        return this.family == Family.HAZARD;
    }

    public Component label() {
        return Component.translatable("sensor.create_stats." + this.key);
    }

    public String plain() {
        return this.key.toUpperCase(Locale.ROOT);
    }

    public static SensorKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < ORDER.length ? ORDER[ordinal] : MOTION;
    }

    public static SensorKind[] all() {
        return ORDER;
    }
}
