package com.createstats.network;

import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.StationTrainRoster;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class DispatchBoardStatePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DispatchBoardStatePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "dispatch_board_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DispatchBoardStatePacket> STREAM_CODEC = StreamCodec.of((buf, pkt) -> pkt.encode(buf), DispatchBoardStatePacket::new);

    private final BlockPos pos;
    private final FreightManifest manifest;
    private final FreightStationCargoView cargoView;
    private final DeliveryOrderBoardView deliveryBoard;
    private final StationTrainRoster stationRoster;

    public DispatchBoardStatePacket(BlockPos pos, FreightManifest manifest, FreightStationCargoView cargoView,
                                    DeliveryOrderBoardView deliveryBoard, StationTrainRoster stationRoster) {
        this.pos = pos;
        this.manifest = manifest.copy();
        this.cargoView = cargoView.copy();
        this.deliveryBoard = deliveryBoard;
        this.stationRoster = stationRoster.copy();
    }

    public DispatchBoardStatePacket(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.manifest = FreightManifest.decode(buf);
        this.cargoView = FreightStationCargoView.decode(buf);
        this.deliveryBoard = DeliveryOrderBoardView.decode(buf);
        this.stationRoster = StationTrainRoster.decode(buf);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        this.manifest.encode(buf);
        this.cargoView.encode(buf);
        this.deliveryBoard.encode(buf);
        this.stationRoster.encode(buf);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DispatchBoardStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> DispatchBoardClientHooks.handleDispatchBoardState(packet.pos, packet.manifest,
                packet.cargoView, packet.deliveryBoard, packet.stationRoster));
    }
}
