package com.createstats.network;

import com.createstats.data.TimeSeriesStore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import com.createstats.data.MemoryCogData;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

public class RequestCogDataPacket implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<RequestCogDataPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "request_cog_data"));
    public static final StreamCodec<FriendlyByteBuf, RequestCogDataPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), RequestCogDataPacket::new);
    
    public RequestCogDataPacket() {
    }
    
    public RequestCogDataPacket(FriendlyByteBuf buf) {
    }
    
    public void encode(FriendlyByteBuf buf) {
    }
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void handle(RequestCogDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            else {
                final ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
                if (!MemoryCogData.hasData(offhand)) {
                    return;
                }
                else {
                    final TimeSeriesStore store = MemoryCogData.readFromStack(offhand);
                    if (store == null || store.getSampleCount() == 0) {
                        return;
                    }
                    else {
                        PacketDistributor.sendToPlayer(player, new CogDataPacket(store));
                        return;
                    }
                }
            }
        });
    }
}
