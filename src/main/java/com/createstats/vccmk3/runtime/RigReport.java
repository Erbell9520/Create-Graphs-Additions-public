package com.createstats.vccmk3.runtime;

import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.util.KineticNetworkMetrics;
import com.createstats.vccmk3.hardware.DeviceCategory;
import com.createstats.vccmk3.model.Airframe;
import com.createstats.vccmk3.model.ControlAxis;
import com.createstats.vccmk3.model.SlotSpec;
import com.createstats.vccmk3.model.VccDevice;
import com.createstats.vccmk3.network.VccMk3SnapshotPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RigReport {
    private static final double FULL_SCALE_FORCE = 4.0;
    private static final double FULL_SCALE_MOMENT = 40.0;

    private RigReport() {}

    public record Result(List<VccMk3SnapshotPacket.AssignEntry> assignments,
                         VccMk3SnapshotPacket.RigKinetics kinetics,
                         float[] authority,
                         List<VccMk3SnapshotPacket.AxisEntry> axisReports,
                         List<VccMk3SnapshotPacket.SlotReport> slotReports,
                         List<VccMk3SnapshotPacket.BusEntry> bus,
                         VccMk3SnapshotPacket.Coupling coupling,
                         Map<UUID, String> deviceNotes) {}

    public static Result build(@Nullable Level level,
                               @Nullable BlockEntity controller,
                               Airframe airframe,
                               Map<UUID, String> slotAssignments,
                               List<VccDevice> devices,
                               CraftFrame frame,
                               RigSolution solution) {
        List<VccMk3SnapshotPacket.AssignEntry> assignments = new ArrayList<>(slotAssignments.size());
        for (Map.Entry<UUID, String> entry : slotAssignments.entrySet()) {
            assignments.add(new VccMk3SnapshotPacket.AssignEntry(entry.getKey(), entry.getValue()));
        }

        Map<UUID, VccDevice> byId = new HashMap<>();
        for (VccDevice device : devices) byId.put(device.id(), device);

        Map<UUID, String> notes = new LinkedHashMap<>();
        for (VccDevice device : devices) {
            String note = deviceNote(level, device, solution, slotAssignments.get(device.id()), airframe);
            if (!note.isEmpty()) notes.put(device.id(), note);
        }

        return new Result(assignments,
                kinetics(level, controller, frame, devices),
                authority(airframe, solution),
                axisReports(airframe, solution),
                slotReports(level, airframe, slotAssignments, byId, frame, solution),
                bus(level, airframe, slotAssignments, byId, devices),
                coupling(solution),
                notes);
    }

    private static String deviceNote(@Nullable Level level, VccDevice device, RigSolution solution,
                                     @Nullable String slotKey, Airframe airframe) {
        if (!device.enabled()) return "Switched off in the hardware list.";
        if (device.awaitingGovernor()) {
            return "No Governor Card in this bay, so nothing can drive it.";
        }
        if (slotKey == null) {
            return "Spare. Click it, then click where it goes on the craft.";
        }
        SlotSpec spec = airframe.slot(slotKey);
        if (spec == null) {
            return "Sits in a slot this airframe does not have. Fit it somewhere else.";
        }
        if (!spec.accepts(device.category())) {
            return "A " + device.category().label().toLowerCase(Locale.ROOT)
                    + " cannot do the job of " + spec.label().toLowerCase(Locale.ROOT) + ".";
        }

        if (device.category() == DeviceCategory.CONTROL_SURFACE && level != null) {
            BlockEntity be = SableBridge.getBlockEntityOnContraption(level, device.actuatorPos());
            if (be != null && SableBridge.matchesSwivelBearing(be) && !SableBridge.isSwivelAssembled(be)) {
                return "Nothing is attached to this bearing, so it moves no air. Build a surface on it.";
            }
        }

        String geometryNote = device.geometry().note();
        if (!device.geometry().isUsable() && device.category() != DeviceCategory.SIGNAL_LINK
                && !geometryNote.isEmpty()) {
            return geometryNote;
        }

        return solution.faultOf(device.id());
    }

    private static VccMk3SnapshotPacket.RigKinetics kinetics(@Nullable Level level,
                                                             @Nullable BlockEntity controller,
                                                             CraftFrame frame,
                                                             List<VccDevice> devices) {
        double mass = controller == null ? Double.NaN : SableBridge.mass(controller);
        double[] inertia = controller == null ? null : SableBridge.inertiaTensor(controller);

        float rollI = inertia != null ? (float) inertia[0] : Float.NaN;
        float pitchI = inertia != null ? (float) inertia[4] : Float.NaN;
        float yawI = inertia != null ? (float) inertia[8] : Float.NaN;

        float comForward = Float.NaN;
        float comUp = Float.NaN;
        if (controller != null && frame.hasPivot()) {
            BlockPos at = controller.getBlockPos();
            double dx = frame.centreOfMass().x - (at.getX() + 0.5);
            double dy = frame.centreOfMass().y - (at.getY() + 0.5);
            double dz = frame.centreOfMass().z - (at.getZ() + 0.5);
            comForward = (float) (dx * frame.forward().x + dz * frame.forward().z);
            comUp = (float) dy;
        }

        int commanded = 0;
        int applied = 0;
        int governors = 0;
        float stressUsed = Float.NaN;
        float stressCapacity = Float.NaN;
        float networkRpm = Float.NaN;
        if (level != null) {
            Map<BlockPos, Boolean> seen = new HashMap<>();
            for (VccDevice device : devices) {
                BlockPos governorPos = device.governorPos();
                if (governorPos == null || seen.put(governorPos, Boolean.TRUE) != null) continue;
                BlockEntity be = SableBridge.getBlockEntityOnContraption(level, governorPos);
                if (!(be instanceof RpmGovernorBE governor)) continue;
                governors++;
                commanded += Math.abs(governor.getCommandedRpm());
                applied += Math.abs(governor.getAppliedRpm());

                if (Float.isNaN(stressCapacity)) {
                    KineticNetworkMetrics.Readout readout = KineticNetworkMetrics.read(governor);
                    stressUsed = readout.networkStress();
                    stressCapacity = readout.networkCapacity();
                    networkRpm = Math.abs(governor.getTheoreticalSpeed());
                }
            }
        }

        float massTonnes = Double.isFinite(mass) ? (float) (mass / 1000.0) : Float.NaN;
        float thrustToWeight = Float.NaN;
        if (Double.isFinite(mass) && mass > 1.0 && controller != null) {
            double thrust = 0.0;
            for (VccDevice device : devices) {
                BlockEntity be = level == null ? null
                        : SableBridge.getBlockEntityOnContraption(level, device.actuatorPos());
                SableBridge.PropData prop = be == null ? null : SableBridge.readProp(be);
                if (prop != null) thrust += Math.abs(prop.thrustSigned());
            }
            Vector3d gravity = SableBridge.gravityWorld(controller);
            double g = gravity == null ? 9.81 : Math.max(0.1, gravity.length());
            double weight = mass * g;
            if (weight > 0.0) thrustToWeight = (float) (thrust / weight);
        }

        return new VccMk3SnapshotPacket.RigKinetics(massTonnes, thrustToWeight,
                rollI, pitchI, yawI, comForward, comUp, commanded, applied, governors,
                stressUsed, stressCapacity, networkRpm);
    }

    private static float[] authority(Airframe airframe, RigSolution solution) {
        float[] out = new float[VccMk3SnapshotPacket.AXIS_COUNT];
        for (ControlAxis axis : ControlAxis.values()) {
            int index = axis.ordinal();
            if (!solution.serves(axis)) {
                out[index] = Float.NaN;
                continue;
            }
            ControlMixer.Mix mix = solution.mix(axis);
            if (mix == null || mix.isEmpty()) {
                out[index] = 0f;
                continue;
            }
            if (axis == ControlAxis.AUX) {
                out[index] = mix.shares().isEmpty() ? 0f : 1f;
                continue;
            }
            double scale = axis.wantsMoment() ? FULL_SCALE_MOMENT : FULL_SCALE_FORCE;
            out[index] = (float) Math.max(0.0, Math.min(1.0, Math.abs(mix.netResponse()) / scale));
        }
        return out;
    }

    private static List<VccMk3SnapshotPacket.AxisEntry> axisReports(Airframe airframe,
                                                                    RigSolution solution) {
        List<VccMk3SnapshotPacket.AxisEntry> out = new ArrayList<>(VccMk3SnapshotPacket.AXIS_COUNT);
        for (ControlAxis axis : ControlAxis.values()) {
            if (!solution.serves(axis)) {
                out.add(new VccMk3SnapshotPacket.AxisEntry(
                        absentNote(airframe, axis), ""));
                continue;
            }
            ControlMixer.Mix mix = solution.mix(axis);
            if (mix == null) {
                out.add(VccMk3SnapshotPacket.AxisEntry.NONE);
                continue;
            }

            int working = 0;
            int idle = 0;
            double positive = 0.0;
            double negative = 0.0;
            for (ControlMixer.Share share : mix.shares()) {
                if (share.contributes()) {
                    working++;
                    if (share.gain() >= 0) positive += Math.abs(share.gain());
                    else negative += Math.abs(share.gain());
                } else {
                    idle++;
                }
            }

            String note;
            String flag = "";
            if (working == 0 && idle == 0) {
                note = "nothing fitted";
            } else if (working == 0) {
                note = idle + (idle == 1 ? " device cannot act" : " devices cannot act");
                flag = "no effect";
            } else {
                note = working + " " + pluralFor(axis, working);
                if (idle > 0) flag = idle + " idle";

                else if (axis.wantsMoment() && working > 1
                        && (positive < 1.0e-6 || negative < 1.0e-6)) {
                    flag = "one sided";
                } else if (axis.wantsMoment() && working > 1
                        && Math.abs(positive - negative) > 0.35 * Math.max(positive, negative)) {
                    flag = "asymmetric";
                }
            }
            out.add(new VccMk3SnapshotPacket.AxisEntry(note, flag));
        }
        return out;
    }

    private static String absentNote(Airframe airframe, ControlAxis axis) {
        return switch (axis) {
            case ROLL -> airframe == Airframe.GROUND
                    ? "ground vehicles do not roll" : "this airframe has no roll slots";
            case PITCH -> airframe == Airframe.GROUND
                    ? "ground vehicles do not pitch" : "this airframe has no pitch slots";
            case UP -> "no lift hardware on this airframe";
            case AUX -> "nothing auxiliary on this airframe";
            default -> "this airframe has no " + axis.label().toLowerCase(Locale.ROOT) + " slots";
        };
    }

    private static String pluralFor(ControlAxis axis, int count) {
        String word = switch (axis) {
            case FORWARD -> count == 1 ? "driver" : "drivers";
            case UP -> count == 1 ? "lifter" : "lifters";
            case YAW -> count == 1 ? "yaw device" : "yaw devices";
            case PITCH -> count == 1 ? "pitch device" : "pitch devices";
            case ROLL -> count == 1 ? "roll device" : "roll devices";
            case AUX -> count == 1 ? "device" : "devices";
        };
        return word;
    }

    private static List<VccMk3SnapshotPacket.SlotReport> slotReports(
            @Nullable Level level, Airframe airframe, Map<UUID, String> assignments,
            Map<UUID, VccDevice> byId, CraftFrame frame, RigSolution solution) {
        List<VccMk3SnapshotPacket.SlotReport> out = new ArrayList<>(airframe.slotList().size());
        for (SlotSpec spec : airframe.slotList()) {
            List<VccDevice> fitted = new ArrayList<>();
            for (Map.Entry<UUID, String> entry : assignments.entrySet()) {
                if (!spec.key().equals(entry.getValue())) continue;
                VccDevice device = byId.get(entry.getKey());
                if (device != null) fitted.add(device);
            }

            if (fitted.isEmpty()) {
                out.add(new VccMk3SnapshotPacket.SlotReport(spec.key(),
                        VccMk3SnapshotPacket.SLOT_EMPTY, 0f, Float.NaN, 0, 0f, Float.NaN, ""));
                continue;
            }

            float strongest = 0f;
            float arm = Float.NaN;
            float rpm = 0f;
            float angle = Float.NaN;
            String note = "";
            int working = 0;

            for (VccDevice device : fitted) {
                double gain = solution.gain(spec.axis(), device.id());
                if (Math.abs(gain) > Math.abs(strongest)) strongest = (float) gain;
                if (Math.abs(gain) > 1.0e-6) working++;
                else if (note.isEmpty()) note = solution.faultOf(device.id());

                if (Float.isNaN(arm) && frame.hasPivot()) {
                    Vec3 offset = frame.armTo(device.actuatorPos());
                    arm = (float) offset.length();
                }
                if (level != null) {
                    BlockEntity be = SableBridge.getBlockEntityOnContraption(level, device.actuatorPos());
                    if (be != null) {
                        if (Float.isNaN(angle) && SableBridge.matchesSwivelBearing(be)) {
                            Double target = SableBridge.readSwivelTargetAngle(be);
                            if (target != null) angle = (float) wrapDegrees(target);
                        }
                        SableBridge.PropData prop = SableBridge.readProp(be);
                        if (prop != null) {
                            rpm = Math.max(rpm, (float) Math.min(1.0, Math.abs(prop.rpm()) / 256.0));
                        }
                    }
                }
            }

            byte state = working > 0
                    ? VccMk3SnapshotPacket.SLOT_OK
                    : VccMk3SnapshotPacket.SLOT_FAULT;
            if (state == VccMk3SnapshotPacket.SLOT_FAULT && note.isEmpty()) {
                note = "Fitted, but it produces no " + spec.axis().label().toLowerCase(Locale.ROOT) + ".";
            }
            out.add(new VccMk3SnapshotPacket.SlotReport(spec.key(), state, strongest, arm,
                    fitted.size(), rpm, angle, note));
        }
        return out;
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped > 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    private static List<VccMk3SnapshotPacket.BusEntry> bus(@Nullable Level level, Airframe airframe,
                                                           Map<UUID, String> assignments,
                                                           Map<UUID, VccDevice> byId,
                                                           List<VccDevice> devices) {
        List<VccMk3SnapshotPacket.BusEntry> out = new ArrayList<>();
        if (level == null) return out;

        Map<BlockPos, Boolean> seen = new HashMap<>();
        for (VccDevice device : devices) {
            BlockPos governorPos = device.governorPos();
            if (governorPos == null || seen.put(governorPos, Boolean.TRUE) != null) continue;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(level, governorPos);
            if (!(be instanceof RpmGovernorBE governor)) continue;

            SlotSpec spec = airframe.slot(assignments.get(device.id()));
            String label = spec != null ? spec.shortLabel() : device.label();
            out.add(new VccMk3SnapshotPacket.BusEntry(label,
                    governor.getCommandedRpm(), governor.getAppliedRpm()));
            if (out.size() >= 8) break;
        }
        return out;
    }

    private static VccMk3SnapshotPacket.Coupling coupling(RigSolution solution) {
        RigSolution.Coupling worst = solution.worstCoupling();
        return new VccMk3SnapshotPacket.Coupling(worst.title(), worst.detail(), worst.isClean());
    }
}
