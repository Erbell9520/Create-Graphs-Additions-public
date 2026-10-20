package com.createstats.network;

import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.blockentity.inventory.InventoryFace;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record InventoryFacePacket(BlockPos pos, InventoryFace face) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<InventoryFacePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "inventory_face"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InventoryFacePacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos());
                pkt.face().write(buf);
            },
            buf -> new InventoryFacePacket(buf.readBlockPos(), InventoryFace.read(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InventoryFacePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            final Player player = ctx.player();
            if (player != null && player.level().isLoaded(packet.pos())
                    && player.level().getBlockEntity(packet.pos()) instanceof ProductionDetectorBE detector) {
                detector.applyFace(packet.face());
            }
        });
    }
}
