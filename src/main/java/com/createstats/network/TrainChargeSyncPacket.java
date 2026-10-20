package com.createstats.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class TrainChargeSyncPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TrainChargeSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "train_charge_sync"));
    public static final StreamCodec<FriendlyByteBuf, TrainChargeSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), TrainChargeSyncPacket::new);

    public record Summary(UUID train, int stored, int capacity, int netRate, int lowestCar, int lowestPct) {
    }

    private final Summary summary;

    public TrainChargeSyncPacket(Summary summary) {
        this.summary = summary;
    }

    public TrainChargeSyncPacket(FriendlyByteBuf buf) {
        this.summary = new Summary(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readInt(),
                buf.readVarInt() - 1, buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(summary.train());
        buf.writeVarInt(Math.max(0, summary.stored()));
        buf.writeVarInt(Math.max(0, summary.capacity()));
        buf.writeInt(summary.netRate());
        buf.writeVarInt(Math.max(-1, summary.lowestCar()) + 1);
        buf.writeVarInt(Math.max(0, summary.lowestPct()));
    }

    public Summary summary() {
        return summary;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrainChargeSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                com.createstats.client.train.ClientTrainCharge.accept(packet.summary());
            }
        });
    }
}
