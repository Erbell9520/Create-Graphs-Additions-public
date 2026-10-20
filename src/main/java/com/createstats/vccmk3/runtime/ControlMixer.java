package com.createstats.vccmk3.runtime;

import com.createstats.vccmk3.hardware.DeviceCategory;
import com.createstats.vccmk3.model.ControlAxis;
import com.createstats.vccmk3.model.DeviceGeometry;
import com.createstats.vccmk3.model.VccDevice;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ControlMixer {
    private static final double DEAD_FRACTION = 0.02;

    private static final double EPSILON = 1.0e-6;

    private ControlMixer() {}

    public record Share(UUID deviceId, double gain, double raw, String note, boolean asserted) {
        public boolean contributes() {
            return Math.abs(gain) > EPSILON || asserted;
        }
    }

    public record Mix(ControlAxis axis, List<Share> shares, double netResponse,
                      Map<ControlAxis, Double> crossCoupling, String summary) {
        public static Mix nothing(ControlAxis axis, String summary) {
            return new Mix(axis, List.of(), 0.0, Map.of(), summary);
        }

        public boolean isEmpty() {
            return shares.isEmpty();
        }

        public List<Share> idle() {
            List<Share> out = new ArrayList<>();
            for (Share share : shares) {
                if (!share.contributes()) out.add(share);
            }
            return out;
        }
    }

    public static Mix solve(@Nullable CraftFrame frame, @Nullable ControlAxis axis,
                            @Nullable List<VccDevice> painted) {
        if (axis == null) return Mix.nothing(ControlAxis.AUX, "No axis selected.");
        if (painted == null || painted.isEmpty()) {
            return Mix.nothing(axis, "Nothing painted onto " + axis.label() + " yet.");
        }
        if (frame == null) frame = CraftFrame.UNKNOWN;
        if (axis == ControlAxis.AUX) {
            return auxMix(painted);
        }
        Vec3 reference = frame.reference(axis);
        if (reference == null) {
            return Mix.nothing(axis, "This axis has no geometric reference.");
        }
        if (axis.wantsMoment() && !frame.hasPivot()) {
            return Mix.nothing(axis, frame.note());
        }

        List<Share> raws = new ArrayList<>(painted.size());
        double strongest = 0.0;
        boolean anyAsserted = false;
        for (VccDevice device : painted) {
            Share share = project(frame, axis, reference, device);
            raws.add(share);
            if (share.asserted()) anyAsserted = true;
            else strongest = Math.max(strongest, Math.abs(share.raw()));
        }

        if (strongest < EPSILON && !anyAsserted) {
            return new Mix(axis, zeroed(raws), 0.0, Map.of(),
                    "None of this hardware can produce " + axis.label().toLowerCase(java.util.Locale.ROOT)
                            + ". Check which way it points.");
        }

        List<Share> shares = new ArrayList<>(raws.size());
        double netResponse = 0.0;
        for (Share share : raws) {
            if (share.asserted()) {
                shares.add(share);
                continue;
            }
            if (strongest < EPSILON) {
                shares.add(new Share(share.deviceId(), 0.0, share.raw(), share.note(), false));
                continue;
            }
            double gain = share.raw() / strongest;
            if (Math.abs(gain) < DEAD_FRACTION) {
                shares.add(new Share(share.deviceId(), 0.0, share.raw(),
                        "Too close to the pivot to help here.", false));
                continue;
            }
            shares.add(new Share(share.deviceId(), gain, share.raw(), share.note(), false));
            netResponse += gain * share.raw();
        }

        return new Mix(axis, shares, netResponse, crossCoupling(frame, axis, painted, shares),
                summarise(axis, shares));
    }

    private static Share project(CraftFrame frame, ControlAxis axis, Vec3 reference, VccDevice device) {
        DeviceGeometry geometry = device.geometry();

        // signal links: take the player's word, refusing or guessing is worse

        if (device.category() == DeviceCategory.SIGNAL_LINK) {
            return new Share(device.id(), 1.0, 0.0,
                    "Taken on trust. The controller cannot see what this channel drives.", true);
        }

        Vec3 force = forceOf(frame, device, geometry);
        if (force == null) {
            String note = geometry.note().isEmpty()
                    ? "This device reports no direction, so it cannot be mixed."
                    : geometry.note();
            return new Share(device.id(), 0.0, 0.0, note, false);
        }

        if (device.category() == DeviceCategory.WHEEL && axis == ControlAxis.YAW) {
            double aheadness = frame.armTo(device.actuatorPos()).dot(frame.forward());
            if (Math.abs(aheadness) < EPSILON) {
                return new Share(device.id(), 0.0, 0.0,
                        "This axle sits on the pivot, so steering it will not turn the craft.", false);
            }
            return new Share(device.id(), 0.0, aheadness, "", false);
        }

        Vec3 measured = axis.wantsMoment() ? frame.armTo(device.actuatorPos()).cross(force) : force;
        double raw = measured.dot(reference);

        String note = "";
        if (Math.abs(raw) < EPSILON) {
            note = axis.wantsMoment()
                    ? "Mounted so it makes no " + axis.label().toLowerCase(java.util.Locale.ROOT) + " moment."
                    : "Points the wrong way to push you " + axis.label().toLowerCase(java.util.Locale.ROOT) + ".";
        }
        return new Share(device.id(), 0.0, raw, note, false);
    }

    @Nullable
    private static Vec3 forceOf(CraftFrame frame, VccDevice device, DeviceGeometry geometry) {
        return switch (device.category()) {
            case PROPELLER, GYRO_BEARING -> geometry.thrust();

            case CONTROL_SURFACE -> geometry.surfaceForce(frame.forward());

            case WHEEL -> frame.forward();
            case SIGNAL_LINK -> null;
        };
    }

    private static Map<ControlAxis, Double> crossCoupling(CraftFrame frame, ControlAxis axis,
                                                          List<VccDevice> painted, List<Share> shares) {
        Map<UUID, Double> gains = new java.util.HashMap<>();
        for (Share share : shares) {
            if (!share.asserted()) gains.put(share.deviceId(), share.gain());
        }

        Map<ControlAxis, Double> out = new EnumMap<>(ControlAxis.class);
        for (ControlAxis other : ControlAxis.values()) {
            if (other == axis || !other.isFlightAxis()) continue;
            Vec3 reference = frame.reference(other);
            if (reference == null) continue;
            if (other.wantsMoment() && !frame.hasPivot()) continue;

            double total = 0.0;
            for (VccDevice device : painted) {
                Double gain = gains.get(device.id());
                if (gain == null || gain == 0.0) continue;
                Vec3 force = forceOf(frame, device, device.geometry());
                if (force == null) continue;
                Vec3 measured = other.wantsMoment()
                        ? frame.armTo(device.actuatorPos()).cross(force)
                        : force;
                total += gain * measured.dot(reference);
            }
            if (Math.abs(total) > EPSILON) out.put(other, total);
        }
        return out;
    }

    private static Mix auxMix(List<VccDevice> painted) {
        List<Share> shares = new ArrayList<>(painted.size());
        for (VccDevice device : painted) {
            shares.add(new Share(device.id(), 1.0, 0.0, "", true));
        }
        return new Mix(ControlAxis.AUX, shares, 0.0, Map.of(),
                painted.size() + (painted.size() == 1 ? " device" : " devices") + " on auxiliary.");
    }

    private static List<Share> zeroed(List<Share> raws) {
        List<Share> out = new ArrayList<>(raws.size());
        for (Share share : raws) {
            out.add(share.asserted()
                    ? share
                    : new Share(share.deviceId(), 0.0, share.raw(), share.note(), false));
        }
        return out;
    }

    private static String summarise(ControlAxis axis, List<Share> shares) {
        int working = 0;
        int idle = 0;
        int opposed = 0;
        for (Share share : shares) {
            if (!share.contributes()) {
                idle++;
            } else {
                working++;
                if (share.gain() < 0) opposed++;
            }
        }
        StringBuilder text = new StringBuilder();
        text.append(working).append(working == 1 ? " device on " : " devices on ").append(axis.label().toLowerCase(java.util.Locale.ROOT));
        if (opposed > 0 && opposed < working) {
            text.append(", ").append(opposed).append(" opposed");
        }
        if (idle > 0) {
            text.append(". ").append(idle).append(idle == 1 ? " does nothing here." : " do nothing here.");
        } else {
            text.append('.');
        }
        return text.toString();
    }
}
