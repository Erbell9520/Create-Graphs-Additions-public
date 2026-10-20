package com.createstats.netscan.scan;

public enum ScanNodeKind {
    SOURCE("source"),

    GEARING("gearing"),

    MACHINE("machine"),

    TRANSFER("transfer"),

    INSTRUMENT("instrument"),

    OTHER("other");

    private final String serializedName;

    ScanNodeKind(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public boolean isLoad() {
        return this == MACHINE;
    }

    public boolean isSource() {
        return this == SOURCE;
    }

    public static ScanNodeKind byOrdinal(int ordinal) {
        final ScanNodeKind[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OTHER;
    }
}
