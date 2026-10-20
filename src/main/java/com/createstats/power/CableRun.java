package com.createstats.power;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// one walker for brush, rip-up and goggles. the brush used to walk its own way and painted the wrong runs
public final class CableRun {
    private CableRun() {
    }

    public static final int MAX_RUN_BLOCKS = 1024;

    public static List<BlockPos> collect(CableLinks.View view, BlockPos origin, int cap) {
        final List<BlockPos> out = new ArrayList<>();
        if (!view.isCable(origin)) {
            return out;
        }
        final Set<BlockPos> seen = new HashSet<>();
        final Deque<BlockPos> queue = new ArrayDeque<>();
        final BlockPos start = origin.immutable();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && out.size() < cap) {
            final BlockPos at = queue.poll();
            out.add(at);
            for (BlockPos next : CableLinks.continuations(view, at)) {
                final BlockPos immutable = next.immutable();
                if (view.isCable(immutable) && seen.add(immutable)) {
                    queue.add(immutable);
                }
            }
        }
        return out;
    }

    public static boolean isTruncated(CableLinks.View view, BlockPos origin, int cap) {
        return collect(view, origin, cap).size() >= cap;
    }
}
