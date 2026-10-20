package com.createstats.power;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FeBankMerge {
    private FeBankMerge() {
    }

    public static final Comparator<BlockPos> ORDER = Comparator
            .<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getZ);

    public static List<List<BlockPos>> partition(List<BlockPos> cluster, int maxFootprint, int maxHeight) {
        final List<BlockPos> sorted = new ArrayList<>(cluster);
        sorted.sort(ORDER);

        final List<List<BlockPos>> banks = new ArrayList<>();
        List<BlockPos> current = new ArrayList<>();
        int minX = 0;
        int maxX = 0;
        int minY = 0;
        int maxY = 0;
        int minZ = 0;
        int maxZ = 0;

        for (BlockPos pos : sorted) {
            if (current.isEmpty()) {
                current.add(pos);
                minX = maxX = pos.getX();
                minY = maxY = pos.getY();
                minZ = maxZ = pos.getZ();
                continue;
            }
            final int nx0 = Math.min(minX, pos.getX());
            final int nx1 = Math.max(maxX, pos.getX());
            final int ny0 = Math.min(minY, pos.getY());
            final int ny1 = Math.max(maxY, pos.getY());
            final int nz0 = Math.min(minZ, pos.getZ());
            final int nz1 = Math.max(maxZ, pos.getZ());
            final boolean fits = (nx1 - nx0 + 1) <= maxFootprint
                    && (nz1 - nz0 + 1) <= maxFootprint
                    && (ny1 - ny0 + 1) <= maxHeight;
            if (fits) {
                current.add(pos);
                minX = nx0;
                maxX = nx1;
                minY = ny0;
                maxY = ny1;
                minZ = nz0;
                maxZ = nz1;
            } else {
                banks.add(current);
                current = new ArrayList<>();
                current.add(pos);
                minX = maxX = pos.getX();
                minY = maxY = pos.getY();
                minZ = maxZ = pos.getZ();
            }
        }
        if (!current.isEmpty()) {
            banks.add(current);
        }
        return banks;
    }

    // superlinear on purpose, base * n^1.1. merging is a build decision, it should pay a little
    public static int capacity(int blocks, int basePerBlock, double exponent) {
        if (blocks <= 0 || basePerBlock <= 0) {
            return 0;
        }
        if (blocks == 1) {
            return basePerBlock;
        }
        final double total = (double) basePerBlock * Math.pow(blocks, exponent);
        return (int) Math.max(basePerBlock, Math.min(Integer.MAX_VALUE, Math.round(total)));
    }

    public static int[] shareCapacity(int totalCapacity, int blocks) {
        final int[] shares = new int[Math.max(0, blocks)];
        if (shares.length == 0) {
            return shares;
        }
        final int even = totalCapacity / blocks;
        int remainder = totalCapacity - even * blocks;
        for (int i = 0; i < shares.length; i++) {
            shares[i] = even + (remainder > 0 ? 1 : 0);
            if (remainder > 0) {
                remainder--;
            }
        }
        return shares;
    }
}
