package com.createstats.network;

import com.createstats.blockentity.PowerDistributionUnitBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PduActionPacket(BlockPos pduPos, BlockPos devicePos, int action) implements CustomPacketPayload {
    public static final int ACTION_CYCLE_PRIORITY = 0;
    public static final int ACTION_CYCLE_MODE = 1;
    public static final int ACTION_AUTO = 2;
    public static final int ACTION_CYCLE_LOCK = 3;

    public static final int ACTION_CYCLE_LEG_PRIORITY = 4;

    public static PduActionPacket cycleLegPriority(BlockPos pduPos, int leg) {
        return new PduActionPacket(pduPos, new BlockPos(leg, 0, 0), ACTION_CYCLE_LEG_PRIORITY);
    }

    public static final CustomPacketPayload.Type<PduActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "pdu_action"));

    public static final StreamCodec<FriendlyByteBuf, PduActionPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.pduPos());
                buf.writeBlockPos(pkt.devicePos());
                buf.writeVarInt(pkt.action());
            }, buf -> new PduActionPacket(buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PduActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }

            if (!player.level().isLoaded(packet.pduPos())
                    || player.distanceToSqr(packet.pduPos().getX() + 0.5,
                            packet.pduPos().getY() + 0.5, packet.pduPos().getZ() + 0.5) > 64.0 * 64.0) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.pduPos()) instanceof PowerDistributionUnitBE pdu)) {
                return;
            }
            switch (packet.action()) {
                case ACTION_CYCLE_PRIORITY -> pdu.cyclePriority(packet.devicePos());
                case ACTION_CYCLE_MODE -> pdu.cycleMode();
                case ACTION_AUTO -> pdu.assignAutoPriorities();
                case ACTION_CYCLE_LOCK -> pdu.cycleLock(packet.devicePos());
                case ACTION_CYCLE_LEG_PRIORITY -> pdu.cycleLegPriority(packet.devicePos().getX());
                default -> {
                }
            }
        });
    }
}
