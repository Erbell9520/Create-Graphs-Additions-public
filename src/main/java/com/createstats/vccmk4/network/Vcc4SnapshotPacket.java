package com.createstats.vccmk4.network;

import com.createstats.CreateStats;
import com.createstats.vccmk4.Vcc4ClientBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// the server opens it
public record Vcc4SnapshotPacket(BlockPos pos, boolean open, Vcc4Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<Vcc4SnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vcc4_snapshot"));

    public static final StreamCodec<FriendlyByteBuf, Vcc4SnapshotPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeBoolean(packet.open());
                Vcc4Snapshot.write(buf, packet.snapshot());
            },
            buf -> new Vcc4SnapshotPacket(buf.readBlockPos(), buf.readBoolean(), Vcc4Snapshot.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(Vcc4SnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Vcc4ClientBridge.snapshot(packet));
    }
}
