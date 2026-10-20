package com.createstats.stormcoil;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// once per mob per day or a mooshroom farm flips colour every second
public final class CoilZapMemory {
    public static final long TTL = 24_000L;
    public static final int MAX = 256;

    private final LinkedHashMap<UUID, Long> until = new LinkedHashMap<>();

    public void remember(UUID id, long now) {
        until.remove(id);
        until.put(id, now + TTL);
        while (until.size() > MAX) {
            final Iterator<UUID> oldest = until.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    public boolean has(UUID id, long now) {
        final Long end = until.get(id);
        return end != null && now < end;
    }

    public void prune(long now) {
        until.values().removeIf(end -> end <= now);
    }

    public int size() {
        return until.size();
    }

    public long[] pack() {
        final long[] out = new long[until.size() * 3];
        int i = 0;
        for (Map.Entry<UUID, Long> e : until.entrySet()) {
            out[i++] = e.getKey().getMostSignificantBits();
            out[i++] = e.getKey().getLeastSignificantBits();
            out[i++] = e.getValue();
        }
        return out;
    }

    public void unpack(long[] packed) {
        until.clear();
        for (int i = 0; i + 2 < packed.length; i += 3) {
            until.put(new UUID(packed[i], packed[i + 1]), packed[i + 2]);
        }
    }
}
