package com.createstats.network;

import com.createstats.blockentity.ProductionDetectorBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ProductionDetectorSourceModePacket implements CustomPacketPayload {
    private final BlockPos pos;
    private final byte mode; // 0 = DIRECT, 1 = BRIDGE

    public static final CustomPacketPayload.Type<ProductionDetectorSourceModePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "pd_source_mode"));
    public static final StreamCodec<FriendlyByteBuf, ProductionDetectorSourceModePacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), ProductionDetectorSourceModePacket::new);

    public ProductionDetectorSourceModePacket(BlockPos pos, ProductionDetectorBE.SourceMode mode) {
        this.pos = pos;
        this.mode = (byte) mode.ordinal();
    }

    public ProductionDetectorSourceModePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.mode = buf.readByte();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeByte(this.mode);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ProductionDetectorSourceModePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) return;
            final BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof ProductionDetectorBE detector)) return;
            final ProductionDetectorBE.SourceMode mode = packet.mode == 1
                ? ProductionDetectorBE.SourceMode.BRIDGE
                : ProductionDetectorBE.SourceMode.DIRECT;
            detector.setSourceMode(mode);
        });
    }
}
