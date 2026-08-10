package com.createstats.power;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** [1] */
public final class PduDeviceIds {

    /** [2] */
    public static final int PRUNE_ABOVE = 1024;

    /** Two digits is what fits a row without crowding it; beyond that the number just grows. */
    public static final int DISPLAY_DIGITS = 2;

    private final Map<BlockPos, Integer> assigned = new HashMap<>();
    /** The name each position was numbered under, so a block replaced by a different one renumbers. */
    private final Map<BlockPos, String> namedAs = new HashMap<>();

    /** Drops every assignment. For a PDU that has been broken and replaced. */
    public void clear() {
        assigned.clear();
        namedAs.clear();
    }

    public int size() {
        return assigned.size();
    }

    /** The number this position holds, or 0 if it has never been given one. */
    public int peek(BlockPos pos) {
        return assigned.getOrDefault(pos, 0);
    }

    /**
     * The number for this device, assigning one if it has none.
     *
     * @param pos     where the device is
     * @param name    its display name, which scopes the numbering
     * @param present every position on the branch right now, so a number in use is not handed out twice
     * @return a number from 1 up
     */
    public int idFor(BlockPos pos, String name, Map<BlockPos, String> present) {
        final Integer existing = assigned.get(pos);
        if (existing != null && name.equals(namedAs.get(pos))) {
            return existing;
        }
        // [3]
        final Set<Integer> taken = new HashSet<>();
        for (Map.Entry<BlockPos, Integer> entry : assigned.entrySet()) {
            if (entry.getKey().equals(pos)) {
                continue;
            }
            if (name.equals(namedAs.get(entry.getKey()))) {
                taken.add(entry.getValue());
            }
        }
        int candidate = 1;
        while (taken.contains(candidate)) {
            candidate++;
        }
        assigned.put(pos.immutable(), candidate);
        namedAs.put(pos.immutable(), name);
        if (assigned.size() > PRUNE_ABOVE) {
            prune(present.keySet());
        }
        return candidate;
    }

    /** Forgets every position not in the given set. Only called once the table has grown too large. */
    public void prune(Set<BlockPos> keep) {
        assigned.keySet().retainAll(keep);
        namedAs.keySet().retainAll(keep);
    }

    /** [4] */
    public void assignAll(Map<BlockPos, String> present) {
        final List<BlockPos> pending = new java.util.ArrayList<>();
        for (Map.Entry<BlockPos, String> entry : present.entrySet()) {
            final Integer held = assigned.get(entry.getKey());
            if (held == null || !entry.getValue().equals(namedAs.get(entry.getKey()))) {
                pending.add(entry.getKey());
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        pending.sort((a, b) -> {
            if (a.getX() != b.getX()) {
                return Integer.compare(a.getX(), b.getX());
            }
            if (a.getY() != b.getY()) {
                return Integer.compare(a.getY(), b.getY());
            }
            return Integer.compare(a.getZ(), b.getZ());
        });
        for (BlockPos pos : pending) {
            idFor(pos, present.get(pos), present);
        }
    }

    /** [5] */
    public static String label(String name, int id, boolean unique) {
        if (id <= 0 || unique) {
            return name;
        }
        final StringBuilder digits = new StringBuilder(Integer.toString(id));
        while (digits.length() < DISPLAY_DIGITS) {
            digits.insert(0, '0');
        }
        return name + " ID:" + digits;
    }
}
