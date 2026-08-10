package com.createstats.train.manifest;

public enum DeliveryOrderHistoryType {

    COMPLETED("completed", "Completed"),
    MANUAL_COMPLETE("manual_complete", "Marked Complete"),
    AUTO_RESET("auto_reset", "Cycle Reset"),
    MANUAL_RESET("manual_reset", "Manual Reset"),
    HIDDEN("hidden", "Hidden"),
    SHOWN("shown", "Shown");

    private final String serializedName;
    private final String displayName;

    DeliveryOrderHistoryType(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public static DeliveryOrderHistoryType byName(String serializedName) {
        for (DeliveryOrderHistoryType type : values()) {
            if (type.serializedName.equals(serializedName)) {
                return type;
            }
        }
        return COMPLETED;
    }
}
