package com.createstats.antenna;

import java.util.Locale;

public enum AntennaTier {
    NONE(1, "none"),
    SMALL(4, "small"),
    ADVANCED(12, "advanced"),
    TRT(32, "trt");

    private final int rangeMultiplier;
    private final String serializedName;

    AntennaTier(int rangeMultiplier, String serializedName) {
        this.rangeMultiplier = rangeMultiplier;
        this.serializedName = serializedName;
    }

    public int getRangeMultiplier() {
        try {
            return switch (this) {
                case SMALL -> com.createstats.ModConfiguration.COMMON.antennaRangeMultiplierSmall.get();
                case ADVANCED -> com.createstats.ModConfiguration.COMMON.antennaRangeMultiplierAdvanced.get();
                case TRT -> com.createstats.ModConfiguration.COMMON.antennaRangeMultiplierTrt.get();
                default -> this.rangeMultiplier;
            };
        } catch (RuntimeException ignored) {
            return this.rangeMultiplier;
        }
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public boolean isAttached() {
        return this != NONE;
    }

    public boolean isCrossDimensional() {
        return this == TRT;
    }

    public static AntennaTier byName(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (AntennaTier tier : values()) {
            if (tier.name().equals(normalized) || tier.serializedName.equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return NONE;
    }
}
