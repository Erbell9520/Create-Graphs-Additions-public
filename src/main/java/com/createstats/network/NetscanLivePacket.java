package com.createstats.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// structure once, numbers often
public class NetscanLivePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NetscanLivePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "netscan_live"));

    public static final StreamCodec<FriendlyByteBuf, NetscanLivePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.encode(buf), NetscanLivePacket::new);

    private final BlockPos pos;
    private final int contentHash;
    private final float[] speeds;
    private final float[] stresses;
    private final float[] capacities;
    private final boolean[] overStressed;
    private final float[] history;

    public NetscanLivePacket(BlockPos pos, int contentHash, float[] speeds, float[] stresses,
                             float[] capacities, boolean[] overStressed, float[] history) {
        this.pos = pos;
        this.contentHash = contentHash;
        this.speeds = speeds;
        this.stresses = stresses;
        this.capacities = capacities;
        this.overStressed = overStressed;
        this.history = history == null ? new float[0] : history;
    }

    public NetscanLivePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.contentHash = buf.readInt();
        final int n = buf.readVarInt();
        this.speeds = new float[n];
        this.stresses = new float[n];
        this.capacities = new float[n];
        this.overStressed = new boolean[n];
        for (int i = 0; i < n; i++) {
            this.speeds[i] = buf.readFloat();
            this.stresses[i] = buf.readFloat();
            this.capacities[i] = buf.readFloat();
            this.overStressed[i] = buf.readBoolean();
        }
        final int h = buf.readVarInt();
        this.history = new float[h];
        for (int i = 0; i < h; i++) {
            this.history[i] = buf.readFloat();
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeInt(this.contentHash);
        buf.writeVarInt(this.speeds.length);
        for (int i = 0; i < this.speeds.length; i++) {
            buf.writeFloat(this.speeds[i]);
            buf.writeFloat(this.stresses[i]);
            buf.writeFloat(this.capacities[i]);
            buf.writeBoolean(this.overStressed[i]);
        }
        buf.writeVarInt(this.history.length);
        for (final float sample : this.history) {
            buf.writeFloat(sample);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NetscanLivePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.createstats.netscan.client.NetscanClient.acceptLive(
                packet.pos, packet.contentHash, packet.speeds, packet.stresses, packet.capacities,
                packet.overStressed, packet.history));
    }
}
