package com.createstats.network;

import com.createstats.data.TimeSeriesStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class CogDataPacket implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<CogDataPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "cog_data"));
    public static final StreamCodec<FriendlyByteBuf, CogDataPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), CogDataPacket::new);

    private final TimeSeriesStore store;
    
    public CogDataPacket(TimeSeriesStore store) {
        this.store = store;
    }
    
    public CogDataPacket(FriendlyByteBuf buf) {
        this.store = TimeSeriesStore.readFromBuf(buf);
    }
    
    public void encode(FriendlyByteBuf buf) {
        this.store.writeToBuf(buf);
    }
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(CogDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> StatsClientHooks.openCogDashboard(packet.store));
    }
    
    public TimeSeriesStore getStore() {
        return this.store;
    }
}
