package com.createstats.repeller;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

// big and low to small and high, a ghast at 20.5, a bat at 64
public final class FrequencyTable {
    public static final int MIN_TENTHS = 200;

    public static final int MAX_TENTHS = 650;
    public static final int SLOTS = MAX_TENTHS - MIN_TENTHS + 1;

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final Map<String, Integer> tenths;

    private FrequencyTable(Map<String, Integer> tenths) {
        this.tenths = Collections.unmodifiableMap(tenths);
    }

    public static FrequencyTable build(Map<String, Double> placed, Collection<String> ids) {
        final BitSet taken = new BitSet(SLOTS);
        final Map<String, Integer> out = new HashMap<>();
        for (String id : new TreeSet<>(placed.keySet())) {
            final Double khz = placed.get(id);
            if (khz == null || khz.isNaN()) {
                continue;
            }
            out.put(id, claim(taken, clampTenths((int) Math.round(khz * 10.0))));
        }
        final TreeSet<String> rest = new TreeSet<>(ids);
        rest.removeAll(out.keySet());
        for (String id : rest) {
            out.put(id, claim(taken, homeSlot(id)));
        }
        return new FrequencyTable(out);
    }

    public int tenthsOf(String id) {
        final Integer t = tenths.get(id);
        return t != null ? t : homeSlot(id);
    }

    public boolean contains(String id) {
        return tenths.containsKey(id);
    }

    public int size() {
        return tenths.size();
    }

    public List<String> byFrequency() {
        final List<String> ids = new ArrayList<>(tenths.keySet());
        ids.sort((a, b) -> {
            final int c = Integer.compare(tenths.get(a), tenths.get(b));
            return c != 0 ? c : a.compareTo(b);
        });
        return ids;
    }

    public static String format(int tenthsOfKhz) {
        return (tenthsOfKhz / 10) + "." + Math.abs(tenthsOfKhz % 10);
    }

    public static int homeSlot(String id) {
        return MIN_TENTHS + (int) Long.remainderUnsigned(fnv1a64(id), SLOTS);
    }

    public static long fnv1a64(String s) {
        long h = FNV_OFFSET;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xFF);
            h *= FNV_PRIME;
        }
        return h;
    }

    static int clampTenths(int t) {
        return Math.max(MIN_TENTHS, Math.min(MAX_TENTHS, t));
    }

    private static int claim(BitSet taken, int home) {
        for (int d = 0; d < SLOTS; d++) {
            final int up = home + d;
            if (up <= MAX_TENTHS && !taken.get(up - MIN_TENTHS)) {
                taken.set(up - MIN_TENTHS);
                return up;
            }
            final int down = home - d;
            if (d > 0 && down >= MIN_TENTHS && !taken.get(down - MIN_TENTHS)) {
                taken.set(down - MIN_TENTHS);
                return down;
            }
        }
        return home;
    }
}
