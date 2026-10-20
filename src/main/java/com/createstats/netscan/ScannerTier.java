package com.createstats.netscan;

import java.util.Locale;

public enum ScannerTier {
    FIELD("field", 64, 4, 1, 2.0, 1),

    ADVANCED("advanced", 128, 8, 2, 4.0, 2),

    INDUSTRIAL("industrial", 512, 16, 3, 8.0, 4);

    private final String serializedName;
    private final int elementBudget;
    private final int elementsPerTick;
    private final int detailLevel;
    private final double stressPerRpm;
    private final int powerMultiplier;

    ScannerTier(String serializedName, int elementBudget, int elementsPerTick, int detailLevel,
                double stressPerRpm, int powerMultiplier) {
        this.serializedName = serializedName;
        this.elementBudget = elementBudget;
        this.elementsPerTick = elementsPerTick;
        this.detailLevel = detailLevel;
        this.stressPerRpm = stressPerRpm;
        this.powerMultiplier = powerMultiplier;
    }

    public int elementBudget() {
        try {
            return switch (this) {
                case FIELD -> com.createstats.ModConfiguration.COMMON.scannerBudgetField.get();
                case ADVANCED -> com.createstats.ModConfiguration.COMMON.scannerBudgetAdvanced.get();
                case INDUSTRIAL -> com.createstats.ModConfiguration.COMMON.scannerBudgetIndustrial.get();
            };
        } catch (RuntimeException ignored) {
            return this.elementBudget;
        }
    }

    public int elementsPerTick() {
        try {
            return switch (this) {
                case FIELD -> com.createstats.ModConfiguration.COMMON.scannerRateField.get();
                case ADVANCED -> com.createstats.ModConfiguration.COMMON.scannerRateAdvanced.get();
                case INDUSTRIAL -> com.createstats.ModConfiguration.COMMON.scannerRateIndustrial.get();
            };
        } catch (RuntimeException ignored) {
            return this.elementsPerTick;
        }
    }

    public int detailLevel() {
        return this.detailLevel;
    }

    public double stressPerRpm() {
        try {
            return switch (this) {
                case FIELD -> com.createstats.ModConfiguration.COMMON.scannerStressField.get();
                case ADVANCED -> com.createstats.ModConfiguration.COMMON.scannerStressAdvanced.get();
                case INDUSTRIAL -> com.createstats.ModConfiguration.COMMON.scannerStressIndustrial.get();
            };
        } catch (RuntimeException ignored) {
            return this.stressPerRpm;
        }
    }

    public String feProfileKey() {
        return switch (this) {
            case FIELD -> "topologyScannerField";
            case ADVANCED -> "topologyScannerAdvanced";
            case INDUSTRIAL -> "topologyScannerIndustrial";
        };
    }

    public int powerMultiplier() {
        return this.powerMultiplier;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public String translationKey() {
        return "create_stats.netscan.tier." + this.serializedName;
    }

    public static ScannerTier byOrdinal(int ordinal) {
        final ScannerTier[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FIELD;
    }

    public static ScannerTier byName(String name) {
        if (name == null || name.isBlank()) {
            return FIELD;
        }
        final String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (final ScannerTier tier : values()) {
            if (tier.name().equals(normalized) || tier.serializedName.equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return FIELD;
    }
}
