package com.createstats.network;

import com.createstats.security.LockerCode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// server decides now. the client used to open it inside isClientSide
public record OpenSecurityPanelPacket(BlockPos pos, boolean keypad, int verdict,
                                      int lockoutSeconds, String house, int purpose)
        implements CustomPacketPayload {
    public static final int PLAIN = 0;

    public static final int JOIN = 1;

    public static final int SEAL_ARM = 2;

    public static final int SEAL_STAND_DOWN = 3;

    public static final int RELAY = 4;

    public static final int SHUT = -2;

    public static OpenSecurityPanelPacket open(BlockPos pos) {
        return new OpenSecurityPanelPacket(pos, false, -1, 0, "", PLAIN);
    }

    public static OpenSecurityPanelPacket shut(BlockPos pos) {
        return new OpenSecurityPanelPacket(pos, false, SHUT, 0, "", PLAIN);
    }

    public static OpenSecurityPanelPacket ask(BlockPos pos, String house) {
        return new OpenSecurityPanelPacket(pos, true, -1, 0, house, PLAIN);
    }

    public static OpenSecurityPanelPacket askAtRelay(BlockPos pos, String house) {
        return new OpenSecurityPanelPacket(pos, true, -1, 0, house, RELAY);
    }

    public static OpenSecurityPanelPacket askToJoin(BlockPos pos, String house) {
        return new OpenSecurityPanelPacket(pos, true, -1, 0, house, JOIN);
    }

    public static OpenSecurityPanelPacket askToSeal(BlockPos pos, String house, boolean arming) {
        return new OpenSecurityPanelPacket(pos, true, -1, 0, house,
                arming ? SEAL_ARM : SEAL_STAND_DOWN);
    }

    public static OpenSecurityPanelPacket verdict(BlockPos pos, LockerCode.Result result,
                                                  int lockoutSeconds, String house, int purpose) {
        return new OpenSecurityPanelPacket(pos, true, result.ordinal(), lockoutSeconds, house,
                purpose);
    }

    public static final CustomPacketPayload.Type<OpenSecurityPanelPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "open_security_panel"));

    public static final StreamCodec<FriendlyByteBuf, OpenSecurityPanelPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {
                buf.writeBlockPos(pkt.pos());
                buf.writeBoolean(pkt.keypad());

                buf.writeVarInt(pkt.verdict() - SHUT);
                buf.writeVarInt(pkt.lockoutSeconds());
                buf.writeUtf(com.createstats.security.SecurityLog.clip(pkt.house(), 32), 32);

                buf.writeVarInt(pkt.purpose());
            }, buf -> new OpenSecurityPanelPacket(buf.readBlockPos(), buf.readBoolean(),
                    buf.readVarInt() + SHUT, buf.readVarInt(), buf.readUtf(32), buf.readVarInt()));

    public boolean isShut() {
        return this.verdict == SHUT;
    }

    public boolean isJoin() {
        return this.purpose == JOIN;
    }

    public boolean isSeal() {
        return this.purpose == SEAL_ARM || this.purpose == SEAL_STAND_DOWN;
    }

    public LockerCode.Result resultOf() {
        final LockerCode.Result[] all = LockerCode.Result.values();
        return this.verdict >= 0 && this.verdict < all.length ? all[this.verdict] : null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenSecurityPanelPacket packet, IPayloadContext context) {
        context.enqueueWork(
                () -> com.createstats.util.ClientScreenAccess.openSecurityPanel(packet));
    }
}
