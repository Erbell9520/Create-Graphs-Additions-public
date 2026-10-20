package com.createstats.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// toasts, everyone already knows how to read them
public record SmartHomeToastPacket(ItemStack icon, Component title, Component body) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SmartHomeToastPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "smart_home_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SmartHomeToastPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, pkt.icon());
                ComponentSerialization.STREAM_CODEC.encode(buf, pkt.title());
                ComponentSerialization.STREAM_CODEC.encode(buf, pkt.body());
            }, buf -> new SmartHomeToastPacket(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SmartHomeToastPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.createstats.util.ClientScreenAccess.showSmartHomeToast(packet));
    }
}
