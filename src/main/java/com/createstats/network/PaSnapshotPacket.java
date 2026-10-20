package com.createstats.network;

import com.createstats.pa.PaSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PaSnapshotPacket(PaSnapshot snapshot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PaSnapshotPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "pa_snapshot"));

    public static final StreamCodec<FriendlyByteBuf, PaSnapshotPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.snapshot().encode(buf),
                    buf -> new PaSnapshotPacket(PaSnapshot.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PaSnapshotPacket pkt, IPayloadContext context) {
        context.enqueueWork(() -> com.createstats.gui.PaConsoleScreen.accept(pkt.snapshot()));
    }
}
