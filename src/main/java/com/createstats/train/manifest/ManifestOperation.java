package com.createstats.train.manifest;

public enum ManifestOperation {
    LOAD("load", "Load"),
    UNLOAD("unload", "Unload");

    private final String serializedName;
    private final String displayName;

    ManifestOperation(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public ManifestOperation cycle() {
        return this == LOAD ? UNLOAD : LOAD;
    }

    public static ManifestOperation byName(String serializedName) {
        for (ManifestOperation operation : values()) {
            if (operation.serializedName.equals(serializedName)) {
                return operation;
            }
        }
        return LOAD;
    }
}
