package com.createstats.pathfinder.util;

import com.createstats.pathfinder.model.ChunkScanCacheEntry;
import com.createstats.pathfinder.model.HazardFlags;
import com.createstats.pathfinder.model.Passability;
import com.createstats.pathfinder.model.ScanRegion;
import com.createstats.pathfinder.settings.PathScanSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;

public final class DrivabilityScanner {
    private static final int CHUNK_SIZE = 16;
    private static final int CELL_COUNT = CHUNK_SIZE * CHUNK_SIZE;
    private static final int REQUIRED_CLEARANCE = 2;

    private DrivabilityScanner() {
    }

    public static ChunkScanCacheEntry scanChunk(ServerLevel level, int chunkX, int chunkZ,
                                                ScanRegion region, PathScanSettings settings) {
        long gameTime = level.getGameTime();
        try {
            level.getChunk(chunkX, chunkZ);
        } catch (RuntimeException ex) {
            byte[] unknownPayload = new byte[CELL_COUNT];
            byte[] unknownHeightPayload = new byte[CELL_COUNT];
            Arrays.fill(unknownPayload, pack(Passability.UNKNOWN, HazardFlags.UNKNOWN_CHUNK));
            Arrays.fill(unknownHeightPayload, Byte.MIN_VALUE);
            return new ChunkScanCacheEntry(chunkX, chunkZ, gameTime, 0L, false,
                combinedHash(unknownPayload, unknownHeightPayload), unknownPayload, unknownHeightPayload, 0, 0, 0, HazardFlags.UNKNOWN_CHUNK);
        }

        int[] supportY = new int[CELL_COUNT];
        int[] hazardFlags = new int[CELL_COUNT];
        Passability[] passability = new Passability[CELL_COUNT];
        Arrays.fill(supportY, Integer.MIN_VALUE);

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - REQUIRED_CLEARANCE - 1;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
            for (int localX = 0; localX < CHUNK_SIZE; localX++) {
                int index = index(localX, localZ);
                int worldX = (chunkX << 4) + localX;
                int worldZ = (chunkZ << 4) + localZ;
                int surfaceY = findDriveSurfaceY(level, mutable, worldX, worldZ, minY, maxY);
                if (surfaceY == Integer.MIN_VALUE) {
                    passability[index] = Passability.BLOCKED;
                    hazardFlags[index] = HazardFlags.UNSTABLE;
                    continue;
                }

                mutable.set(worldX, surfaceY, worldZ);
                BlockState supportState = level.getBlockState(mutable);
                int hazards = hazardFlagsFor(level, mutable, supportState);
                if (!isWheelSupport(level, mutable, supportState)) {
                    passability[index] = Passability.BLOCKED;
                    hazardFlags[index] = hazards | HazardFlags.UNSTABLE;
                    continue;
                }

                int clearance = clearanceAbove(level, mutable, REQUIRED_CLEARANCE);
                supportY[index] = surfaceY;
                hazardFlags[index] = hazards;
                if ((hazards & HazardFlags.LAVA) != 0) {
                    passability[index] = Passability.BLOCKED;
                } else if ((hazards & HazardFlags.LIQUID) != 0) {
                    // water = blocked
                    passability[index] = Passability.BLOCKED;
                } else if (clearance >= REQUIRED_CLEARANCE) {
                    passability[index] = Passability.PASSABLE;
                } else {
                    passability[index] = Passability.BLOCKED;
                    hazardFlags[index] |= HazardFlags.UNSTABLE;
                }
            }
        }

        applyHeightDeltaRules(passability, supportY, hazardFlags, settings.allowTwoBlockPaths(),
            fetchBorderHeights(level, chunkX, chunkZ, minY, maxY));

