package com.createstats.network;

import com.createstats.netscan.NetscanHost;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class NetscanSubscribePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NetscanSubscribePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "netscan_subscribe"));

    public static final StreamCodec<FriendlyByteBuf, NetscanSubscribePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), NetscanSubscribePacket::new);

    private final BlockPos pos;
    private final boolean active;

    public NetscanSubscribePacket(BlockPos pos, boolean active) {
        this.pos = pos;
        this.active = active;
    }

    public NetscanSubscribePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.active = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.active);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NetscanSubscribePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }

            if (!player.level().isLoaded(packet.pos)
                    || player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5,
                    packet.pos.getZ() + 0.5) > 64.0 * 64.0) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.pos) instanceof NetscanHost host)) {
                return;
            }
            if (packet.active) {
                host.scanEngine().watch(player);
            } else {
                host.scanEngine().unwatch(player);
            }
        });
    }
}
