package com.createstats.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.blockentity.ProductionDetectorBE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class DetectorResetPacket implements CustomPacketPayload
{
    private final BlockPos pos;
    
    public static final CustomPacketPayload.Type<DetectorResetPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "detector_reset"));
    public static final StreamCodec<FriendlyByteBuf, DetectorResetPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), DetectorResetPacket::new);
    
    public DetectorResetPacket(BlockPos pos) {
        this.pos = pos;
    }
    
    public DetectorResetPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }
    
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(DetectorResetPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            else if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            else {
                final BlockEntity be = player.level().getBlockEntity(packet.pos);
                if (be instanceof ProductionDetectorBE detector) {
                    detector.resetCounters();
                }
                return;
            }
        });
    }
}
