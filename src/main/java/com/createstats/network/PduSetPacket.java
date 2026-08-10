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

/** [1] */
public record PduSetPacket(BlockPos pduPos, int action, int value, List<BlockPos> targets)
        implements CustomPacketPayload {

    /** Set every target's tier to the {@link com.createstats.power.FePriority} ordinal in {@code value}. */
    public static final int ACTION_PRIORITY = 0;
    /** Set every target's lock to the {@link com.createstats.power.FeLock} ordinal in {@code value}. */
    public static final int ACTION_LOCK = 1;
    /** Set every lighting device on the branch to the tier ordinal in {@code value}; targets is ignored. */
    public static final int ACTION_ALL_LIGHTING = 2;
    /** [2] */
    public static final int ACTION_SET_BREAKER = 3;

    /** Packs a breaker instruction into the single value field. */
    public static int breakerValue(int leg, boolean closed) {
        return (leg & 0xFF) | (closed ? 0x100 : 0);
    }

    /** A real branch is small; a crafted packet must not make the server build an unbounded list. */
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
                    // Refuse, do not clamp: clamping would leave the extra positions unread and desync
                    // the whole stream. A malformed packet drops the connection, which is correct.
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
            // Reach guard: a payload must never let a client act on a block it could not touch.
            if (!player.level().isLoaded(packet.pduPos())
                    || player.distanceToSqr(packet.pduPos().getX() + 0.5,
                            packet.pduPos().getY() + 0.5, packet.pduPos().getZ() + 0.5) > 64.0 * 64.0) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.pduPos()) instanceof PowerDistributionUnitBE pdu)) {
                return;
            }
            // The block entity validates every position against its own live roster before applying.
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
