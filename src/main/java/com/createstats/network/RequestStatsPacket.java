package com.createstats.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.blockentity.StatsRecorderBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

public class RequestStatsPacket implements CustomPacketPayload
{
    private final BlockPos pos;
    
    public static final CustomPacketPayload.Type<RequestStatsPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "request_stats"));
    public static final StreamCodec<FriendlyByteBuf, RequestStatsPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), RequestStatsPacket::new);
    
    public RequestStatsPacket(BlockPos pos) {
        this.pos = pos;
    }
    
    public RequestStatsPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }
    
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(RequestStatsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            else {
                final BlockEntity be = player.level().getBlockEntity(packet.pos);
                if (be instanceof StatsDisplayPanelBE panel) {
                    PacketDistributor.sendToPlayer(player, new StatsUpdatePacket(packet.pos, panel.getStore()));
                }
                else if (be instanceof StatsRecorderBE recorder) {
                    PacketDistributor.sendToPlayer(player, new StatsUpdatePacket(packet.pos, recorder.getStore()));
                }
                return;
            }
        });
    }
}
