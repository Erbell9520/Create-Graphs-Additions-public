package com.createstats.train.manifest;

public enum DeliveryOrderResetMode {
    EVERY_ARRIVAL("every_arrival", "Every Arrival", "Arrival", false),
    ONCE("once", "Once", "Once", false),
    MINECRAFT_DAY("minecraft_day", "Every Day", "Day", false),
    EVERY_X_MINUTES("every_x_minutes", "Every X Minutes", "Minutes", true),
    EVERY_X_HOURS("every_x_hours", "Every X Hours", "Hours", true);

    private final String serializedName;
    private final String displayName;
    private final String shortName;
    private final boolean intervalBased;

    DeliveryOrderResetMode(String serializedName, String displayName, String shortName, boolean intervalBased) {
        this.serializedName = serializedName;
        this.displayName = displayName;
        this.shortName = shortName;
        this.intervalBased = intervalBased;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public boolean isIntervalBased() {
        return this.intervalBased;
    }

    public boolean isPersistent() {
        return this != EVERY_ARRIVAL;
    }

    public String getEditorLabel(int intervalValue) {
        if (!this.intervalBased) {
            return this.displayName;
        }
        return this.displayName + " (" + Math.max(1, intervalValue) + ")";
    }

    public String getBadgeLabel(int intervalValue) {
        return switch (this) {
            case EVERY_ARRIVAL -> "Arrival";
            case ONCE -> "Once";
            case MINECRAFT_DAY -> "Day";
            case EVERY_X_MINUTES -> Math.max(1, intervalValue) + "m";
            case EVERY_X_HOURS -> Math.max(1, intervalValue) + "h";
        };
    }

    public long getIntervalTicks(int intervalValue) {
        long clamped = Math.max(1, intervalValue);
        return switch (this) {
            case EVERY_X_MINUTES -> clamped * 1200L;
            case EVERY_X_HOURS -> clamped * 72000L;
            default -> -1L;
        };
    }

    public DeliveryOrderResetMode cycle() {
        DeliveryOrderResetMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static DeliveryOrderResetMode byName(String serializedName) {
        for (DeliveryOrderResetMode mode : values()) {
            if (mode.serializedName.equals(serializedName)) {
                return mode;
            }
        }
        return EVERY_ARRIVAL;
    }
}
