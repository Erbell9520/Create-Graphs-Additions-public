package com.createstats.network;

import com.createstats.blockentity.BattleScreenDisplayBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** [1] */
public class BattleScreenHologramTogglePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BattleScreenHologramTogglePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "battle_screen_hologram_toggle"));

    public static final StreamCodec<FriendlyByteBuf, BattleScreenHologramTogglePacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), BattleScreenHologramTogglePacket::new);

    private final BlockPos displayPos;

    public BattleScreenHologramTogglePacket(BlockPos displayPos) {
        this.displayPos = displayPos.immutable();
    }

    public BattleScreenHologramTogglePacket(FriendlyByteBuf buf) {
        this.displayPos = buf.readBlockPos();
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.displayPos);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BattleScreenHologramTogglePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (player.distanceToSqr(packet.displayPos.getX() + 0.5, packet.displayPos.getY() + 0.5,
                    packet.displayPos.getZ() + 0.5) > 256.0) {
                return;
            }
            if (level.getBlockEntity(packet.displayPos) instanceof BattleScreenDisplayBE display) {
                display.toggleHologramEnabled();
            }
        });
    }
}
