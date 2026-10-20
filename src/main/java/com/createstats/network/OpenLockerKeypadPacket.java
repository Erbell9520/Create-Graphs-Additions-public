package com.createstats.network;

import com.createstats.security.LockerCode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenLockerKeypadPacket(BlockPos pos, int verdict, int lockoutSeconds, boolean claim)
        implements CustomPacketPayload {
    public static OpenLockerKeypadPacket fresh(BlockPos pos) {
        return new OpenLockerKeypadPacket(pos, -1, 0, false);
    }

    public static OpenLockerKeypadPacket claim(BlockPos pos) {
        return new OpenLockerKeypadPacket(pos, -1, 0, true);
    }

    public static final CustomPacketPayload.Type<OpenLockerKeypadPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "open_locker_keypad"));

    public static final StreamCodec<FriendlyByteBuf, OpenLockerKeypadPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.pos());
                buf.writeVarInt(pkt.verdict() + 1);
                buf.writeVarInt(pkt.lockoutSeconds());
                buf.writeBoolean(pkt.claim());
            }, buf -> new OpenLockerKeypadPacket(buf.readBlockPos(), buf.readVarInt() - 1,
                    buf.readVarInt(), buf.readBoolean()));

    public LockerCode.Result resultOf() {
        final LockerCode.Result[] all = LockerCode.Result.values();
        return this.verdict >= 0 && this.verdict < all.length ? all[this.verdict] : null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenLockerKeypadPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.createstats.util.ClientScreenAccess.openLockerKeypad(packet));
    }
}
