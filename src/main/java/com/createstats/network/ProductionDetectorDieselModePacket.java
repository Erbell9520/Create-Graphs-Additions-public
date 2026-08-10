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

public class ProductionDetectorDieselModePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ProductionDetectorDieselModePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "pd_diesel_mode"));
    public static final StreamCodec<FriendlyByteBuf, ProductionDetectorDieselModePacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), ProductionDetectorDieselModePacket::new);

    private final BlockPos pos;
    private final boolean multi;

    public ProductionDetectorDieselModePacket(BlockPos pos, boolean multi) {
        this.pos = pos;
        this.multi = multi;
    }

    public ProductionDetectorDieselModePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.multi = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.multi);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ProductionDetectorDieselModePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            final ServerPlayer player = (ServerPlayer) context.player();
            if (player == null || player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            final BlockEntity blockEntity = player.level().getBlockEntity(packet.pos);
            if (blockEntity instanceof ProductionDetectorBE detector) {
                detector.setDieselMultiStructureMode(packet.multi);
            }
        });
    }
}