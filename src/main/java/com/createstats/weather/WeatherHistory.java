package com.createstats.weather;

// pure ring, the wrap seam is tested
public final class WeatherHistory {
    public static final int SIZE = 120;
    public static final int SAMPLE_TICKS = 200;

    public static final int SAMPLES_PER_HOUR = 1000 / SAMPLE_TICKS;

    private static final int TEMP_BITS = 12;
    private static final int TEMP_OFFSET = 2048;
    private static final int TEMP_MASK = (1 << TEMP_BITS) - 1;
    private static final int PRESSURE_SHIFT = 12;
    private static final int PRESSURE_OFFSET = 900;
    private static final int PRESSURE_MASK = 0xFF;
    private static final int HUMIDITY_SHIFT = 20;
    private static final int HUMIDITY_MASK = 0x7F;
    private static final int CONDITION_SHIFT = 27;
    private static final int CONDITION_MASK = 0x07;

    private static final Condition[] CONDITIONS = Condition.values();

    private final int[] packed = new int[SIZE];
    private int head;
    private int count;

    public void record(int temperatureTenths, int humidityPercent, int pressureHpa,
                       Condition condition) {
        final int t = clamp(temperatureTenths + TEMP_OFFSET, 0, TEMP_MASK);
        final int p = clamp(pressureHpa - PRESSURE_OFFSET, 0, PRESSURE_MASK);
        final int h = clamp(humidityPercent, 0, HUMIDITY_MASK);
        final int c = condition == null ? 0 : condition.ordinal() & CONDITION_MASK;
        packed[head] = t | (p << PRESSURE_SHIFT) | (h << HUMIDITY_SHIFT) | (c << CONDITION_SHIFT);
        head = (head + 1) % SIZE;
        if (count < SIZE) {
            count++;
        }
    }

    public int size() {
        return count;
    }

    public int[] temperatureTenths() {
        final int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = (packed[index(i)] & TEMP_MASK) - TEMP_OFFSET;
        }
        return out;
    }

    public int[] pressures() {
        final int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = ((packed[index(i)] >>> PRESSURE_SHIFT) & PRESSURE_MASK) + PRESSURE_OFFSET;
        }
        return out;
    }

    public int[] humidities() {
        final int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = (packed[index(i)] >>> HUMIDITY_SHIFT) & HUMIDITY_MASK;
        }
        return out;
    }

    public Condition[] conditions() {
        final Condition[] out = new Condition[count];
        for (int i = 0; i < count; i++) {
            final int ordinal = (packed[index(i)] >>> CONDITION_SHIFT) & CONDITION_MASK;
            out[i] = ordinal < CONDITIONS.length ? CONDITIONS[ordinal] : Condition.CLEAR;
        }
        return out;
    }

    public float pressureOneHourAgo(float fallback) {
        if (count <= SAMPLES_PER_HOUR) {
            return fallback;
        }
        final int[] all = pressures();
        return all[all.length - 1 - SAMPLES_PER_HOUR];
    }

    private int index(int i) {
        return count < SIZE ? i : (head + i) % SIZE;
    }

    public int[] save() {
        final int[] out = new int[SIZE + 2];
        out[0] = head;
        out[1] = count;
        System.arraycopy(packed, 0, out, 2, SIZE);
        return out;
    }

    public static WeatherHistory load(int[] in) {
        final WeatherHistory h = new WeatherHistory();
        if (in == null || in.length < SIZE + 2) {
            return h;
        }
        h.head = Math.floorMod(in[0], SIZE);
        h.count = clamp(in[1], 0, SIZE);
        System.arraycopy(in, 2, h.packed, 0, SIZE);
        return h;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
