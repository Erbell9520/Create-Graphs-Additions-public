package com.createstats.driverhelmet;

import com.createstats.CreateStats;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DriverTelemetryPacket(float speed, float latG, float lonG, float vertG, float peakG, float impactG,
                                    int impactAge, float tripImpact, float airNow, float lastAir, float pitch,
                                    float roll, double odometer, float trip, int tripTicks, float topSpeed,
                                    List<Wheel> wheels)
        implements CustomPacketPayload {
    public static final int MAX_WHEELS = 24;

    public record Wheel(float lat, float lon, float applied, float actual, float slip, float grip, float load,
                        boolean contact, boolean driven) {
    }

    public static final Type<DriverTelemetryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "driver_helmet_telemetry"));

    public static final StreamCodec<FriendlyByteBuf, DriverTelemetryPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.speed());
                buf.writeFloat(p.latG());
                buf.writeFloat(p.lonG());
                buf.writeFloat(p.vertG());
                buf.writeFloat(p.peakG());
                buf.writeFloat(p.impactG());
                buf.writeVarInt(p.impactAge() + 1);
                buf.writeFloat(p.tripImpact());
                buf.writeFloat(p.airNow());
                buf.writeFloat(p.lastAir());
                buf.writeFloat(p.pitch());
                buf.writeFloat(p.roll());
                buf.writeDouble(p.odometer());
                buf.writeFloat(p.trip());
                buf.writeVarInt(p.tripTicks());
                buf.writeFloat(p.topSpeed());
                final int n = Math.min(MAX_WHEELS, p.wheels().size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    final Wheel w = p.wheels().get(i);
                    buf.writeFloat(w.lat());
                    buf.writeFloat(w.lon());
                    buf.writeFloat(w.applied());
                    buf.writeFloat(w.actual());
                    buf.writeFloat(w.slip());
                    buf.writeFloat(w.grip());
                    buf.writeFloat(w.load());
                    buf.writeBoolean(w.contact());
                    buf.writeBoolean(w.driven());
                }
            },
            buf -> {
                final float speed = buf.readFloat();
                final float latG = buf.readFloat();
                final float lonG = buf.readFloat();
                final float vertG = buf.readFloat();
                final float peakG = buf.readFloat();
                final float impactG = buf.readFloat();
                final int impactAge = buf.readVarInt() - 1;
                final float tripImpact = buf.readFloat();
                final float airNow = buf.readFloat();
                final float lastAir = buf.readFloat();
                final float pitch = buf.readFloat();
                final float roll = buf.readFloat();
                final double odometer = buf.readDouble();
                final float trip = buf.readFloat();
                final int tripTicks = buf.readVarInt();
                final float top = buf.readFloat();
                final int n = Math.min(MAX_WHEELS, buf.readVarInt());
                final List<Wheel> wheels = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    wheels.add(new Wheel(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean()));
                }
                return new DriverTelemetryPacket(speed, latG, lonG, vertG, peakG, impactG, impactAge, tripImpact,
                        airNow, lastAir, pitch, roll, odometer, trip, tripTicks, top, List.copyOf(wheels));
            });

    @Nullable
    private static volatile DriverTelemetryPacket latest;
    private static volatile long latestNanos;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DriverTelemetryPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            latest = packet;
            latestNanos = System.nanoTime();
        });
    }

    @Nullable
    public static DriverTelemetryPacket current() {
        final DriverTelemetryPacket p = latest;
        return p != null && System.nanoTime() - latestNanos < 1_000_000_000L ? p : null;
    }
}
