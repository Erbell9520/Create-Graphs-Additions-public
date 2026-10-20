package com.createstats.power;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeBankMergeTest {
    private static List<BlockPos> column(int height) {
        final List<BlockPos> positions = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            positions.add(new BlockPos(0, y, 0));
        }
        return positions;
    }

    private static List<BlockPos> box(int width, int height, int depth) {
        final List<BlockPos> positions = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    @Test
    void aColumnWithinTheLimitMergesIntoOneBank() {
        List<List<BlockPos>> banks = FeBankMerge.partition(column(6), 3, 6);
        assertEquals(1, banks.size());
        assertEquals(6, banks.get(0).size());
    }

    @Test
    void aTallerColumnSplitsAtTheHeightLimitFromTheBottomUp() {
        List<List<BlockPos>> banks = FeBankMerge.partition(column(10), 3, 6);
        assertEquals(2, banks.size());
        assertEquals(6, banks.get(0).size());
        assertEquals(4, banks.get(1).size());
        assertEquals(0, banks.get(0).get(0).getY(), "the first bank starts at the bottom");
        assertEquals(6, banks.get(1).get(0).getY(), "the second picks up right above it");
    }

    @Test
    void aFullThreeByThreeBySixIsASingleBank() {
        List<List<BlockPos>> banks = FeBankMerge.partition(box(3, 6, 3), 3, 6);
        assertEquals(1, banks.size());
        assertEquals(54, banks.get(0).size());
    }

    @Test
    void theFootprintLimitIsEnforcedNotJustTheHeight() {
        List<List<BlockPos>> banks = FeBankMerge.partition(box(4, 1, 1), 3, 6);
        assertTrue(banks.size() > 1, "a 4-wide row cannot merge under a 3 footprint");
        for (List<BlockPos> bank : banks) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            for (BlockPos pos : bank) {
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
            }
            assertTrue(maxX - minX + 1 <= 3, "no bank may exceed the footprint");
        }
    }

    @Test
    void thePartitionIsIdenticalWhateverOrderTheClusterArrivesIn() {
        final List<BlockPos> cluster = box(3, 8, 2);
        final List<List<BlockPos>> reference = FeBankMerge.partition(cluster, 3, 6);

        for (long seed = 0; seed < 25; seed++) {
            final List<BlockPos> shuffled = new ArrayList<>(cluster);
            Collections.shuffle(shuffled, new java.util.Random(seed));
            final List<List<BlockPos>> banks = FeBankMerge.partition(shuffled, 3, 6);
            assertEquals(reference, banks, "partition must not depend on input order (seed " + seed + ")");
        }
    }

    @Test
    void everyBlockEndsUpInExactlyOneBank() {
        final List<BlockPos> cluster = box(3, 11, 3);
        final List<List<BlockPos>> banks = FeBankMerge.partition(cluster, 3, 6);
        final List<BlockPos> flattened = new ArrayList<>();
        banks.forEach(flattened::addAll);
        assertEquals(cluster.size(), flattened.size(), "no block is dropped or duplicated");
        assertEquals(cluster.size(), new java.util.HashSet<>(flattened).size());
    }

    private static final int BASE = 1_024_000;

    @Test
    void capacityFollowsTheGentleMergeCurve() {
        assertEquals(BASE, FeBankMerge.capacity(1, BASE, 1.1));
        assertEquals(2_194_992, FeBankMerge.capacity(2, BASE, 1.1));
        assertEquals(4_705_068, FeBankMerge.capacity(4, BASE, 1.1));
        assertEquals(82_401_116, FeBankMerge.capacity(54, BASE, 1.1), "a full 3x3x6 bank");

        assertEquals(4 * BASE, FeBankMerge.capacity(4, BASE, 1.0));

        int previous = 0;
        for (int n = 1; n <= 54; n++) {
            final int capacity = FeBankMerge.capacity(n, BASE, 1.1);
            assertTrue(capacity > previous, "capacity must rise with every added block at n=" + n);
            assertTrue(capacity >= n * BASE, "and never fall below the plain sum at n=" + n);
            previous = capacity;
        }
    }

    @Test
    void anAbsurdConfigSaturatesInsteadOfOverflowing() {
        final int huge = FeBankMerge.capacity(54, 100_000_000, 1.5);
        assertTrue(huge > 0, "capacity must never wrap negative");
        assertEquals(Integer.MAX_VALUE, huge);
        final int[] shares = FeBankMerge.shareCapacity(huge, 54);
        long sum = 0;
        for (int share : shares) {
            sum += share;
        }
        assertEquals(huge, sum, "a saturated total still shares out exactly");
    }

    @Test
    void capacitySharesOutExactlyWithNothingLostToRounding() {
        for (int blocks = 1; blocks <= 54; blocks++) {
            final int total = FeBankMerge.capacity(blocks, BASE, 1.1);
            final int[] shares = FeBankMerge.shareCapacity(total, blocks);
            assertEquals(blocks, shares.length);
            int sum = 0;
            for (int share : shares) {
                sum += share;
            }
            assertEquals(total, sum, "shares must add back up to the bank total at n=" + blocks);

            assertTrue(shares[0] >= shares[shares.length - 1]);
        }
    }

    @Test
    void degenerateInputsDoNotExplode() {
        assertEquals(0, FeBankMerge.capacity(0, 1_024, 1.1));
        assertEquals(0, FeBankMerge.capacity(4, 0, 1.1));
        assertEquals(0, FeBankMerge.shareCapacity(1_000, 0).length);
        assertTrue(FeBankMerge.partition(List.of(), 3, 6).isEmpty());
    }
}
