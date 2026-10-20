package com.createstats.vacuum;

import java.util.Objects;

public final class TypeSlots<K> {
    public static final int SLOTS = 3;

    private final Object[] kinds = new Object[SLOTS];
    private final int[] stored = new int[SLOTS];
    private final int[] reserved = new int[SLOTS];

    public int slotOf(K key) {
        for (int i = 0; i < SLOTS; i++) {
            if (kinds[i] != null && Objects.equals(kinds[i], key)) {
                return i;
            }
        }
        return -1;
    }

    private int freeSlot() {
        for (int i = 0; i < SLOTS; i++) {
            if (kinds[i] == null) {
                return i;
            }
        }
        return -1;
    }

    public boolean accepts(K key, int count, int max) {
        final int slot = slotOf(key);
        if (slot >= 0) {
            return stored[slot] + reserved[slot] + count <= max;
        }
        return count <= max && freeSlot() >= 0;
    }

    public boolean reserve(K key, int count, int max) {
        if (count <= 0 || !accepts(key, count, max)) {
            return false;
        }
        int slot = slotOf(key);
        if (slot < 0) {
            slot = freeSlot();
            kinds[slot] = key;
        }
        reserved[slot] += count;
        return true;
    }

    public boolean commit(K key, int count) {
        final int slot = slotOf(key);
        if (slot < 0 || reserved[slot] < count) {
            return false;
        }
        reserved[slot] -= count;
        stored[slot] += count;
        return true;
    }

    public void release(K key, int count) {
        final int slot = slotOf(key);
        if (slot < 0) {
            return;
        }
        reserved[slot] = Math.max(0, reserved[slot] - count);
        forgetIfEmpty(slot);
    }

    public int extract(int slot, int count) {
        if (slot < 0 || slot >= SLOTS || count <= 0) {
            return 0;
        }
        final int out = Math.min(count, stored[slot]);
        stored[slot] -= out;
        forgetIfEmpty(slot);
        return out;
    }

    public void load(int slot, K key, int count) {
        kinds[slot] = count > 0 ? key : null;
        stored[slot] = Math.max(0, count);
        reserved[slot] = 0;
    }

    private void forgetIfEmpty(int slot) {
        if (stored[slot] == 0 && reserved[slot] == 0) {
            kinds[slot] = null;
        }
    }

    @SuppressWarnings("unchecked")
    public K kind(int slot) {
        return (K) kinds[slot];
    }

    public int stored(int slot) {
        return stored[slot];
    }

    public int reserved(int slot) {
        return reserved[slot];
    }

    public int kindsHeld() {
        int n = 0;
        for (Object k : kinds) {
            if (k != null) {
                n++;
            }
        }
        return n;
    }

    public int totalStored() {
        return stored[0] + stored[1] + stored[2];
    }
}
