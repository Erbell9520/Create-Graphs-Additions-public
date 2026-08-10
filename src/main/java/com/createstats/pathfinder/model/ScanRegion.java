package com.createstats.pathfinder.model;

import com.createstats.pathfinder.settings.PathScanSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record ScanRegion(
    BlockPos origin,
    float yawDeg,
    int forwardChunks,
    int sideChunks,
    int verticalWindow,
    int chunkStride,
    UnknownPolicy unknownPolicy
) {
    public static final int SCHEMA_VERSION = 1;

    public ScanRegion {
        origin = origin == null ? BlockPos.ZERO : origin;
        yawDeg = normalizeYaw(yawDeg);
        forwardChunks = clamp(forwardChunks, 1, PathScanSettings.MAX_FORWARD_CHUNKS);
        sideChunks = clamp(sideChunks, 0, PathScanSettings.MAX_SIDE_CHUNKS);
        verticalWindow = clamp(verticalWindow, 4, 256);
        chunkStride = chunkStride == 2 ? 2 : 1;
        unknownPolicy = unknownPolicy == null ? UnknownPolicy.BLOCK : unknownPolicy;
    }

    public static ScanRegion fromSettings(BlockPos origin, float yawDeg, PathScanSettings settings) {
        PathScanSettings resolved = settings == null ? PathScanSettings.defaults() : settings;
        return new ScanRegion(origin, yawDeg, resolved.forwardChunks(), resolved.sideChunks(),
            resolved.verticalWindow(), resolved.chunkStride(), resolved.unknownPolicy());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SCHEMA_VERSION);
        tag.putLong("Origin", origin.asLong());
        tag.putFloat("YawDeg", yawDeg);
        tag.putInt("ForwardChunks", forwardChunks);
        tag.putInt("SideChunks", sideChunks);
        tag.putInt("VerticalWindow", verticalWindow);
        tag.putInt("ChunkStride", chunkStride);
        tag.putString("UnknownPolicy", unknownPolicy.name());
        return tag;
    }

    public static ScanRegion load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return ScanRegion.fromSettings(BlockPos.ZERO, 0f, PathScanSettings.defaults());
        return new ScanRegion(
            tag.contains("Origin") ? BlockPos.of(tag.getLong("Origin")) : BlockPos.ZERO,
            tag.getFloat("YawDeg"),
            tag.contains("ForwardChunks") ? tag.getInt("ForwardChunks") : PathScanSettings.DEFAULT_FORWARD_CHUNKS,
            tag.contains("SideChunks") ? tag.getInt("SideChunks") : PathScanSettings.DEFAULT_SIDE_CHUNKS,
            tag.contains("VerticalWindow") ? tag.getInt("VerticalWindow") : PathScanSettings.DEFAULT_VERTICAL_WINDOW,
            tag.contains("ChunkStride") ? tag.getInt("ChunkStride") : PathScanSettings.DEFAULT_CHUNK_STRIDE,
            UnknownPolicy.safeValueOf(tag.getString("UnknownPolicy"), UnknownPolicy.BLOCK)
        );
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(origin);
        buf.writeFloat(yawDeg);
        buf.writeVarInt(forwardChunks);
        buf.writeVarInt(sideChunks);
        buf.writeVarInt(verticalWindow);
        buf.writeVarInt(chunkStride);
        buf.writeEnum(unknownPolicy);
    }

    public static ScanRegion decode(FriendlyByteBuf buf) {
        return new ScanRegion(buf.readBlockPos(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
            buf.readVarInt(), buf.readVarInt(), buf.readEnum(UnknownPolicy.class));
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360f;
        if (normalized < -180f) normalized += 360f;
        if (normalized > 180f) normalized -= 360f;
        return normalized;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}