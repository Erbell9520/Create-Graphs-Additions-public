package com.createstats.power;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// third party machines keep their tier here, nowhere else to put it
public final class PduDeviceOverrides {
    private static final int TIER_MASK = 0x07;
    private static final int LOCK_SHIFT = 3;
    private static final int LOCK_MASK = 0x03;

    private final Map<Long, Byte> byPos = new HashMap<>();

    public FePriority priority(long pos) {
        final Byte packed = byPos.get(pos);
        return packed == null ? FePriority.DEFAULT : FePriority.byOrdinal(packed & TIER_MASK);
    }

    public FeLock lock(long pos) {
        final Byte packed = byPos.get(pos);
        return packed == null ? FeLock.NONE : FeLock.byOrdinal((packed >> LOCK_SHIFT) & LOCK_MASK);
    }

    public void setPriority(long pos, FePriority tier) {
        put(pos, tier == null ? FePriority.DEFAULT : tier, lock(pos));
    }

    public void setLock(long pos, FeLock lock) {
        put(pos, priority(pos), lock == null ? FeLock.NONE : lock);
    }

    public boolean has(long pos) {
        return byPos.containsKey(pos);
    }

    public int size() {
        return byPos.size();
    }

    public void forget(long pos) {
        byPos.remove(pos);
    }

    public Set<Long> positions() {
        return Set.copyOf(byPos.keySet());
    }

    private void put(long pos, FePriority tier, FeLock lock) {
        if (tier == FePriority.DEFAULT && lock == FeLock.NONE) {
            byPos.remove(pos);
            return;
        }
        byPos.put(pos, (byte) ((tier.ordinal() & TIER_MASK) | ((lock.ordinal() & LOCK_MASK) << LOCK_SHIFT)));
    }

    public void save(CompoundTag tag, String key) {
        if (byPos.isEmpty()) {
            return;
        }
        final long[] positions = new long[byPos.size()];
        final byte[] values = new byte[byPos.size()];
        int i = 0;
        for (Map.Entry<Long, Byte> e : byPos.entrySet()) {
            positions[i] = e.getKey();
            values[i] = e.getValue();
            i++;
        }
        final CompoundTag out = new CompoundTag();
        out.putLongArray("P", positions);
        out.putByteArray("V", values);
        tag.put(key, out);
    }

    public void load(CompoundTag tag, String key) {
        byPos.clear();
        if (!tag.contains(key)) {
            return;
        }
        final CompoundTag in = tag.getCompound(key);
        final long[] positions = in.getLongArray("P");
        final byte[] values = in.getByteArray("V");

        final int n = Math.min(positions.length, values.length);
        for (int i = 0; i < n; i++) {
            byPos.put(positions[i], values[i]);
        }
    }
}
