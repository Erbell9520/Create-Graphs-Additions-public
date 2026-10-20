package com.createstats.power;

public enum PduDeviceKind {
    MACHINE,

    STORAGE,

    BOARD;

    public boolean isLoad() {
        return this != STORAGE;
    }

    public boolean isBoard() {
        return this == BOARD;
    }

    public static PduDeviceKind byOrdinal(int ordinal) {
        final PduDeviceKind[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MACHINE;
    }
}
