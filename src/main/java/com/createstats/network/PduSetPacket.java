package com.createstats.network;

import com.createstats.blockentity.PowerDistributionUnitBE;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record PduSetPacket(BlockPos pduPos, int action, int value, List<BlockPos> targets)
        implements CustomPacketPayload {
    public static final int ACTION_PRIORITY = 0;

    public static final int ACTION_LOCK = 1;

    public static final int ACTION_ALL_LIGHTING = 2;

    public static final int ACTION_SET_BREAKER = 3;

    public static int breakerValue(int leg, boolean closed) {
        return (leg & 0xFF) | (closed ? 0x100 : 0);
    }

    public static final int MAX_TARGETS = 512;

    public static final CustomPacketPayload.Type<PduSetPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "pdu_set"));

    public static final StreamCodec<FriendlyByteBuf, PduSetPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pduPos());
                buf.writeVarInt(pkt.action());
                buf.writeVarInt(pkt.value());
                final int n = Math.min(pkt.targets().size(), MAX_TARGETS);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeBlockPos(pkt.targets().get(i));
                }
            },
            buf -> {
                final BlockPos pdu = buf.readBlockPos();
                final int action = buf.readVarInt();
                final int value = buf.readVarInt();
                final int count = buf.readVarInt();
                if (count < 0 || count > MAX_TARGETS) {
                    throw new DecoderException("PDU bulk target count out of range: " + count);
                }
                final List<BlockPos> targets = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    targets.add(buf.readBlockPos());
                }
                return new PduSetPacket(pdu, action, value, targets);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PduSetPacket packet, IPayloadContext ctx) {
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
                case ACTION_PRIORITY -> pdu.bulkSetPriority(packet.targets(), packet.value());
                case ACTION_LOCK -> pdu.bulkSetLock(packet.targets(), packet.value());
                case ACTION_ALL_LIGHTING -> pdu.setAllLightingTier(packet.value());
                case ACTION_SET_BREAKER -> pdu.setLegClosed(
                        packet.value() & 0xFF, (packet.value() & 0x100) != 0);
                default -> {
                }
            }
        });
    }
}
