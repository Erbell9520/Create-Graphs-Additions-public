package com.createstats.network;

import com.createstats.blockentity.FreightManifestClipboardBE;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightManifestStationResolver;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RequestFreightManifestClipboardStatePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestFreightManifestClipboardStatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "request_freight_manifest_clipboard_state"));

    public static final StreamCodec<FriendlyByteBuf, RequestFreightManifestClipboardStatePacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> pkt.encode(buf), RequestFreightManifestClipboardStatePacket::new);

    private final BlockPos pos;

    public RequestFreightManifestClipboardStatePacket(BlockPos pos) {
        this.pos = pos;
    }

    public RequestFreightManifestClipboardStatePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestFreightManifestClipboardStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(packet.pos.getX() + 0.5, packet.pos.getY() + 0.5, packet.pos.getZ() + 0.5) > 64.0) {
                return;
            }
            BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof FreightManifestClipboardBE clipboard)) {
                return;
            }
            FreightStationLink link = clipboard.getStationLink();
            FreightManifestStationResolver.StationView view = FreightManifestStationResolver.captureStationView(player.server, link);
            PacketDistributor.sendToPlayer(player,
                new FreightManifestClipboardStatePacket(packet.pos, view.manifest(), view.cargoView(), view.deliveryBoard()));
        });
    }
}
