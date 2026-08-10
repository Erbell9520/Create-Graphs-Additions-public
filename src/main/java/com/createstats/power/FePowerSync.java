package com.createstats.power;

import com.createstats.network.FePowerSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

/** [1] */
public final class FePowerSync {

    private FePowerSync() {
    }

    /** Broadcast one consumer's snapshot to every player tracking its chunk. */
    public static void push(Level level, BlockPos pos, FePowerSnapshot snapshot) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new net.minecraft.world.level.ChunkPos(pos),
                new FePowerSyncPacket(pos.immutable(), snapshot));
    }

    /** [2] */
    public static void sendChunkContents(ServerPlayer player, LevelChunk chunk) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof IFeConsumer owner) {
                PacketDistributor.sendToPlayer(player, new FePowerSyncPacket(
                        blockEntity.getBlockPos().immutable(), owner.feConsumer().snapshot()));
            }
        }
    }
}
