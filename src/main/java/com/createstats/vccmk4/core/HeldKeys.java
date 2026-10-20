package com.createstats.vccmk4.core;

import java.util.Arrays;

public final class HeldKeys {
    public static final int MAX = 32;
    public static final HeldKeys NONE = new HeldKeys(new int[0]);

    private final int[] ids;

    private HeldKeys(int[] sortedDistinct) {
        this.ids = sortedDistinct;
    }

    public static HeldKeys of(int... raw) {
        if (raw == null || raw.length == 0) return NONE;
        int[] copy = new int[Math.min(raw.length, MAX)];
        int n = 0;
        for (int id : raw) {
            if (n >= copy.length) break;
            if (id != KeyIds.NONE && KeyIds.valid(id)) copy[n++] = id;
        }
        if (n == 0) return NONE;
        int[] sorted = Arrays.copyOf(copy, n);
        Arrays.sort(sorted);
        int distinct = 1;
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] != sorted[distinct - 1]) sorted[distinct++] = sorted[i];
        }
        return new HeldKeys(Arrays.copyOf(sorted, distinct));
    }

    public static HeldKeys union(HeldKeys a, HeldKeys b) {
        if (a == null || a.isEmpty()) return b == null ? NONE : b;
        if (b == null || b.isEmpty()) return a;
        int[] both = Arrays.copyOf(a.ids, a.ids.length + b.ids.length);
        System.arraycopy(b.ids, 0, both, a.ids.length, b.ids.length);
        return of(both);
    }

    public boolean has(int id) {
        return id != KeyIds.NONE && Arrays.binarySearch(ids, id) >= 0;
    }

    public boolean isEmpty() {
        return ids.length == 0;
    }

    public int[] toArray() {
        return ids.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HeldKeys h && Arrays.equals(ids, h.ids);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ids);
    }

    @Override
    public String toString() {
        return Arrays.toString(ids);
    }
}
