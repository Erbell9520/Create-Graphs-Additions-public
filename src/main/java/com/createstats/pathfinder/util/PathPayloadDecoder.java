package com.createstats.pathfinder.util;

import com.createstats.pathfinder.model.Passability;

public final class PathPayloadDecoder {
    private PathPayloadDecoder() {
    }

    public static Passability passability(byte packedCell) {
        int ordinal = (Byte.toUnsignedInt(packedCell) >> 6) & 0x3;
        Passability[] values = Passability.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Passability.UNKNOWN;
    }

    public static int hazards(byte packedCell) {
        return Byte.toUnsignedInt(packedCell) & 0x3F;
    }

    public static int supportY(byte[] heightPayload, int index, int originY) {
        if (heightPayload == null || index < 0 || index >= heightPayload.length) return originY;
        byte packed = heightPayload[index];
        if (packed == Byte.MIN_VALUE) return originY;
        return originY + packed;
    }
}
