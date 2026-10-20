package com.createstats.pathfinder.model;

import com.createstats.pathfinder.util.PathfinderNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record ChunkScanCacheEntry(
    int chunkX,
    int chunkZ,
    long lastScannedTick,
    long dirtySinceTick,
    boolean loaded,
    int summaryHash,
    byte[] compressedCellPayload,
    byte[] compressedHeightPayload,
    int passableCount,
    int marginalCount,
    int blockedCount,
    int hazardFlags
) {
    public static final int SCHEMA_VERSION = 2;

    public ChunkScanCacheEntry {
        compressedCellPayload = PathfinderNbt.safePayload(compressedCellPayload);
        compressedHeightPayload = PathfinderNbt.safePayload(compressedHeightPayload);
        passableCount = Math.max(0, passableCount);
        marginalCount = Math.max(0, marginalCount);
        blockedCount = Math.max(0, blockedCount);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SCHEMA_VERSION);
        tag.putInt("ChunkX", chunkX);
        tag.putInt("ChunkZ", chunkZ);
        tag.putLong("LastScannedTick", lastScannedTick);
        tag.putLong("DirtySinceTick", dirtySinceTick);
        tag.putBoolean("Loaded", loaded);
        tag.putInt("SummaryHash", summaryHash);
        tag.putByteArray("Payload", compressedCellPayload);
        tag.putByteArray("HeightPayload", compressedHeightPayload);
        tag.putInt("Passable", passableCount);
        tag.putInt("Marginal", marginalCount);
        tag.putInt("Blocked", blockedCount);
        tag.putInt("HazardFlags", hazardFlags);
        return tag;
    }

    public static ChunkScanCacheEntry load(CompoundTag tag) {
        if (tag == null) return new ChunkScanCacheEntry(0, 0, 0, 0, false, 0, new byte[0], new byte[0], 0, 0, 0, 0);
        return new ChunkScanCacheEntry(tag.getInt("ChunkX"), tag.getInt("ChunkZ"),
            tag.getLong("LastScannedTick"), tag.getLong("DirtySinceTick"), tag.getBoolean("Loaded"),
            tag.getInt("SummaryHash"), tag.getByteArray("Payload"), tag.getByteArray("HeightPayload"), tag.getInt("Passable"),
            tag.getInt("Marginal"), tag.getInt("Blocked"), tag.getInt("HazardFlags"));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(chunkX);
        buf.writeVarInt(chunkZ);
        buf.writeVarLong(lastScannedTick);
        buf.writeVarLong(dirtySinceTick);
        buf.writeBoolean(loaded);
        buf.writeInt(summaryHash);
        buf.writeByteArray(compressedCellPayload);
        buf.writeByteArray(compressedHeightPayload);
        buf.writeVarInt(passableCount);
        buf.writeVarInt(marginalCount);
        buf.writeVarInt(blockedCount);
        buf.writeVarInt(hazardFlags);
    }

    public static ChunkScanCacheEntry decode(FriendlyByteBuf buf) {
        return new ChunkScanCacheEntry(buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
            buf.readVarLong(), buf.readBoolean(), buf.readInt(),
            PathfinderNbt.safePayload(buf.readByteArray(PathfinderNbt.MAX_PAYLOAD_BYTES)),
            PathfinderNbt.safePayload(buf.readByteArray(PathfinderNbt.MAX_PAYLOAD_BYTES)),
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