        byte[] payload = new byte[CELL_COUNT];
        byte[] heightPayload = new byte[CELL_COUNT];
        int passableCount = 0;
        int marginalCount = 0;
        int blockedCount = 0;
        int chunkHazards = 0;
        for (int i = 0; i < CELL_COUNT; i++) {
            Passability resolved = passability[i] == null ? Passability.UNKNOWN : passability[i];
            chunkHazards |= hazardFlags[i];
            payload[i] = pack(resolved, hazardFlags[i]);
            heightPayload[i] = packSurfaceHeight(supportY[i], region.origin().getY());
            switch (resolved) {
                case PASSABLE -> passableCount++;
                case MARGINAL -> marginalCount++;
                case BLOCKED -> blockedCount++;
                case UNKNOWN -> {
                }
            }
        }

        return new ChunkScanCacheEntry(chunkX, chunkZ, gameTime, 0L, true, combinedHash(payload, heightPayload),
            payload, heightPayload, passableCount, marginalCount, blockedCount, chunkHazards);
    }

    private static int findDriveSurfaceY(ServerLevel level, BlockPos.MutableBlockPos mutable,
                                         int worldX, int worldZ, int minY, int maxY) {
        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ) - 1;
        if (topY < minY || topY > maxY) return Integer.MIN_VALUE;
        mutable.set(worldX, topY, worldZ);
        BlockState supportState = level.getBlockState(mutable);
        if (!isWheelSupport(level, mutable, supportState)) return Integer.MIN_VALUE;
        return clearanceAbove(level, mutable, REQUIRED_CLEARANCE) >= REQUIRED_CLEARANCE ? topY : Integer.MIN_VALUE;
    }

    private static int[] fetchBorderHeights(ServerLevel level, int chunkX, int chunkZ, int minY, int maxY) {
        int[] border = new int[CHUNK_SIZE * 4];
        int worldX0 = chunkX << 4;
        int worldZ0 = chunkZ << 4;
        for (int i = 0; i < CHUNK_SIZE; i++) {
            border[i]                  = fetchBorderHeight(level, worldX0 - 1,           worldZ0 + i, minY, maxY);
            border[CHUNK_SIZE + i]     = fetchBorderHeight(level, worldX0 + CHUNK_SIZE,  worldZ0 + i, minY, maxY);
            border[CHUNK_SIZE * 2 + i] = fetchBorderHeight(level, worldX0 + i, worldZ0 - 1,           minY, maxY);
            border[CHUNK_SIZE * 3 + i] = fetchBorderHeight(level, worldX0 + i, worldZ0 + CHUNK_SIZE,  minY, maxY);
        }
        return border;
    }

    private static int fetchBorderHeight(ServerLevel level, int worldX, int worldZ, int minY, int maxY) {
        if (!level.hasChunk(worldX >> 4, worldZ >> 4)) return Integer.MIN_VALUE;
        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ) - 1;
        return (topY >= minY && topY <= maxY) ? topY : Integer.MIN_VALUE;
    }

    private static void applyHeightDeltaRules(Passability[] passability, int[] supportY, int[] hazardFlags,
                                              boolean allowTwoBlockPaths, int[] border) {
        for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
            for (int localX = 0; localX < CHUNK_SIZE; localX++) {
                int index = index(localX, localZ);
                if (passability[index] == Passability.BLOCKED || passability[index] == Passability.UNKNOWN
                    || supportY[index] == Integer.MIN_VALUE) {
                    continue;
                }
                int maxDelta = maxNeighborDelta(localX, localZ, supportY, border);
                if (maxDelta > 2) {
                    passability[index] = Passability.BLOCKED;
                    hazardFlags[index] |= HazardFlags.UNSTABLE;
                } else if (maxDelta == 2) {
                    hazardFlags[index] |= HazardFlags.TWO_BLOCK;
                    passability[index] = allowTwoBlockPaths ? Passability.MARGINAL : Passability.BLOCKED;
                } else if (maxDelta == 1 && passability[index] == Passability.PASSABLE) {
                    passability[index] = Passability.MARGINAL;
                }
            }
        }
    }

    private static int maxNeighborDelta(int localX, int localZ, int[] supportY, int[] border) {
        int current = supportY[index(localX, localZ)];
        int maxDelta = 0;
        if (localX > 0)              maxDelta = Math.max(maxDelta, delta(current, supportY[index(localX - 1, localZ)]));
        else                         maxDelta = Math.max(maxDelta, delta(current, border[localZ]));
        if (localX < CHUNK_SIZE - 1) maxDelta = Math.max(maxDelta, delta(current, supportY[index(localX + 1, localZ)]));
        else                         maxDelta = Math.max(maxDelta, delta(current, border[CHUNK_SIZE + localZ]));
        if (localZ > 0)              maxDelta = Math.max(maxDelta, delta(current, supportY[index(localX, localZ - 1)]));
        else                         maxDelta = Math.max(maxDelta, delta(current, border[CHUNK_SIZE * 2 + localX]));
        if (localZ < CHUNK_SIZE - 1) maxDelta = Math.max(maxDelta, delta(current, supportY[index(localX, localZ + 1)]));
        else                         maxDelta = Math.max(maxDelta, delta(current, border[CHUNK_SIZE * 3 + localX]));
        return maxDelta;
    }

    private static int delta(int first, int second) {
        if (first == Integer.MIN_VALUE || second == Integer.MIN_VALUE) return 3;
        return Math.abs(first - second);
    }

    private static int hazardFlagsFor(ServerLevel level, BlockPos.MutableBlockPos pos, BlockState supportState) {
        int hazards = 0;
        if (!supportState.getFluidState().isEmpty()) {
            hazards |= supportState.getFluidState().is(FluidTags.LAVA) ? HazardFlags.LAVA : HazardFlags.LIQUID;
        }
        if (supportState.is(BlockTags.RAILS)) {
            hazards |= HazardFlags.RAIL_OR_FLOOR;
        }
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos(pos.getX(), pos.getY() + 1, pos.getZ());
        BlockState aboveState = level.getBlockState(above);
        if (!aboveState.getFluidState().isEmpty()) {
            hazards |= aboveState.getFluidState().is(FluidTags.LAVA) ? HazardFlags.LAVA : HazardFlags.LIQUID;
        }
        return hazards;
    }

    private static boolean isWheelSupport(ServerLevel level, BlockPos.MutableBlockPos pos, BlockState state) {
        if (state.is(BlockTags.LEAVES)) return false;
        return state.isFaceSturdy(level, pos, Direction.UP) || !state.getCollisionShape(level, pos).isEmpty();
    }

    private static int clearanceAbove(ServerLevel level, BlockPos.MutableBlockPos supportPos, int requiredClearance) {
        int clear = 0;
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        for (int offset = 1; offset <= requiredClearance; offset++) {
            checkPos.set(supportPos.getX(), supportPos.getY() + offset, supportPos.getZ());
            BlockState state = level.getBlockState(checkPos);
            if (!state.getCollisionShape(level, checkPos).isEmpty() || !state.getFluidState().isEmpty()) {
                break;
            }
            clear++;
        }
        return clear;
    }

    private static byte pack(Passability passability, int hazards) {
        return (byte) (((passability.ordinal() & 0x3) << 6) | (hazards & 0x3F));
    }

    private static byte packSurfaceHeight(int surfaceY, int originY) {
        if (surfaceY == Integer.MIN_VALUE) return Byte.MIN_VALUE;
        int delta = Math.max(Byte.MIN_VALUE + 1, Math.min(Byte.MAX_VALUE, surfaceY - originY));
        return (byte) delta;
    }

    private static int combinedHash(byte[] payload, byte[] heightPayload) {
        return 31 * Arrays.hashCode(payload) + Arrays.hashCode(heightPayload);
    }

    private static int index(int localX, int localZ) {
        return localZ * CHUNK_SIZE + localX;
    }
}
