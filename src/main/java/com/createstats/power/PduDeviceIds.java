package com.createstats.power;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// fifteen rows reading "Induction Range" is a wall
public final class PduDeviceIds {
    public static final int PRUNE_ABOVE = 1024;

    public static final int DISPLAY_DIGITS = 2;

    private final Map<BlockPos, Integer> assigned = new HashMap<>();

    private final Map<BlockPos, String> namedAs = new HashMap<>();

    public void clear() {
        assigned.clear();
        namedAs.clear();
    }

    public int size() {
        return assigned.size();
    }

    public int peek(BlockPos pos) {
        return assigned.getOrDefault(pos, 0);
    }

    public int idFor(BlockPos pos, String name, Map<BlockPos, String> present) {
        final Integer existing = assigned.get(pos);
        if (existing != null && name.equals(namedAs.get(pos))) {
            return existing;
        }

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

    public void prune(Set<BlockPos> keep) {
        assigned.keySet().retainAll(keep);
        namedAs.keySet().retainAll(keep);
    }

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
