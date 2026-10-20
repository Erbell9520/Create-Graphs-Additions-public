package com.createstats.alarm.annunciator;

public enum AnnunciatorVariant {
    KLAXON("klaxon", true),

    SENTRY("sentry", false);

    private final String serializedName;
    private final boolean wired;

    AnnunciatorVariant(String serializedName, boolean wired) {
        this.serializedName = serializedName;
        this.wired = wired;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public boolean isWired() {
        return this.wired;
    }

    public boolean canReadGrid() {
        return this.wired;
    }

    public boolean usesAntenna() {
        return !this.wired;
    }

    public static AnnunciatorVariant byName(String name) {
        for (AnnunciatorVariant variant : values()) {
            if (variant.serializedName.equalsIgnoreCase(name) || variant.name().equalsIgnoreCase(name)) {
                return variant;
            }
        }
        return SENTRY;
    }
}
