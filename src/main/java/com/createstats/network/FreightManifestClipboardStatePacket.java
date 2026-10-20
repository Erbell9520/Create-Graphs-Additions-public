package com.createstats.network;

import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightManifest;
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
    private final FreightManifest manifest;
    private final FreightStationCargoView cargoView;
    private final DeliveryOrderBoardView deliveryBoard;
    private final boolean saveReply;
    private final boolean saveAccepted;

    public FreightManifestClipboardStatePacket(BlockPos pos, FreightManifest manifest,
                                               FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard) {
        this(pos, manifest, cargoView, deliveryBoard, false, false);
    }

    public FreightManifestClipboardStatePacket(BlockPos pos, FreightManifest manifest,
            FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard,
            boolean saveReply, boolean saveAccepted) {
        this.pos = pos;
        this.manifest = manifest.copy();
        this.cargoView = cargoView.copy();
        this.deliveryBoard = deliveryBoard;
        this.saveReply = saveReply;
        this.saveAccepted = saveAccepted;
    }

    public FreightManifestClipboardStatePacket(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.manifest = FreightManifest.decode(buf);
        this.cargoView = FreightStationCargoView.decode(buf);
        this.deliveryBoard = DeliveryOrderBoardView.decode(buf);
        this.saveReply = buf.readBoolean();
        this.saveAccepted = buf.readBoolean();
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        this.manifest.encode(buf);
        this.cargoView.encode(buf);
        this.deliveryBoard.encode(buf);
        buf.writeBoolean(this.saveReply);
        buf.writeBoolean(this.saveAccepted);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FreightManifestClipboardStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> StatsClientHooks.handleFreightManifestClipboardState(packet.pos, packet.manifest,
            packet.cargoView, packet.deliveryBoard, packet.saveReply, packet.saveAccepted));
    }
}
