package com.createstats.network;

import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import com.createstats.util.ClientScreenAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class OpenFreightManifestClipboardPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenFreightManifestClipboardPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "open_freight_manifest_clipboard"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFreightManifestClipboardPacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), OpenFreightManifestClipboardPacket::new);

    private final BlockPos pos;
    private final FreightStationLink stationLink;
    private final FreightManifest manifest;
    private final FreightStationCargoView cargoView;
    private final DeliveryOrderBoardView deliveryBoard;
    private final boolean canEdit;

    public OpenFreightManifestClipboardPacket(BlockPos pos, FreightStationLink stationLink, FreightManifest manifest,
                                              FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard, boolean canEdit) {
        this.pos = pos;
        this.stationLink = stationLink.copy();
        this.manifest = manifest.copy();
        this.cargoView = cargoView.copy();
        this.deliveryBoard = deliveryBoard;
        this.canEdit = canEdit;
    }

    public OpenFreightManifestClipboardPacket(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.stationLink = FreightStationLink.decode(buf);
        this.manifest = FreightManifest.decode(buf);
        this.cargoView = FreightStationCargoView.decode(buf);
        this.deliveryBoard = DeliveryOrderBoardView.decode(buf);
        this.canEdit = buf.readBoolean();
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        this.stationLink.encode(buf);
        this.manifest.encode(buf);
        this.cargoView.encode(buf);
        this.deliveryBoard.encode(buf);
        buf.writeBoolean(this.canEdit);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFreightManifestClipboardPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientScreenAccess.openFreightManifestClipboard(packet.pos, packet.stationLink, packet.manifest,
                packet.cargoView, packet.deliveryBoard, packet.canEdit));
    }
}
