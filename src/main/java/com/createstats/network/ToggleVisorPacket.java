package com.createstats.network;

import com.createstats.CreateStats;
import com.createstats.item.PilotHelmetData;
import com.createstats.item.PilotHelmetItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleVisorPacket() implements CustomPacketPayload {
    public static final ToggleVisorPacket INSTANCE = new ToggleVisorPacket();

    public static final CustomPacketPayload.Type<ToggleVisorPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "toggle_visor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleVisorPacket> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleVisorPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) {
                return;
            }
            ItemStack head = sp.getItemBySlot(EquipmentSlot.HEAD);
            if (head.isEmpty() || !(head.getItem() instanceof PilotHelmetItem)) {
                return;
            }
            PilotHelmetData data = PilotHelmetItem.getData(head);
            data.setVisorDeployed(!data.isVisorDeployed());
            PilotHelmetItem.setData(head, data);
        });
    }
}
