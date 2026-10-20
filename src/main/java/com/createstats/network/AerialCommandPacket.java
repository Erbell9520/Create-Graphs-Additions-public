package com.createstats.network;

import com.createstats.aerial.AerialCommand;
import com.createstats.blockentity.StatsDisplayPanelBE;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AerialCommandPacket(BlockPos panelPos, int command) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AerialCommandPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("create_stats", "aerial_command"));

    public static final StreamCodec<FriendlyByteBuf, AerialCommandPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.panelPos());
                buf.writeVarInt(pkt.command());
            },
            buf -> new AerialCommandPacket(buf.readBlockPos(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AerialCommandPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final AerialCommand command = AerialCommand.byOrdinal(packet.command());
            if (command == null || !level.isLoaded(packet.panelPos())
                    || !(level.getBlockEntity(packet.panelPos()) instanceof StatsDisplayPanelBE panel)) {
                return;
            }
            final StatsDisplayPanelBE controller = panel.getControllerBE();
            if (controller == null || !controller.isPlayerInInteractionRange(player)) {
                return;
            }
            controller.aerialLinks().command(level, command);
        });
    }
}
