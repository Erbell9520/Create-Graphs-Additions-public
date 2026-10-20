package com.createstats.network;

import com.createstats.blockentity.SeismicLinks;
import com.createstats.blockentity.StatsDisplayPanelBE;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SeismicPanelPacket(BlockPos panelPos, int op, long arg) implements CustomPacketPayload {
    static final double SEE_RANGE = 128.0;

    public static final CustomPacketPayload.Type<SeismicPanelPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "seismic_panel"));

    public static final StreamCodec<FriendlyByteBuf, SeismicPanelPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.panelPos());
                buf.writeVarInt(pkt.op());
                buf.writeLong(pkt.arg());
            },
            buf -> new SeismicPanelPacket(buf.readBlockPos(), buf.readVarInt(), buf.readLong()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SeismicPanelPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!level.isLoaded(packet.panelPos())
                    || !(level.getBlockEntity(packet.panelPos()) instanceof StatsDisplayPanelBE panel)) {
                return;
            }
            final StatsDisplayPanelBE controller = panel.getControllerBE() != null ? panel.getControllerBE() : panel;
            if (packet.op() == SeismicLinks.OP_REQUEST) {
                // world point. sable tax #13

                final net.minecraft.world.phys.Vec3 at = com.createstats.flight.bridge.SableBridge.worldPointOf(
                        controller, net.minecraft.world.phys.Vec3.atCenterOf(controller.getBlockPos()));
                if (at != null && player.distanceToSqr(at) <= SEE_RANGE * SEE_RANGE) {
                    controller.seismicLinks().onRequest(player, packet.arg(), false);
                }
                return;
            }
            if (!controller.isPlayerInInteractionRange(player)) {
                return;
            }
            if (packet.op() == SeismicLinks.OP_REQUEST_PAGE) {
                controller.seismicLinks().onRequest(player, packet.arg(), true);
                return;
            }
            controller.seismicLinks().command(level, packet.op(), packet.arg());
        });
    }
}
