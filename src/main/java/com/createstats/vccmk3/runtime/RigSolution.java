package com.createstats.vccmk3.runtime;

import com.createstats.vccmk3.hardware.DeviceCategory;
import com.createstats.vccmk3.model.Airframe;
import com.createstats.vccmk3.model.ControlAxis;
import com.createstats.vccmk3.model.SlotSpec;
import com.createstats.vccmk3.model.VccDevice;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// the only thing that commands hardware. before this the board was decoration
public final class RigSolution {
    public static final int RPM_CLAMP = 256;

    public static final double SURFACE_FULL_DEG = 24.0;

    public static final int STEER_FULL = 15;

    public static final int SIGNAL_FULL = 15;

    private static final double DEADBAND = 1.0e-3;

    private final Airframe airframe;
    private final CraftFrame frame;
    private final Map<ControlAxis, ControlMixer.Mix> mixes;
    private final Map<ControlAxis, List<VccDevice>> painted;
    private final Map<UUID, VccDevice> byId;
    private final Map<UUID, String> assignments;

    private RigSolution(Airframe airframe, CraftFrame frame,
                        Map<ControlAxis, ControlMixer.Mix> mixes,
                        Map<ControlAxis, List<VccDevice>> painted,
                        Map<UUID, VccDevice> byId,
                        Map<UUID, String> assignments) {
        this.airframe = airframe;
        this.frame = frame;
        this.mixes = mixes;
        this.painted = painted;
        this.byId = byId;
        this.assignments = assignments;
    }

    public static RigSolution solve(@Nullable Airframe airframe,
                                    @Nullable Map<UUID, String> assignments,
                                    @Nullable List<VccDevice> devices,
                                    @Nullable CraftFrame frame) {
        Airframe frameKind = airframe == null ? Airframe.FIXED_WING : airframe;
        CraftFrame craft = frame == null ? CraftFrame.UNKNOWN : frame;
        Map<UUID, String> assigned = assignments == null ? Map.of() : assignments;

        Map<UUID, VccDevice> byId = new HashMap<>();
        if (devices != null) {
            for (VccDevice device : devices) byId.put(device.id(), device);
        }

        Map<ControlAxis, List<VccDevice>> painted = new EnumMap<>(ControlAxis.class);
        for (Map.Entry<UUID, String> entry : assigned.entrySet()) {
            SlotSpec spec = frameKind.slot(entry.getValue());
            if (spec == null) continue;
            VccDevice device = byId.get(entry.getKey());
            if (device == null || !device.enabled()) continue;
            painted.computeIfAbsent(spec.axis(), a -> new ArrayList<>()).add(device);

            if (device.category() == DeviceCategory.WHEEL && spec.axis() == ControlAxis.FORWARD) {
                painted.computeIfAbsent(ControlAxis.YAW, a -> new ArrayList<>()).add(device);
            }
        }

        Map<ControlAxis, ControlMixer.Mix> mixes = new EnumMap<>(ControlAxis.class);
        for (ControlAxis axis : ControlAxis.values()) {
            if (!hasAxis(frameKind, axis)) continue;
            List<VccDevice> set = painted.get(axis);
            if (set == null || set.isEmpty()) {
                mixes.put(axis, ControlMixer.Mix.nothing(axis,
                        "Nothing on " + axis.label().toLowerCase(Locale.ROOT) + " yet."));
                continue;
            }
            mixes.put(axis, ControlMixer.solve(craft, axis, set));
        }

        return new RigSolution(frameKind, craft, mixes, painted, byId, assigned);
    }

    private static boolean hasAxis(Airframe airframe, ControlAxis axis) {
        return airframe.serves(axis);
    }

    public Airframe airframe() {
        return airframe;
    }

    public CraftFrame frame() {
        return frame;
    }

    public boolean serves(ControlAxis axis) {
        return mixes.containsKey(axis);
    }

    @Nullable
    public ControlMixer.Mix mix(@Nullable ControlAxis axis) {
        return axis == null ? null : mixes.get(axis);
    }

    public List<VccDevice> painted(@Nullable ControlAxis axis) {
        List<VccDevice> set = axis == null ? null : painted.get(axis);
        return set == null ? List.of() : Collections.unmodifiableList(set);
    }

    public double gain(@Nullable ControlAxis axis, @Nullable UUID deviceId) {
        ControlMixer.Mix mix = mix(axis);
        if (mix == null || deviceId == null) return 0.0;
        for (ControlMixer.Share share : mix.shares()) {
            if (share.deviceId().equals(deviceId)) return share.gain();
        }
        return 0.0;
    }

    public String faultOf(@Nullable UUID deviceId) {
        if (deviceId == null) return "";
        String slotKey = assignments.get(deviceId);
        SlotSpec spec = airframe.slot(slotKey);
        if (spec == null) return "";
        ControlMixer.Mix mix = mix(spec.axis());
        if (mix == null) return "";
        for (ControlMixer.Share share : mix.shares()) {
            if (!share.deviceId().equals(deviceId)) continue;
            return share.contributes() ? "" : share.note();
        }
        return "";
    }

