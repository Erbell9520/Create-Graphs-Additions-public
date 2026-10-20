package com.createstats.power;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

// learn by conservation, headroom is wrong both ways
public final class PduLoadModel {
    public record Tuning(int warmupSamples, float emaAlpha, float peakDecay, int maxTracked) {
        public static final Tuning DEFAULT = new Tuning(10, 0.2f, 0.995f, 512);

        public Tuning {
            warmupSamples = Math.max(1, warmupSamples);
            emaAlpha = Math.min(1.0f, Math.max(0.01f, emaAlpha));
            peakDecay = Math.min(1.0f, Math.max(0.5f, peakDecay));
            maxTracked = Math.max(16, maxTracked);
        }
    }

    private static final class Watch {
        int lastStored = -1;
        int lastDelivered;
        float ema = -1.0f;
        float peak;
        int samples;
        long lastSeen;
    }

    private final Map<Long, Watch> watched = new HashMap<>();
    private Tuning tuning = Tuning.DEFAULT;

    public void setTuning(Tuning tuning) {
        this.tuning = tuning == null ? Tuning.DEFAULT : tuning;
    }

    public Tuning tuning() {
        return tuning;
    }

    public void reset() {
        watched.clear();
    }

    public int size() {
        return watched.size();
    }

    public void sample(long key, int stored, int capacity, long now) {
        if (capacity <= 0) {
            return;
        }
        Watch watch = watched.get(key);
        if (watch == null) {
            if (watched.size() >= tuning.maxTracked()) {
                return;
            }
            watch = new Watch();
            watched.put(key, watch);
        }
        watch.lastSeen = now;
        if (watch.lastStored < 0) {
            watch.lastStored = stored;
            watch.lastDelivered = 0;
            return;
        }

        final int consumed = watch.lastDelivered - (stored - watch.lastStored);
        watch.lastStored = stored;
        watch.lastDelivered = 0;
        if (consumed < 0) {
            return;
        }
        watch.samples = Math.min(Integer.MAX_VALUE - 1, watch.samples + 1);
        watch.ema = watch.ema < 0.0f ? consumed : watch.ema + (consumed - watch.ema) * tuning.emaAlpha();
        watch.peak = Math.max(consumed, watch.peak * tuning.peakDecay());
    }

    public void delivered(long key, int fe) {
        final Watch watch = watched.get(key);
        if (watch != null && fe > 0) {
            watch.lastDelivered += fe;
        }
    }

    public int steady(long key) {
        final Watch watch = watched.get(key);
        if (watch == null || watch.samples < tuning.warmupSamples()) {
            return -1;
        }
        return Math.max(0, Math.round(Math.max(watch.peak, Math.max(0.0f, watch.ema))));
    }

    public boolean knows(long key) {
        final Watch watch = watched.get(key);
        return watch != null && watch.samples >= tuning.warmupSamples();
    }

    public int samples(long key) {
        final Watch watch = watched.get(key);
        return watch == null ? 0 : watch.samples;
    }

    public void retain(Set<Long> present) {
        watched.keySet().retainAll(present);
    }

    public void sweep(long now, long staleTicks) {
        final Iterator<Map.Entry<Long, Watch>> it = watched.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastSeen > staleTicks) {
                it.remove();
            }
        }
    }
}
