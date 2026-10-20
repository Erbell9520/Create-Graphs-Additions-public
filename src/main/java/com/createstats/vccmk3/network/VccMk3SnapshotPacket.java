package com.createstats.vccmk3.network;

import com.createstats.CreateStats;
import com.createstats.vccmk3.avionics.AvionicsChannel;
import com.createstats.vccmk3.avionics.AvionicsState;
import com.createstats.vccmk3.avionics.SensorSource;
import com.createstats.vccmk3.client.VccMk3ClientState;
import com.createstats.vccmk3.hardware.DeviceCategory;
import com.createstats.vccmk3.hardware.LinkHealth;
import com.createstats.vccmk3.hardware.LinkSlot;
import com.createstats.vccmk3.model.ControlAxis;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// one packet so half the screen can't disagree with the other half
public record VccMk3SnapshotPacket(
        BlockPos controllerPos,

        int facingOrdinal,
        boolean armed,
        boolean flipGuardActive,
        float flipGuardDegrees,
        String craftName,
        int linkRowMask,
        int linkRxMask,
        String notice,
        AutoState auto,
        List<SlotEntry> slots,
        List<DeviceEntry> devices,
        List<AlertEntry> alerts,
        byte[] sensorStates,
        float[] avionicsValues,
        byte[] avionicsSources,

        int airframeOrdinal,

        List<AssignEntry> assignments,

        RigKinetics kinetics,

        float[] axisAuthority,

        float[] axisDemand,

        List<AxisEntry> axisReports,

        List<SlotReport> slotReports,

        List<BusEntry> bus,

        Coupling coupling
) implements CustomPacketPayload {
    public record AssignEntry(UUID deviceId, String slotKey) {}

    public record RigKinetics(float massTonnes, float thrustToWeight,
                              float rollInertia, float pitchInertia, float yawInertia,
                              float comForward, float comUp,
                              int commandedRpm, int appliedRpm, int governorCount,
                              float stressUsed, float stressCapacity, float networkRpm) {
        public static final RigKinetics UNKNOWN = new RigKinetics(
                Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
                0, 0, 0, Float.NaN, Float.NaN, Float.NaN);
    }

    public static final int AXIS_COUNT = 6;

    public record SlotEntry(int row, byte health, byte category, boolean hasDevice, String detail) {}

    public record DeviceEntry(UUID id, String label, byte category, boolean enabled,
                              int overrideRpm, int actualRpm,
                              int relX, int relY, int relZ, int cardRow,
                              String note) {}

    public record AxisEntry(String note, String flag) {
        public static final AxisEntry NONE = new AxisEntry("", "");
    }

    public static final byte SLOT_EMPTY = 0;
    public static final byte SLOT_OK = 1;
    public static final byte SLOT_FAULT = 2;

    public record SlotReport(String slotKey, byte state, float gain, float arm,
                             int deviceCount, float rpmFraction, float angleDeg, String note) {}

    public record BusEntry(String label, int commanded, int applied) {}

    public record Coupling(String title, String detail, boolean clean) {
        public static final Coupling CLEAN = new Coupling("Mix is clean", "No axis pushes another.", true);
    }

    public static final int POSITION_RANGE = 127;

    public static int clampPosition(int value) {
        return Math.max(-POSITION_RANGE, Math.min(POSITION_RANGE, value));
    }

    public record AutoState(boolean engaged, boolean hasFix, boolean routeSource,
                            int waypointIndex, int waypointCount,
                            float distance, float bearingDeg, String status) {
        public AutoState {
            if (status == null) status = "";
        }

        public static final AutoState OFF =
                new AutoState(false, false, false, 0, 0, Float.NaN, Float.NaN, "Autopilot off");
    }

    public record AlertEntry(byte level, String code, String message) {}

    public static final byte SENSOR_NONE = 0;
    public static final byte SENSOR_AUTO = 1;
    public static final byte SENSOR_MANUAL = 2;
    public static final byte SENSOR_BROKEN = 3;
    public static final int SENSOR_COUNT = 6;

    public static final int CH_ALTITUDE = 0;
    public static final int CH_CALIBRATED = 1;
    public static final int CH_PRESSURE = 2;
    public static final int CH_PITCH = 3;
    public static final int CH_ROLL = 4;
    public static final int CH_HEADING = 5;
    public static final int CH_GROUND_SPEED = 6;
    public static final int CH_AIRSPEED = 7;
    public static final int CH_CLIMB = 8;
    public static final int CH_WIND_SPEED = 9;
    public static final int CH_WIND_FROM = 10;
    public static final int CH_TERRAIN = 11;
    public static final int CHANNEL_COUNT = 12;

    public static final Type<VccMk3SnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vcc_mk3_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VccMk3SnapshotPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {
                buf.writeBlockPos(p.controllerPos);
                buf.writeByte(p.facingOrdinal);
                buf.writeBoolean(p.armed);
                buf.writeBoolean(p.flipGuardActive);
                buf.writeFloat(p.flipGuardDegrees);
                buf.writeUtf(p.craftName, 64);
                buf.writeVarInt(p.linkRowMask);
                buf.writeVarInt(p.linkRxMask);
                buf.writeUtf(p.notice, 128);
                buf.writeBoolean(p.auto.engaged());
                buf.writeBoolean(p.auto.hasFix());
                buf.writeBoolean(p.auto.routeSource());
                buf.writeVarInt(p.auto.waypointIndex());
                buf.writeVarInt(p.auto.waypointCount());
                buf.writeFloat(p.auto.distance());
                buf.writeFloat(p.auto.bearingDeg());
                buf.writeUtf(p.auto.status(), 96);

                buf.writeVarInt(p.slots.size());
                for (SlotEntry e : p.slots) {
                    buf.writeVarInt(e.row());
                    buf.writeByte(e.health());
                    buf.writeByte(e.category());
                    buf.writeBoolean(e.hasDevice());
                    buf.writeUtf(e.detail(), 64);
                }

                buf.writeVarInt(p.devices.size());
                for (DeviceEntry e : p.devices) {
                    buf.writeUUID(e.id());
                    buf.writeUtf(e.label(), 32);
                    buf.writeByte(e.category());
                    buf.writeBoolean(e.enabled());
                    buf.writeVarInt(e.overrideRpm() + 1);
                    buf.writeVarInt(e.actualRpm() + 512);
                    buf.writeByte(clampPosition(e.relX()));
                    buf.writeByte(clampPosition(e.relY()));
                    buf.writeByte(clampPosition(e.relZ()));
                    buf.writeByte(e.cardRow());
                    buf.writeUtf(e.note(), 128);
                }

                buf.writeVarInt(p.alerts.size());
                for (AlertEntry e : p.alerts) {
                    buf.writeByte(e.level());
                    buf.writeUtf(e.code(), 8);
                    buf.writeUtf(e.message(), 128);
                }

                for (int i = 0; i < SENSOR_COUNT; i++) buf.writeByte(p.sensorStates[i]);
                for (int i = 0; i < CHANNEL_COUNT; i++) {
                    buf.writeFloat(p.avionicsValues[i]);
                    buf.writeByte(p.avionicsSources[i]);
                }

                buf.writeVarInt(p.airframeOrdinal);
                buf.writeVarInt(p.assignments.size());
                for (AssignEntry e : p.assignments) {
                    buf.writeUUID(e.deviceId());
                    buf.writeUtf(e.slotKey(), 32);
                }
                RigKinetics k = p.kinetics;
                buf.writeFloat(k.massTonnes());
                buf.writeFloat(k.thrustToWeight());
                buf.writeFloat(k.rollInertia());
                buf.writeFloat(k.pitchInertia());
                buf.writeFloat(k.yawInertia());
                buf.writeFloat(k.comForward());
                buf.writeFloat(k.comUp());
                buf.writeVarInt(k.commandedRpm());
                buf.writeVarInt(k.appliedRpm());
                buf.writeVarInt(k.governorCount());
                buf.writeFloat(k.stressUsed());
                buf.writeFloat(k.stressCapacity());
                buf.writeFloat(k.networkRpm());

                for (int i = 0; i < AXIS_COUNT; i++) buf.writeFloat(p.axisAuthority[i]);
                for (int i = 0; i < AXIS_COUNT; i++) buf.writeFloat(p.axisDemand[i]);
                for (int i = 0; i < AXIS_COUNT; i++) {
                    AxisEntry e = i < p.axisReports.size() ? p.axisReports.get(i) : AxisEntry.NONE;
                    buf.writeUtf(e.note(), 96);
                    buf.writeUtf(e.flag(), 24);
                }

                buf.writeVarInt(p.slotReports.size());
                for (SlotReport e : p.slotReports) {
                    buf.writeUtf(e.slotKey(), 32);
                    buf.writeByte(e.state());
                    buf.writeFloat(e.gain());
                    buf.writeFloat(e.arm());
                    buf.writeVarInt(e.deviceCount());
                    buf.writeFloat(e.rpmFraction());
                    buf.writeFloat(e.angleDeg());
                    buf.writeUtf(e.note(), 128);
                }

                buf.writeVarInt(p.bus.size());
                for (BusEntry e : p.bus) {
                    buf.writeUtf(e.label(), 24);
                    buf.writeVarInt(e.commanded() + 512);
                    buf.writeVarInt(e.applied() + 512);
                }

                buf.writeUtf(p.coupling.title(), 64);
                buf.writeUtf(p.coupling.detail(), 128);
                buf.writeBoolean(p.coupling.clean());
            }, buf -> {
                BlockPos pos = buf.readBlockPos();
                int facing = buf.readByte();
                boolean armed = buf.readBoolean();
                boolean flip = buf.readBoolean();
                float guardDegrees = buf.readFloat();
                String name = buf.readUtf(64);
                int mask = buf.readVarInt();
                int rxMask = buf.readVarInt();
                String notice = buf.readUtf(128);
                AutoState auto = new AutoState(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                        buf.readVarInt(), buf.readVarInt(),
                        buf.readFloat(), buf.readFloat(), buf.readUtf(96));

                int slotCount = buf.readVarInt();
                List<SlotEntry> slots = new ArrayList<>(slotCount);
                for (int i = 0; i < slotCount; i++) {
                    slots.add(new SlotEntry(buf.readVarInt(), buf.readByte(), buf.readByte(),
                            buf.readBoolean(), buf.readUtf(64)));
                }

                int deviceCount = buf.readVarInt();
                List<DeviceEntry> devices = new ArrayList<>(deviceCount);
                for (int i = 0; i < deviceCount; i++) {
                    devices.add(new DeviceEntry(buf.readUUID(), buf.readUtf(32), buf.readByte(),
                            buf.readBoolean(), buf.readVarInt() - 1, buf.readVarInt() - 512,
                            buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(),
                            buf.readUtf(128)));
                }

                int alertCount = buf.readVarInt();
                List<AlertEntry> alerts = new ArrayList<>(alertCount);
                for (int i = 0; i < alertCount; i++) {
                    alerts.add(new AlertEntry(buf.readByte(), buf.readUtf(8), buf.readUtf(128)));
                }

                byte[] sensorStates = new byte[SENSOR_COUNT];
                for (int i = 0; i < SENSOR_COUNT; i++) sensorStates[i] = buf.readByte();

                float[] values = new float[CHANNEL_COUNT];
                byte[] sources = new byte[CHANNEL_COUNT];
                for (int i = 0; i < CHANNEL_COUNT; i++) {
                    values[i] = buf.readFloat();
                    sources[i] = buf.readByte();
                }
                int airframe = buf.readVarInt();
                int assignCount = buf.readVarInt();
                List<AssignEntry> assignments = new ArrayList<>(assignCount);
                for (int i = 0; i < assignCount; i++) {
                    assignments.add(new AssignEntry(buf.readUUID(), buf.readUtf(32)));
                }
                RigKinetics kinetics = new RigKinetics(buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readFloat(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat());

                float[] authority = new float[AXIS_COUNT];
                for (int i = 0; i < AXIS_COUNT; i++) authority[i] = buf.readFloat();
                float[] demand = new float[AXIS_COUNT];
                for (int i = 0; i < AXIS_COUNT; i++) demand[i] = buf.readFloat();
                List<AxisEntry> axisReports = new ArrayList<>(AXIS_COUNT);
                for (int i = 0; i < AXIS_COUNT; i++) {
                    axisReports.add(new AxisEntry(buf.readUtf(96), buf.readUtf(24)));
                }

                int reportCount = buf.readVarInt();
                List<SlotReport> slotReports = new ArrayList<>(reportCount);
                for (int i = 0; i < reportCount; i++) {
                    slotReports.add(new SlotReport(buf.readUtf(32), buf.readByte(), buf.readFloat(),
                            buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                            buf.readUtf(128)));
                }

                int busCount = buf.readVarInt();
                List<BusEntry> bus = new ArrayList<>(busCount);
                for (int i = 0; i < busCount; i++) {
                    bus.add(new BusEntry(buf.readUtf(24), buf.readVarInt() - 512, buf.readVarInt() - 512));
                }

                Coupling coupling = new Coupling(buf.readUtf(64), buf.readUtf(128), buf.readBoolean());

                return new VccMk3SnapshotPacket(pos, facing, armed, flip, guardDegrees, name, mask,
                        rxMask, notice, auto, slots, devices, alerts, sensorStates, values, sources,
                        airframe, assignments, kinetics, authority, demand, axisReports,
                        slotReports, bus, coupling);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static float[] packValues(AvionicsState state) {
        AvionicsChannel[] channels = channelsOf(state);
        float[] values = new float[CHANNEL_COUNT];
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            values[i] = channels[i].present() ? (float) channels[i].value() : Float.NaN;
        }
        return values;
    }

    public static byte[] packSources(AvionicsState state) {
        AvionicsChannel[] channels = channelsOf(state);
        byte[] sources = new byte[CHANNEL_COUNT];
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            sources[i] = (byte) channels[i].source().ordinal();
        }
        return sources;
    }

    private static AvionicsChannel[] channelsOf(AvionicsState state) {
        return new AvionicsChannel[] {
                state.altitude(), state.calibratedHeight(), state.airPressure(),
                state.pitchDeg(), state.rollDeg(), state.headingDeg(),
                state.groundSpeed(), state.airspeed(), state.climbRate(),
                state.windSpeed(), state.windFromDeg(), state.terrainClearance()
        };
    }

    public AvionicsChannel channel(int index) {
        if (index < 0 || index >= CHANNEL_COUNT) return AvionicsChannel.NONE;
        SensorSource source = sourceOf(avionicsSources[index]);
        if (source == SensorSource.NONE || Float.isNaN(avionicsValues[index])) return AvionicsChannel.NONE;
        return new AvionicsChannel(avionicsValues[index], source);
    }

    private static SensorSource sourceOf(byte ordinal) {
        SensorSource[] all = SensorSource.values();
        return (ordinal >= 0 && ordinal < all.length) ? all[ordinal] : SensorSource.NONE;
    }

    public static LinkHealth healthOf(SlotEntry entry) {
        LinkHealth[] all = LinkHealth.values();
        return (entry.health() >= 0 && entry.health() < all.length) ? all[entry.health()] : LinkHealth.EMPTY;
    }

    public static DeviceCategory categoryOf(SlotEntry entry) {
        DeviceCategory[] all = DeviceCategory.values();
        return (entry.category() >= 0 && entry.category() < all.length) ? all[entry.category()] : null;
    }

    public static LinkSlot toLinkSlot(SlotEntry entry) {
        return new LinkSlot(entry.row(), healthOf(entry), categoryOf(entry), null, null,
                entry.hasDevice() ? new UUID(0L, entry.row() + 1L) : null, entry.detail());
    }

    public AlertEntry topAlert() {
        return alerts.isEmpty()
                ? new AlertEntry((byte) com.createstats.vccmk3.alert.AlertLevel.OK.ordinal(), "RDY", "All systems nominal")
                : alerts.get(0);
    }

    public float demand(@Nullable ControlAxis axis) {
        if (axis == null) return 0f;
        int index = axis.ordinal();
        return index < axisDemand.length ? axisDemand[index] : 0f;
    }

    public float authority(@Nullable ControlAxis axis) {
        if (axis == null) return Float.NaN;
        int index = axis.ordinal();
        return index < axisAuthority.length ? axisAuthority[index] : Float.NaN;
    }

    public AxisEntry axisReport(@Nullable ControlAxis axis) {
        if (axis == null) return AxisEntry.NONE;
        int index = axis.ordinal();
        return index < axisReports.size() ? axisReports.get(index) : AxisEntry.NONE;
    }

    @Nullable
    public SlotReport slotReport(@Nullable String slotKey) {
        if (slotKey == null) return null;
        for (SlotReport report : slotReports) {
            if (report.slotKey().equals(slotKey)) return report;
        }
        return null;
    }

    public boolean axisUsable(@Nullable ControlAxis axis) {
        float value = authority(axis);
        return !Float.isNaN(value) && value > 0f;
    }

    public static void handle(VccMk3SnapshotPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> VccMk3ClientState.accept(packet));
    }
}
