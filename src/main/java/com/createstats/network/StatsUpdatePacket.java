package com.createstats.network;

import com.createstats.data.TimeSeriesStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class StatsUpdatePacket implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<StatsUpdatePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "stats_update"));
    public static final StreamCodec<FriendlyByteBuf, StatsUpdatePacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), StatsUpdatePacket::new);

    private final BlockPos pos;
    private final TimeSeriesStore store;
    
    public StatsUpdatePacket(BlockPos pos, TimeSeriesStore store) {
        this.pos = pos;
        this.store = store;
    }
    
    public StatsUpdatePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.store = TimeSeriesStore.readFromBuf(buf);
    }
    
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        this.store.writeToBuf(buf);
    }
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(StatsUpdatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> StatsClientHooks.handleStatsUpdate(packet.pos, packet.store));
    }
}
