package com.createstats.dynlight;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;

// one hook into the light engine and sodium/iris just work, nothing downstream is touched
public final class DynamicLightField {
    private static volatile Object boundEngine;
    private static volatile Long2ByteMap cells = Long2ByteMaps.EMPTY_MAP;

    private static volatile boolean asked;

    private DynamicLightField() {
    }

    public static int emission(Object engine, long packedPos, int original) {
        if (engine != boundEngine || engine == null) {
            return original;
        }
        if (!asked) {
            asked = true;
        }
        final int dynamic = cells.get(packedPos);
        return dynamic > original ? dynamic : original;
    }

    public static void bind(Object engine) {
        cells = Long2ByteMaps.EMPTY_MAP;
        asked = false;
        boundEngine = engine;
    }

    public static void publish(Long2ByteMap next) {
        cells = next;
    }

    public static Object boundEngine() {
        return boundEngine;
    }

    public static boolean asked() {
        return asked;
    }

    public static Long2ByteMap cells() {
        return cells;
    }
}
