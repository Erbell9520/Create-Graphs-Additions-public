package com.createstats.network;

import com.createstats.security.LockerMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// server opens it, holder is a uuid the client shouldn't decide on
public record OpenLockerModePacket(BlockPos pos, int mode) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenLockerModePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "open_locker_mode"));

    public static final StreamCodec<FriendlyByteBuf, OpenLockerModePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.pos());
                buf.writeVarInt(pkt.mode());
            }, buf -> new OpenLockerModePacket(buf.readBlockPos(), buf.readVarInt()));

    public LockerMode modeOf() {
        return LockerMode.fromOrdinal(this.mode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenLockerModePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.createstats.util.ClientScreenAccess.openLockerMode(packet));
    }
}
