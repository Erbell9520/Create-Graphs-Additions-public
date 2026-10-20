package com.createstats.network;

import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class DetectorResetPacket implements CustomPacketPayload {
    private final BlockPos pos;

    public static final CustomPacketPayload.Type<DetectorResetPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "detector_reset"));
    public static final StreamCodec<FriendlyByteBuf, DetectorResetPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), DetectorResetPacket::new);

    public DetectorResetPacket(BlockPos pos) {
        this.pos = pos;
    }

    public DetectorResetPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DetectorResetPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !player.level().isLoaded(packet.pos)) {
                return;
            }
            if (player.level().getBlockEntity(packet.pos) instanceof ProductionDetectorBE detector
                    && SableBridge.isPlayerInInteractionRangeFor(detector, player)) {
                detector.closeBooks();
            }
        });
    }
}
