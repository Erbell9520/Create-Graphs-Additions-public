package com.createstats.network;

import com.createstats.blockentity.ArcFurnaceBE;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ArcFurnaceActionPacket(BlockPos pos, int action, int value)
        implements CustomPacketPayload {
    public static final int ACTION_RUNNING = 0;

    public static final int ACTION_TAP = 1;

    public static final CustomPacketPayload.Type<ArcFurnaceActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    "create_stats", "arc_furnace_action"));

    public static final StreamCodec<FriendlyByteBuf, ArcFurnaceActionPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos());
                        buf.writeVarInt(pkt.action());
                        buf.writeVarInt(pkt.value());
                    },
                    buf -> new ArcFurnaceActionPacket(buf.readBlockPos(), buf.readVarInt(),
                            buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArcFurnaceActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            // reach guard
            if (!player.level().isLoaded(packet.pos())
                    || player.distanceToSqr(packet.pos().getX() + 0.5,
                            packet.pos().getY() + 0.5, packet.pos().getZ() + 0.5) > 64.0 * 64.0) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.pos()) instanceof ArcFurnaceBE furnace)) {
                return;
            }
            switch (packet.action()) {
                case ACTION_RUNNING -> {
                    final boolean on = packet.value() != 0;
                    furnace.setRunning(on);
                    player.level().playSound(null, packet.pos(), SoundEvents.LEVER_CLICK,
                            SoundSource.BLOCKS, 0.7f, on ? 0.9f : 0.6f);
                }
                case ACTION_TAP -> {
                    furnace.setTap(packet.value());

                    player.level().playSound(null, packet.pos(), SoundEvents.LEVER_CLICK,
                            SoundSource.BLOCKS, 0.5f, 0.7f + 0.2f * furnace.tap());
                }
                default -> {
                }
            }
        });
    }
}
