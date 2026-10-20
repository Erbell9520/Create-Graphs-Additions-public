package com.createstats.network;

import com.createstats.blockentity.HomeSecurityPanelBE;
import com.createstats.security.LockerCode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PanelKeypadPacket(BlockPos pos, String code) implements CustomPacketPayload {
    private static final double REACH_SQ = 64.0;

    public static final CustomPacketPayload.Type<PanelKeypadPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "panel_keypad"));

    public static final StreamCodec<FriendlyByteBuf, PanelKeypadPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.pos());
                buf.writeUtf(pkt.code(), LockerCode.DIGITS);
            }, buf -> new PanelKeypadPacket(buf.readBlockPos(), buf.readUtf(LockerCode.DIGITS)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PanelKeypadPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
                return;
            }

            if (sender.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5,
                    packet.pos().getZ() + 0.5) > REACH_SQ) {
                return;
            }
            final net.minecraft.world.level.block.entity.BlockEntity be = sender.level().getBlockEntity(packet.pos());
            if (be instanceof HomeSecurityPanelBE panel) {
                panel.offerPassword(sender, packet.code());
            } else if (be instanceof com.createstats.blockentity.SecurityRelayBE relay) {
                relay.offerPassword(sender, packet.code());
            }
        });
    }
}
