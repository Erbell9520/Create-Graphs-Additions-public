package com.createstats.network;

import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.power.WireTension;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WireTensionPacket(BlockPos connector, long[] far, byte[] tension) implements CustomPacketPayload {
    private static final int MAX_SPANS = 16;

    public static final CustomPacketPayload.Type<WireTensionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "wire_tension"));

    public static final StreamCodec<FriendlyByteBuf, WireTensionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.connector);
                final int n = Math.min(MAX_SPANS, Math.min(pkt.far.length, pkt.tension.length));
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeLong(pkt.far[i]);
                    buf.writeByte(pkt.tension[i]);
                }
            },
            buf -> {
                final BlockPos connector = buf.readBlockPos();
                final int n = Math.min(MAX_SPANS, Math.max(0, buf.readVarInt()));
                final long[] far = new long[n];
                final byte[] tension = new byte[n];
                for (int i = 0; i < n; i++) {
                    far[i] = buf.readLong();
                    tension[i] = buf.readByte();
                }
                return new WireTensionPacket(connector, far, tension);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WireTensionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            final Level level = player.level();
            final BlockPos pos = packet.connector;
            if (!level.isLoaded(pos) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    > WireTension.REACH * WireTension.REACH) {
                return;
            }
            if (!(level.getBlockEntity(pos) instanceof PowerConnectorBE connector)) {
                return;
            }
            WireTension tightest = null;
            final int n = Math.min(packet.far.length, packet.tension.length);
            for (int i = 0; i < n; i++) {
                final BlockPos far = BlockPos.of(packet.far[i]);
                if (!connector.links().contains(far)) {
                    continue;
                }
                final WireTension t = WireTension.byId(packet.tension[i]);
                connector.setTension(far, t);
                // isLoaded first, don't load a chunk for a cosmetic byte

                if (level.isLoaded(far) && level.getBlockEntity(far) instanceof PowerConnectorBE other
                        && other.links().contains(pos)) {
                    other.setTension(pos, t);
                }
                if (tightest == null || t.slack() < tightest.slack()) {
                    tightest = t;
                }
            }
            if (tightest != null) {
                final float pitch = 0.8f + 0.15f * tightest.ordinal();
                level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7f, pitch);
            }
        });
    }
}
