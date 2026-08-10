package com.createstats.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.blockentity.ProductionDetectorBE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class DetectorRangePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DetectorRangePacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "detector_range"));
    
    public static final StreamCodec<FriendlyByteBuf, DetectorRangePacket> STREAM_CODEC = 
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), DetectorRangePacket::new);

    private final BlockPos pos;
    private final int range;
    private final int rateWindowSeconds;

    public DetectorRangePacket(BlockPos pos, int range, int rateWindowSeconds) {
        this.pos = pos;
        this.range = range;
        this.rateWindowSeconds = rateWindowSeconds;
    }

    public DetectorRangePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.range = buf.readVarInt();
        this.rateWindowSeconds = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.range);
        buf.writeVarInt(this.rateWindowSeconds);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DetectorRangePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (be instanceof ProductionDetectorBE detector) {
                detector.setDetectionRange(packet.range);
                final BlockState state = player.level().getBlockState(packet.pos);
                player.level().sendBlockUpdated(packet.pos, state, state, 3);
            }
        });
    }
}
