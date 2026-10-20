package com.createstats.train.power;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class DeviceSyncThrottle<K> {
    public static final int SETTLE_TICKS = 20;

    private record Sent(int value, long tick) {
    }

    private final Map<K, Sent> sent = new HashMap<>();

    public boolean chargeWorthSending(K key, int value, int capacity, long now) {
        final Sent last = sent.get(key);
        if (last == null) {
            return true;
        }
        if (last.value() == value) {
            return false;
        }
        final int cap = Math.max(1, capacity);
        if ((value <= 0) != (last.value() <= 0) || (value >= cap) != (last.value() >= cap)) {
            return true;
        }
        if (Math.abs((long) value - last.value()) >= Math.max(1, cap / 64)) {
            return true;
        }
        return now - last.tick() >= SETTLE_TICKS;
    }

    public boolean rateWorthSending(K key, int value, long now) {
        final Sent last = sent.get(key);
        if (last == null) {
            return true;
        }
        if (last.value() == value) {
            return false;
        }
        if ((value == 0) != (last.value() == 0)) {
            return true;
        }
        if (Math.abs((long) value - last.value()) >= Math.max(1, Math.abs(value) / 16)) {
            return true;
        }
        return now - last.tick() >= SETTLE_TICKS;
    }

    public void sent(K key, int value, long now) {
        sent.put(key, new Sent(value, now));
    }

    public boolean differs(K key, int value) {
        final Sent last = sent.get(key);
        return last != null && last.value() != value;
    }

    public void retain(Predicate<K> keep) {
        sent.keySet().removeIf(keep.negate());
    }

    public int size() {
        return sent.size();
    }
}
