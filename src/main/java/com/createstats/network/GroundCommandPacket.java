package com.createstats.network;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.ground.GroundCommand;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GroundCommandPacket(BlockPos panelPos, int command) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GroundCommandPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "ground_command"));

    public static final StreamCodec<FriendlyByteBuf, GroundCommandPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.panelPos());
                buf.writeVarInt(pkt.command());
            },
            buf -> new GroundCommandPacket(buf.readBlockPos(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GroundCommandPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final GroundCommand command = GroundCommand.byOrdinal(packet.command());
            if (command == null || !level.isLoaded(packet.panelPos())
                    || !(level.getBlockEntity(packet.panelPos()) instanceof StatsDisplayPanelBE panel)) {
                return;
            }
            final StatsDisplayPanelBE controller = panel.getControllerBE();
            if (controller == null || !controller.isPlayerInInteractionRange(player)) {
                return;
            }
            controller.groundLinks().command(level, command);
        });
    }
}
