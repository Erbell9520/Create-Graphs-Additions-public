package com.createstats.flight.network;

import com.createstats.CreateStats;
import com.createstats.flight.SensorRequirement;
import com.createstats.flight.blockentity.FlightControlComputerBE;
import com.createstats.flight.menu.FlightControlComputerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

public record FccSensorBindPacket(
    int containerId,
    @Nullable SensorRequirement sensor,
    @Nullable BlockPos pos
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FccSensorBindPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "fcc_sensor_bind"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FccSensorBindPacket> STREAM_CODEC =
        StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.containerId);
            buf.writeBoolean(p.sensor != null);
            if (p.sensor != null) buf.writeEnum(p.sensor);
            buf.writeBoolean(p.pos != null);
            if (p.pos != null) buf.writeBlockPos(p.pos);
        }, buf -> new FccSensorBindPacket(
            buf.readVarInt(),
            buf.readBoolean() ? buf.readEnum(SensorRequirement.class) : null,
            buf.readBoolean() ? buf.readBlockPos() : null
        ));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FccSensorBindPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof FlightControlComputerMenu menu)) return;
            if (menu.containerId != pkt.containerId) return;
            FlightControlComputerBE fcc = menu.getController();
            if (fcc.isRemoved()) return;
            if (!menu.stillValid(sp)) return;

            SensorRequirement req = pkt.sensor;
            BlockPos pos = pkt.pos;

            if (req == null) {
                var sensors = fcc.sensors();
                if (sensors != null) {
                    sensors.forceScanNow();
                    sensors.maybeScan(sp.serverLevel().getGameTime());
                }
                fcc.broadcastSnapshot();
                return;
            }

            if (pos != null && (sp.level() == null || !sp.level().isLoaded(pos))) return;

            switch (req) {
                case GIMBAL    -> fcc.setManualGimbalPos(pos);
                case ALTITUDE  -> fcc.setManualAltitudePos(pos);
                case VELOCITY  -> fcc.setManualVelocityPos(pos);
                case NAV_TABLE -> fcc.setManualNavTablePos(pos);
            }
        });
    }
}
