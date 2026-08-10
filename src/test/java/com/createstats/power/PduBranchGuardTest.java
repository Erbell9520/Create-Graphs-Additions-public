package com.createstats.power;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [1] */
class PduBranchGuardTest {

    @Test
    void keepsOnlyPositionsThatAreOnTheBranch() {
        final Set<BlockPos> branch = Set.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));
        final List<BlockPos> requested = List.of(
                new BlockPos(0, 0, 0), new BlockPos(99, 0, 0), new BlockPos(1, 0, 0));
        assertEquals(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)),
                PduBranchGuard.retainOnBranch(requested, branch));
    }

    @Test
    void aCraftedOffBranchPositionIsDropped() {
        final Set<BlockPos> branch = Set.of(new BlockPos(5, 5, 5));
        assertTrue(PduBranchGuard.retainOnBranch(List.of(new BlockPos(-1000, 0, 0)), branch).isEmpty(),
                "a device on another grid must never be touched");
    }

    @Test
    void anEmptyRequestYieldsNothing() {
        assertTrue(PduBranchGuard.retainOnBranch(List.of(), Set.of(new BlockPos(0, 0, 0))).isEmpty());
    }

    @Test
    void preservesRequestedOrderAndDuplicates() {
        final Set<BlockPos> branch = Set.of(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0));
        assertEquals(
                List.of(new BlockPos(2, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)),
                PduBranchGuard.retainOnBranch(
                        List.of(new BlockPos(2, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)),
                        branch));
    }
}
