package com.createstats.flight.network;

import com.createstats.CreateStats;
import com.createstats.flight.blockentity.FlightControlComputerBE;
import com.createstats.flight.menu.FlightControlComputerMenu;
import com.createstats.flight.runtime.FccHudSubscriptionState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FccHudSubscriptionPacket(int containerId, long controllerPosLong, boolean enabled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FccHudSubscriptionPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "fcc_hud_subscription"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FccHudSubscriptionPacket> STREAM_CODEC =
        StreamCodec.of((buf, packet) -> {
            buf.writeVarInt(packet.containerId);
            buf.writeLong(packet.controllerPosLong);
            buf.writeBoolean(packet.enabled);
        }, buf -> new FccHudSubscriptionPacket(buf.readVarInt(), buf.readLong(), buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FccHudSubscriptionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) {
                return;
            }
            if (!(sp.containerMenu instanceof FlightControlComputerMenu menu)) {
                return;
            }
            if (menu.containerId != packet.containerId) {
                return;
            }
            FlightControlComputerBE fcc = menu.getController();
            if (fcc == null || fcc.isRemoved()) {
                return;
            }
            BlockPos controllerPos = BlockPos.of(packet.controllerPosLong);
            if (!fcc.getBlockPos().equals(controllerPos)) {
                return;
            }
            if (!menu.stillValid(sp)) {
                return;
            }
            if (packet.enabled) {
                FccHudSubscriptionState.subscribe(sp.getUUID(), controllerPos);
            } else {
                FccHudSubscriptionState.unsubscribe(sp.getUUID(), controllerPos);
            }
        });
    }
}
