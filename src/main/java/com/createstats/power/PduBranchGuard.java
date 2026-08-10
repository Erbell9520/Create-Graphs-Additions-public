package com.createstats.power;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** [1] */
public final class PduBranchGuard {

    private PduBranchGuard() {
    }

    /** [2] */
    public static List<BlockPos> retainOnBranch(Collection<BlockPos> requested, Set<BlockPos> branch) {
        final List<BlockPos> kept = new ArrayList<>();
        for (BlockPos pos : requested) {
            if (branch.contains(pos)) {
                kept.add(pos);
            }
        }
        return kept;
    }
}
