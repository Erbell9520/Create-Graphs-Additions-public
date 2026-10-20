package com.createstats.network;

import com.createstats.blockentity.AnnouncementComputerBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PaActionPacket(BlockPos pos, String verb, String key, String value, float number)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PaActionPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "pa_action"));

    public static final StreamCodec<FriendlyByteBuf, PaActionPacket> STREAM_CODEC =
            StreamCodec.of(PaActionPacket::encode, PaActionPacket::decode);

    public static PaActionPacket of(BlockPos pos, String verb) {
        return new PaActionPacket(pos, verb, "", "", 0.0f);
    }

    public static PaActionPacket of(BlockPos pos, String verb, String key) {
        return new PaActionPacket(pos, verb, key, "", 0.0f);
    }

    public static PaActionPacket of(BlockPos pos, String verb, String key, String value) {
        return new PaActionPacket(pos, verb, key, value, 0.0f);
    }

    public static PaActionPacket of(BlockPos pos, String verb, String key, float number) {
        return new PaActionPacket(pos, verb, key, "", number);
    }

    private static void encode(FriendlyByteBuf buf, PaActionPacket pkt) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.verb, 32);
        buf.writeUtf(pkt.key, 64);
        buf.writeUtf(pkt.value, 32);
        buf.writeFloat(pkt.number);
    }

    private static PaActionPacket decode(FriendlyByteBuf buf) {
        return new PaActionPacket(buf.readBlockPos(), buf.readUtf(32), buf.readUtf(64),
                buf.readUtf(32), buf.readFloat());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PaActionPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            if (!player.level().isLoaded(pkt.pos())
                    || player.blockPosition().distSqr(pkt.pos()) > 64 * 64) {
                return;
            }
            if (player.level().getBlockEntity(pkt.pos()) instanceof AnnouncementComputerBE brain) {
                com.createstats.pa.PaConsoleRequests.apply(player, brain, pkt);
            }
        });
    }
}
