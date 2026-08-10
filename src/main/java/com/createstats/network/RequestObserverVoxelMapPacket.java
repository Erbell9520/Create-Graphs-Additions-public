package com.createstats.network;

import com.createstats.blockentity.DamageObserverBE;
import com.createstats.battle.DamageObserverVoxelMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server. Asks the server for a fresh voxel-map snapshot of the named
 * Damage Observer. Server responds with an {@link ObserverVoxelMapPayloadPacket}.
 */
public class RequestObserverVoxelMapPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestObserverVoxelMapPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "request_observer_voxel_map"));
    public static final StreamCodec<FriendlyByteBuf, RequestObserverVoxelMapPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), RequestObserverVoxelMapPacket::new);

    private final BlockPos observerPos;

    public RequestObserverVoxelMapPacket(BlockPos observerPos) {
        this.observerPos = observerPos;
    }

    public RequestObserverVoxelMapPacket(FriendlyByteBuf buf) {
        this.observerPos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.observerPos);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestObserverVoxelMapPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            // Range sanity check: deny far-away requests.
            double dist2 = player.distanceToSqr(packet.observerPos.getX() + 0.5,
                    packet.observerPos.getY() + 0.5, packet.observerPos.getZ() + 0.5);
            if (dist2 > 128.0 * 128.0) {
                return;
            }
            if (!player.level().isLoaded(packet.observerPos)) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(packet.observerPos);
            if (!(be instanceof DamageObserverBE observer)) {
                return;
            }
            DamageObserverVoxelMap map = observer.buildVoxelMap();
            if (map == null) {
                return;
            }
            PacketDistributor.sendToPlayer(player, new ObserverVoxelMapPayloadPacket(packet.observerPos, map));
        });
    }
}
