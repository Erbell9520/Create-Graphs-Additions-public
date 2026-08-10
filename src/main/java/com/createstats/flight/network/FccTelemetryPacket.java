package com.createstats.flight.network;

import com.createstats.CreateStats;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;

/** [1] */
public record FccTelemetryPacket(
    int containerId,
    long controllerPosLong,
    double altitude,
    float climbRate,           // blocks/second (positive = ascending)
    float headingDeg,          // [-180, +180], 0 = +Z (north-ish before nose remap)
    float pitchDeg,            // signed, 0 = level
    float rollDeg,             // signed, 0 = wings level
    float velX,
    float velZ,
    float velY,
    float autoTargetDistance,
    int propBoundCount,
    int propResolvedCount,
    int[] bindingRows,         // row index per binding; -1 if unknown
    int[] commandedRpm,        // length == propBoundCount
    int[] actualRpm,           // physical prop RPM, length == propBoundCount
    float[] thrustSigned,      // signed thrust scalar
    float[] thrustVertical,    // world-space Y component of thrust
    float[] thrustHorizontal,  // world-space horizontal thrust magnitude, signed by thrustSigned
    float[] sailPower,         // sail power scalar from prop actor
    float[] tiltAngleDeg,      // angle between thrust direction and local forward axis
    int gyroMask,              // bit i set when binding i is a gyroscopic bearing
    int[] vehicleWheelRpm,     // FL, FR, ML, MR, RL, RR runtime wheel RPM commands (commanded)
    int[] vehicleWheelSteer,   // FL, FR, ML, MR, RL, RR runtime steering signals [-15, 15]
    int[] actualWheelRpm,      // FL, FR, ML, MR, RL, RR physical wheel shaft RPM (kinetic getSpeed())
    int vehicleSteerModeOrdinal // live steering mode from the vehicle runtime: -1 idle, else FRONT=0 ALL_WHEEL=1 CRAB=2
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FccTelemetryPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "fcc_telemetry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FccTelemetryPacket> STREAM_CODEC =
        StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.containerId);
            buf.writeLong(p.controllerPosLong);
            buf.writeDouble(p.altitude);
            buf.writeFloat(p.climbRate);
            buf.writeFloat(p.headingDeg);
            buf.writeFloat(p.pitchDeg);
            buf.writeFloat(p.rollDeg);
            buf.writeFloat(p.velX);
            buf.writeFloat(p.velZ);
            buf.writeFloat(p.velY);
            buf.writeFloat(p.autoTargetDistance);
            buf.writeVarInt(p.propBoundCount);
            buf.writeVarInt(p.propResolvedCount);
            writeIntArray(buf, p.bindingRows);
            writeIntArray(buf, p.commandedRpm);
            writeIntArray(buf, p.actualRpm);
            writeFloatArray(buf, p.thrustSigned);
            writeFloatArray(buf, p.thrustVertical);
            writeFloatArray(buf, p.thrustHorizontal);
            writeFloatArray(buf, p.sailPower);
            writeFloatArray(buf, p.tiltAngleDeg);
            buf.writeVarInt(p.gyroMask);
            writeIntArray(buf, p.vehicleWheelRpm);
            writeIntArray(buf, p.vehicleWheelSteer);
            writeIntArray(buf, p.actualWheelRpm);
            buf.writeByte(p.vehicleSteerModeOrdinal);
        }, buf -> {
            int cid = buf.readVarInt();
            long controllerPosLong = buf.readLong();
            double alt = buf.readDouble();
            float climb = buf.readFloat();
            float head = buf.readFloat();
            float pitch = buf.readFloat();
            float roll  = buf.readFloat();
            float vx = buf.readFloat();
            float vz = buf.readFloat();
            float vy = buf.readFloat();
            float autoTargetDistance = buf.readFloat();
            int bound = buf.readVarInt();
            int resolved = buf.readVarInt();
            int[] rows = readIntArray(buf);
            int[] cmd = readIntArray(buf);
            int[] act = readIntArray(buf);
            float[] thrust = readFloatArray(buf);
            float[] vertical = readFloatArray(buf);
            float[] horizontal = readFloatArray(buf);
            float[] sail = readFloatArray(buf);
            float[] tilt = readFloatArray(buf);
            int gyroMask = buf.readVarInt();
            int[] vehicleWheelRpm = readIntArray(buf);
            int[] vehicleWheelSteer = readIntArray(buf);
            int[] actualWheelRpm = readIntArray(buf);
            int vehicleSteerModeOrdinal = buf.readByte();
            return new FccTelemetryPacket(
                cid, controllerPosLong, alt, climb, head, pitch, roll, vx, vz, vy, autoTargetDistance,
                bound, resolved, rows, cmd, act, thrust, vertical, horizontal, sail, tilt, gyroMask,
                vehicleWheelRpm, vehicleWheelSteer, actualWheelRpm, vehicleSteerModeOrdinal);
        });

    public FccTelemetryPacket {
        bindingRows = bindingRows == null ? new int[0] : bindingRows;
        commandedRpm = commandedRpm == null ? new int[0] : commandedRpm;
        actualRpm = actualRpm == null ? new int[0] : actualRpm;
        thrustSigned = thrustSigned == null ? new float[0] : thrustSigned;
        thrustVertical = thrustVertical == null ? new float[0] : thrustVertical;
        thrustHorizontal = thrustHorizontal == null ? new float[0] : thrustHorizontal;
        sailPower = sailPower == null ? new float[0] : sailPower;
        tiltAngleDeg = tiltAngleDeg == null ? new float[0] : tiltAngleDeg;
        vehicleWheelRpm = normalizeVehicleArray(vehicleWheelRpm);
        vehicleWheelSteer = normalizeVehicleArray(vehicleWheelSteer);
        actualWheelRpm = normalizeVehicleArray(actualWheelRpm);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FccTelemetryPacket pkt, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        ctx.enqueueWork(() -> invokeClient(pkt));
    }

    private static void invokeClient(FccTelemetryPacket pkt) {
        try {
            Class<?> c = Class.forName("com.createstats.flight.client.FccClientState");
            Method m = c.getMethod("applyTelemetry", FccTelemetryPacket.class);
            m.invoke(null, pkt);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FCC telemetry client hook failed", e);
        }
    }

    private static void writeIntArray(RegistryFriendlyByteBuf buf, int[] values) {
        int[] safe = values == null ? new int[0] : values;
        buf.writeVarInt(safe.length);
        for (int value : safe) {
            buf.writeVarInt(value);
        }
    }

    private static int[] readIntArray(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        int[] out = new int[size];
        for (int i = 0; i < size; i++) {
            out[i] = buf.readVarInt();
        }
        return out;
    }

    private static void writeFloatArray(RegistryFriendlyByteBuf buf, float[] values) {
        float[] safe = values == null ? new float[0] : values;
        buf.writeVarInt(safe.length);
        for (float value : safe) {
            buf.writeFloat(value);
        }
    }

    private static float[] readFloatArray(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        float[] out = new float[size];
        for (int i = 0; i < size; i++) {
            out[i] = buf.readFloat();
        }
        return out;
    }

    private static int[] normalizeVehicleArray(int[] values) {
        int[] out = new int[6];
        if (values == null) {
            return out;
        }
        System.arraycopy(values, 0, out, 0, Math.min(values.length, out.length));
        return out;
    }
}
