package com.createstats.network;

import com.createstats.train.smart.ClientRailNetwork;
import com.createstats.train.smart.RailSnapshot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RailSnapshotPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RailSnapshotPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "rail_snapshot"));

    public static final StreamCodec<FriendlyByteBuf, RailSnapshotPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), RailSnapshotPacket::new);

    private final boolean includesTopology;
    private final CompoundTag payload;

    public RailSnapshotPacket(RailSnapshot snapshot, boolean includesTopology) {
        this.includesTopology = includesTopology;

        this.payload = snapshot.write(includesTopology);
    }

    public RailSnapshotPacket(FriendlyByteBuf buf) {
        this.includesTopology = buf.readBoolean();
        this.payload = buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.includesTopology);
        buf.writeNbt(this.payload);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RailSnapshotPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (packet.payload == null) {
                return;
            }
            ClientRailNetwork.accept(RailSnapshot.read(packet.payload), packet.includesTopology);
        });
    }
}
