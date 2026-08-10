package com.createstats.client.hud;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** [1] */
public final class HudInterpolation {
    private static final long STALE_MS = 8_000L;
    private static final long SWEEP_INTERVAL_MS = 3_000L;
    /** Default critically-damped spring half-life for gauge readouts (ms). */
    public static final float DEFAULT_HALF_LIFE = 0.18f;

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static long lastSweep = 0L;

    private HudInterpolation() { }

    private static final class Entry {
        float value;
        final float[] velocity = new float[] { 0f };
        long lastTouchMs;
    }

    /**
     * Smooth {@code target} toward the cached value for {@code key} using a critically
     * damped spring with {@link #DEFAULT_HALF_LIFE}. The first call for a given key
     * snaps to {@code target} (no animation in).
     */
    public static float smooth(String key, float target) {
        return smooth(key, target, DEFAULT_HALF_LIFE);
    }

    /**
     * Smooth {@code target} toward the cached value for {@code key} using a critically
     * damped spring with the provided {@code halfLife} (seconds, see {@link HudAnim#spring}).
     */
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
        // [2]
        final long deltaMs = now - entry.lastTouchMs;
        final float dt = deltaMs <= 0L ? 1f / 60f : Math.min(0.1f, deltaMs / 1000f);
        entry.value = HudAnim.spring(entry.value, target, entry.velocity, dt, halfLife);
        entry.lastTouchMs = now;
        return entry.value;
    }

    /** Force-snap the cached value for {@code key} to {@code value} with zero velocity. */
    public static void snap(String key, float value) {
        if (key == null || key.isEmpty() || !Float.isFinite(value)) {
            return;
        }
        Entry entry = ENTRIES.computeIfAbsent(key, k -> new Entry());
        entry.value = value;
        entry.velocity[0] = 0f;
        entry.lastTouchMs = System.currentTimeMillis();
    }

    /** Clear cache for a single key (e.g., when its widget is removed). */
    public static void clear(String key) {
        if (key == null || key.isEmpty()) return;
        ENTRIES.remove(key);
    }

    /** Clear all cached state (e.g., on world unload, panel removal). */
    public static void clearAll() {
        ENTRIES.clear();
        lastSweep = 0L;
    }

    /** Number of currently cached entries, exposed for diagnostics / tests. */
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
