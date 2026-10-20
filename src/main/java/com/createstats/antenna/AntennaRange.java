package com.createstats.antenna;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class AntennaRange {
    public static final double NETHER_COMPRESSION = 1.0 / 8.0;

    private AntennaRange() {
    }

    public static double localRange(double baseRange, AntennaTier tier, boolean isNether) {
        AntennaTier resolvedTier = tier == null ? AntennaTier.NONE : tier;
        double range = Math.max(baseRange, baseRange * resolvedTier.getRangeMultiplier());
        if (resolvedTier == AntennaTier.TRT && isNether) {
            range = Math.max(baseRange, range * NETHER_COMPRESSION);
        }
        return range;
    }

    public static boolean withinRangeSq(double distanceSq, double baseRange, AntennaTier tier, boolean isNether) {
        if (baseRange <= 0.0) {
            return true;
        }
        double range = localRange(baseRange, tier, isNether);
        return distanceSq <= range * range;
    }

    public static boolean crossDimEligible(AntennaTier tier, boolean overworldNetherPair) {
        return tier != null && tier.isCrossDimensional() && overworldNetherPair;
    }

    public static boolean isOverworldNetherPair(ResourceLocation first, ResourceLocation second) {
        if (first == null || second == null) {
            return false;
        }
        ResourceLocation overworld = Level.OVERWORLD.location();
        ResourceLocation nether = Level.NETHER.location();
        return (overworld.equals(first) && nether.equals(second))
                || (nether.equals(first) && overworld.equals(second));
    }

    public static int scale(int baseRange, AntennaTier tier) {
        AntennaTier resolvedTier = tier == null ? AntennaTier.NONE : tier;
        return Math.max(baseRange, baseRange * resolvedTier.getRangeMultiplier());
    }

    public static double directRange(double baseRange, AntennaTier tier) {
        return localRange(baseRange, tier, false);
    }

    public static double directRange(ResourceKey<Level> dimension, double baseRange, AntennaTier tier) {
        return localRange(baseRange, tier, isNether(dimension));
    }

    public static int cappedScanRadius(int baseRange, AntennaTier tier, int maxRange) {
        return Math.max(baseRange, Math.min(maxRange, scale(baseRange, tier)));
    }

    public static boolean canReach(ServerLevel sourceLevel, BlockPos sourcePos, ServerLevel targetLevel, BlockPos targetPos,
                                   AntennaTier tier) {
        return canReach(sourceLevel, sourcePos, targetLevel, targetPos, tier, 0.0);
    }

    public static boolean canReach(ServerLevel sourceLevel, BlockPos sourcePos, ServerLevel targetLevel, BlockPos targetPos,
                                   AntennaTier tier, double baseRange) {
        if (sourceLevel == null || sourcePos == null || targetLevel == null || targetPos == null) {
            return false;
        }
        AntennaTier resolvedTier = tier == null ? AntennaTier.NONE : tier;
        if (sourceLevel.dimension().equals(targetLevel.dimension())) {
            double dx = sourcePos.getX() - targetPos.getX();
            double dy = sourcePos.getY() - targetPos.getY();
            double dz = sourcePos.getZ() - targetPos.getZ();
            return withinRangeSq(dx * dx + dy * dy + dz * dz, baseRange, resolvedTier, isNether(sourceLevel.dimension()));
        }
        return crossDimEligible(resolvedTier, isOverworldNetherPair(sourceLevel.dimension(), targetLevel.dimension()))
                && isChunkLoaded(targetLevel, targetPos);
    }

    public static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && level.isLoaded(pos);
    }

    public static boolean isOverworldNetherPair(ResourceKey<Level> first, ResourceKey<Level> second) {
        if (first == null || second == null) {
            return false;
        }
        return isOverworldNetherPair(first.location(), second.location());
    }

    public static boolean isNether(ResourceKey<Level> dimension) {
        return dimension != null && Level.NETHER.equals(dimension);
    }
}
