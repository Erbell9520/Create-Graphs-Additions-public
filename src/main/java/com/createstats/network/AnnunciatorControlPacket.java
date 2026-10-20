package com.createstats.network;

import com.createstats.blockentity.AnnunciatorBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class AnnunciatorControlPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AnnunciatorControlPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "annunciator_control"));

    public static final StreamCodec<FriendlyByteBuf, AnnunciatorControlPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), AnnunciatorControlPacket::new);

    public enum Control {
        ACKNOWLEDGE,
        RESET,
        CYCLE_TOGGLE,

        CLEAR_LOG
    }

    private final BlockPos pos;
    private final Control control;

    public AnnunciatorControlPacket(BlockPos pos, Control control) {
        this.pos = pos;
        this.control = control;
    }

    public AnnunciatorControlPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.control = buf.readEnum(Control.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeEnum(this.control);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnnunciatorControlPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5,
                    packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof AnnunciatorBE annunciator)) {
                return;
            }
            switch (packet.control) {
                case ACKNOWLEDGE -> annunciator.acknowledge();
                case RESET -> annunciator.reset();
                case CYCLE_TOGGLE -> annunciator.cycleToggle();
                case CLEAR_LOG -> annunciator.clearLog();
            }
        });
    }
}
