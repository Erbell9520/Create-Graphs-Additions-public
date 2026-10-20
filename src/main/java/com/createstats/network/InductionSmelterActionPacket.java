package com.createstats.network;

import com.createstats.blockentity.InductionSmelterBE;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record InductionSmelterActionPacket(BlockPos pos, int action, int value)
        implements CustomPacketPayload {
    public static final int ACTION_DAMPER = 0;

    public static final CustomPacketPayload.Type<InductionSmelterActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    "create_stats", "induction_smelter_action"));

    public static final StreamCodec<FriendlyByteBuf, InductionSmelterActionPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos());
                        buf.writeVarInt(pkt.action());
                        buf.writeVarInt(pkt.value());
                    },
                    buf -> new InductionSmelterActionPacket(buf.readBlockPos(), buf.readVarInt(),
                            buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InductionSmelterActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }

            if (!player.level().isLoaded(packet.pos())
                    || player.distanceToSqr(packet.pos().getX() + 0.5,
                            packet.pos().getY() + 0.5, packet.pos().getZ() + 0.5) > 64.0 * 64.0) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.pos()) instanceof InductionSmelterBE smelter)) {
                return;
            }
            if (packet.action() == ACTION_DAMPER && smelter.setDamper(packet.value())) {
                player.level().playSound(null, packet.pos(), SoundEvents.LEVER_CLICK,
                        SoundSource.BLOCKS, 0.6f, 0.7f + 0.22f * smelter.damper());
            }
        });
    }
}
