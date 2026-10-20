package com.createstats.power;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class FeGridDedupe {
    private FeGridDedupe() {
    }

    public static final int RANK_CONDUIT = 0;

    public static final int RANK_MACHINE = 1;

    public static final int RANK_ATTACHMENT = -1;

    public static final int RANK_TERMINAL = 2;

    public static <T> List<T> collapse(List<T> nodes, Function<T, BlockPos> deviceOf,
                                       ToIntFunction<T> rank) {
        final List<T> out = new ArrayList<>(nodes.size());
        final Map<BlockPos, Integer> slotOf = new HashMap<>();
        for (T node : nodes) {
            final BlockPos device = deviceOf.apply(node);
            if (device == null) {
                out.add(node);
                continue;
            }
            final Integer slot = slotOf.get(device);
            if (slot == null) {
                slotOf.put(device, out.size());
                out.add(node);
                continue;
            }
            if (rank.applyAsInt(node) > rank.applyAsInt(out.get(slot))) {
                out.set(slot, node);
            }
        }
        return out;
    }
}
