package com.createstats.flight.network;

import com.createstats.CreateStats;
import com.createstats.flight.FlightMode;
import com.createstats.flight.NoseAxis;
import com.createstats.flight.blockentity.FlightControlComputerBE;
import com.createstats.flight.menu.FlightControlComputerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C→S typed mode/parameter change request. Server validates: matching open menu,
 * sensor capability, finite + clamped doubles.
 */
public record FccModeChangePacket(
    int containerId,
    FlightMode mode,
    NoseAxis noseAxis,
    double targetAlt,
    double climbRate,
    double descendFloor
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FccModeChangePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "fcc_mode_change"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FccModeChangePacket> STREAM_CODEC =
        StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.containerId);
            buf.writeEnum(p.mode);
            buf.writeEnum(p.noseAxis);
            buf.writeDouble(p.targetAlt);
            buf.writeDouble(p.climbRate);
            buf.writeDouble(p.descendFloor);
        }, buf -> new FccModeChangePacket(
            buf.readVarInt(),
            buf.readEnum(FlightMode.class),
            buf.readEnum(NoseAxis.class),
            buf.readDouble(), buf.readDouble(), buf.readDouble()
        ));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FccModeChangePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof FlightControlComputerMenu menu)) return;
            if (menu.containerId != pkt.containerId) return;
            FlightControlComputerBE fcc = menu.getController();
            if (fcc.isRemoved()) return;
            if (!menu.stillValid(sp)) return;

            if (!Double.isFinite(pkt.targetAlt) || !Double.isFinite(pkt.climbRate) || !Double.isFinite(pkt.descendFloor)) return;
            double tAlt   = clamp(pkt.targetAlt,   -1024,  1024);
            double cRate  = clamp(pkt.climbRate,    -16,     16);
            double dFloor = clamp(pkt.descendFloor, -1024,  1024);

            fcc.requestModeChange(pkt.mode, pkt.noseAxis, tAlt, cRate, dFloor);
        });
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
