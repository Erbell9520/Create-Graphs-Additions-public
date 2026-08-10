package com.createstats.pathfinder.settings;

import com.createstats.pathfinder.model.RiskPreset;
import com.createstats.pathfinder.model.UnknownPolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record PathScanSettings(
    int forwardChunks,
    int sideChunks,
    int chunkStride,
    int chunksPerTick,
    int verticalWindow,
    boolean allowTwoBlockPaths,
    UnknownPolicy unknownPolicy,
    RiskPreset riskPreset,
    int cacheTtlTicks
) {
    public static final int SCHEMA_VERSION = 7;
    public static final int DEFAULT_FORWARD_CHUNKS = 8;
    public static final int DEFAULT_SIDE_CHUNKS = 3;
    public static final int DEFAULT_CHUNK_STRIDE = 1;
    public static final int DEFAULT_CHUNKS_PER_TICK = 6;
    public static final int DEFAULT_VERTICAL_WINDOW = 192;
    public static final boolean DEFAULT_ALLOW_TWO_BLOCK_PATHS = false;
    public static final int DEFAULT_CACHE_TTL_TICKS = 20 * 60 * 5;
    public static final int MAX_FORWARD_CHUNKS = 10;
    public static final int MAX_SIDE_CHUNKS = 3;
    public static final int MAX_CHUNKS_PER_TICK = 8;
    // Fixed rearward scan margin (chunks) applied on top of the forward window so the planner can
    // see and maneuver behind the vehicle (reverse detours, K-turns). Not player-configurable.
    public static final int BACKWARD_CHUNKS = 3;
    public static final int MAX_SCAN_CHUNKS = (MAX_FORWARD_CHUNKS + BACKWARD_CHUNKS) * (MAX_SIDE_CHUNKS * 2 + 1);
    public static final int MAX_PATHFINDER_ROUTE_CHUNKS = MAX_SCAN_CHUNKS;

    public PathScanSettings {
        forwardChunks = clamp(forwardChunks, 1, MAX_FORWARD_CHUNKS);
        sideChunks = clamp(sideChunks, 0, MAX_SIDE_CHUNKS);
        chunkStride = chunkStride == 2 ? 2 : 1;
        chunksPerTick = clamp(chunksPerTick, 1, MAX_CHUNKS_PER_TICK);
        verticalWindow = clamp(verticalWindow, 4, 256);
        unknownPolicy = unknownPolicy == null ? UnknownPolicy.BLOCK : unknownPolicy;
        riskPreset = riskPreset == null ? RiskPreset.BALANCED : riskPreset;
        cacheTtlTicks = clamp(cacheTtlTicks, 20 * 10, 20 * 60 * 30);
    }

    public static PathScanSettings defaults() {
        return new PathScanSettings(DEFAULT_FORWARD_CHUNKS, DEFAULT_SIDE_CHUNKS, DEFAULT_CHUNK_STRIDE,
            DEFAULT_CHUNKS_PER_TICK, DEFAULT_VERTICAL_WINDOW, DEFAULT_ALLOW_TWO_BLOCK_PATHS, UnknownPolicy.BLOCK,
            RiskPreset.BALANCED, DEFAULT_CACHE_TTL_TICKS);
    }

    public int expectedChunkCount() {
        // [1]
        return forwardChunks * (sideChunks * 2 + 1);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SCHEMA_VERSION);
        tag.putInt("ForwardChunks", forwardChunks);
        tag.putInt("SideChunks", sideChunks);
        tag.putInt("ChunkStride", chunkStride);
        tag.putInt("ChunksPerTick", chunksPerTick);
        tag.putInt("VerticalWindow", verticalWindow);
        tag.putBoolean("AllowTwoBlockPaths", allowTwoBlockPaths);
        tag.putString("UnknownPolicy", unknownPolicy.name());
        tag.putString("RiskPreset", riskPreset.name());
        tag.putInt("CacheTtlTicks", cacheTtlTicks);
        return tag;
    }

    public static PathScanSettings load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return defaults();
        int version = tag.contains("Version") ? tag.getInt("Version") : 0;
        int loadedForwardChunks = tag.contains("ForwardChunks") ? tag.getInt("ForwardChunks") : 6;
        int loadedSideChunks = tag.contains("SideChunks") ? tag.getInt("SideChunks") : DEFAULT_SIDE_CHUNKS;
        int loadedChunksPerTick = tag.contains("ChunksPerTick") ? tag.getInt("ChunksPerTick") : 2;
        int loadedVerticalWindow = tag.contains("VerticalWindow") ? tag.getInt("VerticalWindow") : DEFAULT_VERTICAL_WINDOW;
        boolean loadedAllowTwoBlockPaths = tag.contains("AllowTwoBlockPaths")
            ? tag.getBoolean("AllowTwoBlockPaths") : DEFAULT_ALLOW_TWO_BLOCK_PATHS;
        if (version < 2 && loadedVerticalWindow == 12) {
            loadedVerticalWindow = DEFAULT_VERTICAL_WINDOW;
        }
        if (version < 3) {
            loadedForwardChunks = Math.min(MAX_FORWARD_CHUNKS, Math.max(DEFAULT_FORWARD_CHUNKS, loadedForwardChunks * 2));
            loadedChunksPerTick = Math.min(MAX_CHUNKS_PER_TICK, Math.max(DEFAULT_CHUNKS_PER_TICK, loadedChunksPerTick * 2));
        }
        if (version < 4) {
            loadedSideChunks = Math.max(DEFAULT_SIDE_CHUNKS, loadedSideChunks);
        }
        if (version < 5) {
            loadedAllowTwoBlockPaths = DEFAULT_ALLOW_TWO_BLOCK_PATHS;
        }
        if (version < 6) {
            loadedForwardChunks = DEFAULT_FORWARD_CHUNKS;
            loadedSideChunks = DEFAULT_SIDE_CHUNKS;
            loadedAllowTwoBlockPaths = DEFAULT_ALLOW_TWO_BLOCK_PATHS;
        }
        if (version < 7) {
            loadedForwardChunks = DEFAULT_FORWARD_CHUNKS;
            loadedSideChunks = DEFAULT_SIDE_CHUNKS;
            loadedChunksPerTick = DEFAULT_CHUNKS_PER_TICK;
        }
        return new PathScanSettings(
            loadedForwardChunks,
            loadedSideChunks,
            tag.contains("ChunkStride") ? tag.getInt("ChunkStride") : DEFAULT_CHUNK_STRIDE,
            loadedChunksPerTick,
            loadedVerticalWindow,
            loadedAllowTwoBlockPaths,
            UnknownPolicy.safeValueOf(tag.getString("UnknownPolicy"), UnknownPolicy.BLOCK),
            RiskPreset.safeValueOf(tag.getString("RiskPreset"), RiskPreset.BALANCED),
            tag.contains("CacheTtlTicks") ? tag.getInt("CacheTtlTicks") : DEFAULT_CACHE_TTL_TICKS
        );
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(forwardChunks);
        buf.writeVarInt(sideChunks);
        buf.writeVarInt(chunkStride);
        buf.writeVarInt(chunksPerTick);
        buf.writeVarInt(verticalWindow);
        buf.writeBoolean(allowTwoBlockPaths);
        buf.writeEnum(unknownPolicy);
        buf.writeEnum(riskPreset);
        buf.writeVarInt(cacheTtlTicks);
    }

    public static PathScanSettings decode(FriendlyByteBuf buf) {
        return new PathScanSettings(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readVarInt(), buf.readBoolean(), buf.readEnum(UnknownPolicy.class),
            buf.readEnum(RiskPreset.class), buf.readVarInt());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
