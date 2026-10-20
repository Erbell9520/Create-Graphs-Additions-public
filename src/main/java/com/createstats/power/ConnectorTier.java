package com.createstats.power;

public enum ConnectorTier {
    POWER("Power Connector", 2, 4),

    ADVANCED("Advanced Power Connector", 4, 4),

    INDUSTRIAL("Industrial Power Connector", 8, 6);

    private final String displayName;
    private final int multiplier;
    private final int maxLinks;

    ConnectorTier(String displayName, int multiplier, int maxLinks) {
        this.displayName = displayName;
        this.multiplier = multiplier;
        this.maxLinks = maxLinks;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int multiplier() {
        return multiplier;
    }

    public int maxLinks() {
        return maxLinks;
    }

    public static ConnectorTier byOrdinal(int ordinal) {
        final ConnectorTier[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : POWER;
    }
}
