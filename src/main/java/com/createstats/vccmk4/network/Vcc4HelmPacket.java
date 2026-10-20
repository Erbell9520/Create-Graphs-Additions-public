package com.createstats.vccmk4.network;

import com.createstats.CreateStats;
import com.createstats.vccmk4.Vcc4ClientBridge;
import com.createstats.vccmk4.core.KeyIds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record Vcc4HelmPacket(BlockPos pos, boolean helm, int[] watch) implements CustomPacketPayload {
    public static final int MAX_WATCH = 64;

    public static final Type<Vcc4HelmPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vcc4_helm"));

    public static final StreamCodec<FriendlyByteBuf, Vcc4HelmPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeBoolean(packet.helm());
                buf.writeVarIntArray(sane(packet.watch()));
            },
            buf -> new Vcc4HelmPacket(buf.readBlockPos(), buf.readBoolean(), sane(buf.readVarIntArray(MAX_WATCH))));

    private static int[] sane(int[] ids) {
        if (ids == null) return new int[0];
        return java.util.Arrays.stream(ids).filter(id -> id != KeyIds.NONE && KeyIds.valid(id)).distinct()
                .limit(MAX_WATCH).toArray();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(Vcc4HelmPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> Vcc4ClientBridge.helm(packet));
    }
}