    public boolean isIdle(@Nullable UUID deviceId) {
        return !faultOf(deviceId).isEmpty();
    }

    public Coupling worstCoupling() {
        ControlAxis fromAxis = null;
        ControlAxis toAxis = null;
        double worst = 0.0;
        for (Map.Entry<ControlAxis, ControlMixer.Mix> entry : mixes.entrySet()) {
            for (Map.Entry<ControlAxis, Double> cross : entry.getValue().crossCoupling().entrySet()) {
                double magnitude = Math.abs(cross.getValue());
                if (magnitude <= worst) continue;
                worst = magnitude;
                fromAxis = entry.getKey();
                toAxis = cross.getKey();
            }
        }
        if (fromAxis == null) return Coupling.CLEAN;
        return new Coupling(fromAxis, toAxis, worst,
                fromAxis.label() + " also " + verbFor(toAxis),
                explain(fromAxis, toAxis));
    }

    public record Coupling(@Nullable ControlAxis from, @Nullable ControlAxis to, double magnitude,
                           String title, String detail) {
        public static final Coupling CLEAN =
                new Coupling(null, null, 0.0, "Mix is clean", "No axis pushes another.");

        public boolean isClean() {
            return from == null;
        }
    }

    private static String verbFor(ControlAxis axis) {
        return switch (axis) {
            case FORWARD -> "pushes you forward";
            case UP -> "lifts the craft";
            case YAW -> "turns the nose";
            case PITCH -> "pitches the nose";
            case ROLL -> "rolls the craft";
            case AUX -> "drives auxiliary hardware";
        };
    }

    private String explain(ControlAxis from, ControlAxis to) {
        List<VccDevice> set = painted(from);
        int working = 0;
        for (VccDevice device : set) {
            if (Math.abs(gain(from, device.id())) > 1.0e-6) working++;
        }
        if (working == 1) {
            return "Only one device serves " + from.label().toLowerCase(Locale.ROOT)
                    + ", so nothing balances it.";
        }
        return "The " + from.label().toLowerCase(Locale.ROOT)
                + " set is not balanced about the centre of mass.";
    }

    public void dispatch(@Nullable Map<ControlAxis, Double> demand,
                         @Nullable Set<UUID> commandable,
                         CommandBatch batch) {
        if (batch == null) return;
        Map<ControlAxis, Double> demands = demand == null ? Map.of() : demand;

        for (Map.Entry<ControlAxis, ControlMixer.Mix> entry : mixes.entrySet()) {
            ControlAxis axis = entry.getKey();
            double asked = clamp(demands.getOrDefault(axis, 0.0));

            boolean atRest = Math.abs(asked) < DEADBAND;

            for (ControlMixer.Share share : entry.getValue().shares()) {
                VccDevice device = byId.get(share.deviceId());
                if (device == null || !device.enabled()) continue;
                if (commandable != null && !commandable.contains(device.id())) continue;
                if (!share.contributes()) continue;
                if (atRest && device.category() != DeviceCategory.SIGNAL_LINK) continue;

                dispatchOne(device, axis, asked * share.gain(), batch);
            }
        }
    }

    private void dispatchOne(VccDevice device, ControlAxis axis, double command, CommandBatch batch) {
        switch (device.category()) {
            case PROPELLER, GYRO_BEARING -> {
                if (device.governorPos() == null) {
                    batch.countUnsupported();
                    return;
                }

                batch.addRpm(device.governorPos(), clampRpm((int) Math.round(command * RPM_CLAMP)), true);
                batch.countApplied();
            }
            case CONTROL_SURFACE -> {
                int sign = device.geometry().commandSign();
                batch.addAngle(device.actuatorPos(), command * SURFACE_FULL_DEG * sign);
                batch.countApplied();
            }
            case WHEEL -> {
                if (axis == ControlAxis.YAW) {
                    batch.addSteer(device.actuatorPos(), clampSignal((int) Math.round(command * STEER_FULL)));
                    batch.countApplied();
                    return;
                }
                if (device.governorPos() == null) {
                    batch.countUnsupported();
                    return;
                }
                batch.addRpm(device.governorPos(), clampRpm((int) Math.round(command * RPM_CLAMP)), true);
                batch.countApplied();
            }
            case SIGNAL_LINK -> {
                if (device.cardRow() < 0) {
                    batch.countUnsupported();
                    return;
                }

                batch.raiseSignal(device.cardRow(),
                        clampSignal((int) Math.round(Math.max(0.0, command) * SIGNAL_FULL)));
                batch.countApplied();
            }
        }
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(-1.0, Math.min(1.0, value));
    }

    public static int clampRpm(int rpm) {
        return Math.max(-RPM_CLAMP, Math.min(RPM_CLAMP, rpm));
    }

    public static int clampSignal(int signal) {
        return Math.max(-15, Math.min(15, signal));
    }
}
