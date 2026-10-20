package com.createstats.alarm.annunciator;

public enum RuleMeasure {
    LEVEL("level"),

    RATE("rate");

    private final String serializedName;

    RuleMeasure(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public String unitSuffix() {
        return this == RATE ? "/s" : "";
    }

    public RuleMeasure next() {
        return this == LEVEL ? RATE : LEVEL;
    }

    public static RuleMeasure byName(String name) {
        for (RuleMeasure measure : values()) {
            if (measure.serializedName.equalsIgnoreCase(name) || measure.name().equalsIgnoreCase(name)) {
                return measure;
            }
        }
        return LEVEL;
    }
}
