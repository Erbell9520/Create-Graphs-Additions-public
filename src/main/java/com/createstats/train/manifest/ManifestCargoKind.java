package com.createstats.train.manifest;

public enum ManifestCargoKind {

    ITEM("item", "Item", 1, "items"),
    FLUID("fluid", "Fluid", 1000, "mB");

    private final String serializedName;
    private final String displayName;
    private final int defaultAmount;
    private final String unitLabel;

    ManifestCargoKind(String serializedName, String displayName, int defaultAmount, String unitLabel) {
        this.serializedName = serializedName;
        this.displayName = displayName;
        this.defaultAmount = defaultAmount;
        this.unitLabel = unitLabel;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getDefaultAmount() {
        return this.defaultAmount;
    }

    public String getUnitLabel() {
        return this.unitLabel;
    }

    public static ManifestCargoKind byName(String serializedName) {
        for (ManifestCargoKind kind : values()) {
            if (kind.serializedName.equals(serializedName)) {
                return kind;
            }
        }
        return ITEM;
    }
}
