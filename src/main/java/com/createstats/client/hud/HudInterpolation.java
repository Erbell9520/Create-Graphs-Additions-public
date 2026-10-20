package com.createstats.client.hud;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class HudInterpolation {
    private static final long STALE_MS = 8_000L;
    private static final long SWEEP_INTERVAL_MS = 3_000L;

    public static final float DEFAULT_HALF_LIFE = 0.18f;

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static long lastSweep = 0L;

    private HudInterpolation() { }

    private static final class Entry {
        float value;
        final float[] velocity = new float[] { 0f };
        long lastTouchMs;
    }

    public static float smooth(String key, float target) {
        return smooth(key, target, DEFAULT_HALF_LIFE);
    }

    public static float smooth(String key, float target, float halfLife) {
        if (key == null || key.isEmpty() || !Float.isFinite(target)) {
            return target;
        }
        final long now = System.currentTimeMillis();
        sweepIfStale(now);
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            entry = new Entry();
            entry.value = target;
            entry.lastTouchMs = now;
            ENTRIES.put(key, entry);
            return target;
        }

        final long deltaMs = now - entry.lastTouchMs;
        final float dt = deltaMs <= 0L ? 1f / 60f : Math.min(0.1f, deltaMs / 1000f);
        entry.value = HudAnim.spring(entry.value, target, entry.velocity, dt, halfLife);
        entry.lastTouchMs = now;
        return entry.value;
    }

    public static void snap(String key, float value) {
        if (key == null || key.isEmpty() || !Float.isFinite(value)) {
            return;
        }
        Entry entry = ENTRIES.computeIfAbsent(key, k -> new Entry());
        entry.value = value;
        entry.velocity[0] = 0f;
        entry.lastTouchMs = System.currentTimeMillis();
    }

    public static void clear(String key) {
        if (key == null || key.isEmpty()) return;
        ENTRIES.remove(key);
    }

    public static void clearAll() {
        ENTRIES.clear();
        lastSweep = 0L;
    }

    public static int size() {
        return ENTRIES.size();
    }

    private static void sweepIfStale(long now) {
        if (now - lastSweep < SWEEP_INTERVAL_MS) {
            return;
        }
        lastSweep = now;
        final long cutoff = now - STALE_MS;
        final Iterator<Map.Entry<String, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().lastTouchMs < cutoff) {
                it.remove();
            }
        }
    }
}
