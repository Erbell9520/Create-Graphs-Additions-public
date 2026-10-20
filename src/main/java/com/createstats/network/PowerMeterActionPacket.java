package com.createstats.network;

import com.createstats.blockentity.PowerMeterBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PowerMeterActionPacket(BlockPos meterPos, int action) implements CustomPacketPayload {
    public static final int ACTION_RESET = 0;

    public static final CustomPacketPayload.Type<PowerMeterActionPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "power_meter_action"));

    public static final StreamCodec<FriendlyByteBuf, PowerMeterActionPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.meterPos());
                buf.writeVarInt(pkt.action());
            }, buf -> new PowerMeterActionPacket(buf.readBlockPos(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PowerMeterActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.level().isLoaded(packet.meterPos())
                    || player.distanceToSqr(packet.meterPos().getX() + 0.5,
                            packet.meterPos().getY() + 0.5, packet.meterPos().getZ() + 0.5) > 64.0 * 64.0) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.meterPos()) instanceof PowerMeterBE meter)) {
                return;
            }
            if (packet.action() == ACTION_RESET) {
                meter.resetTotals();
            }
        });
    }
}
