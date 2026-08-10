package com.createstats.network;

import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightStationCargoView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class FreightManifestClipboardStatePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FreightManifestClipboardStatePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "freight_manifest_clipboard_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FreightManifestClipboardStatePacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), FreightManifestClipboardStatePacket::new);

    private final BlockPos pos;
    private final FreightStationCargoView cargoView;
    private final DeliveryOrderBoardView deliveryBoard;

    public FreightManifestClipboardStatePacket(BlockPos pos, FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard) {
        this.pos = pos;
        this.cargoView = cargoView.copy();
        this.deliveryBoard = deliveryBoard;
    }

    public FreightManifestClipboardStatePacket(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.cargoView = FreightStationCargoView.decode(buf);
        this.deliveryBoard = DeliveryOrderBoardView.decode(buf);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        this.cargoView.encode(buf);
        this.deliveryBoard.encode(buf);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FreightManifestClipboardStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> StatsClientHooks.handleFreightManifestClipboardState(packet.pos, packet.cargoView, packet.deliveryBoard));
    }
}
