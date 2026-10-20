package com.createstats.vccmk3.model;

import com.createstats.vccmk3.hardware.DeviceCategory;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum Airframe {
    FIXED_WING("Fixed Wing", "FIXED", buildFixedWing()),

    AIRSHIP("Airship", "AIRSHIP", buildAirship()),

    GROUND("Ground Vehicle", "GROUND", buildGround()),

    HYBRID("Hybrid", "HYBRID", buildHybrid());

    private final String label;
    private final String shortLabel;
    private final Map<String, SlotSpec> slots;

    private final List<SlotSpec> ordered;

    Airframe(String label, String shortLabel, Map<String, SlotSpec> slots) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.ordered = List.copyOf(slots.values());
        this.slots = Map.copyOf(slots);
    }

    public boolean serves(@Nullable ControlAxis axis) {
        if (axis == null) return false;
        for (SlotSpec spec : ordered) {
            if (spec.axis() == axis) return true;
        }
        return this == GROUND && axis == ControlAxis.YAW;
    }

    public String label() {
        return label;
    }

    public String shortLabel() {
        return shortLabel;
    }

    public boolean isFreeform() {
        return this == HYBRID;
    }

    public List<SlotSpec> slotList() {
        return ordered;
    }

    @Nullable
    public SlotSpec slot(@Nullable String key) {
        return key == null ? null : slots.get(key);
    }

    public boolean hasSlot(@Nullable String key) {
        return key != null && slots.containsKey(key);
    }

    public static Airframe safeValueOf(String name, Airframe fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public static Airframe byOrdinal(int ordinal) {
        Airframe[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : FIXED_WING;
    }

    private static final class Categories {
        static final Set<DeviceCategory> THRUST =
                Set.of(DeviceCategory.PROPELLER, DeviceCategory.GYRO_BEARING);
        static final Set<DeviceCategory> SURFACE = Set.of(DeviceCategory.CONTROL_SURFACE);
        static final Set<DeviceCategory> WHEEL = Set.of(DeviceCategory.WHEEL);
        static final Set<DeviceCategory> LINK = Set.of(DeviceCategory.SIGNAL_LINK);

        private Categories() {}
    }

    private static void put(Map<String, SlotSpec> into, String key, String label, String shortLabel,
                            ControlAxis axis, SlotSpec.Symmetry symmetry,
                            Set<DeviceCategory> accepts, boolean wantsLift) {
        into.put(key, new SlotSpec(key, label, shortLabel, axis, symmetry, accepts, wantsLift));
    }

    private static Map<String, SlotSpec> buildFixedWing() {
        Map<String, SlotSpec> m = new LinkedHashMap<>();
        put(m, "NOSE_ENGINE", "Nose Engine", "NOSE", ControlAxis.FORWARD, SlotSpec.Symmetry.SINGLE, Categories.THRUST, false);
        put(m, "PORT_ENGINE", "Port Engine", "P ENG", ControlAxis.FORWARD, SlotSpec.Symmetry.SYMMETRIC, Categories.THRUST, false);
        put(m, "STBD_ENGINE", "Starboard Engine", "S ENG", ControlAxis.FORWARD, SlotSpec.Symmetry.SYMMETRIC, Categories.THRUST, false);

        put(m, "PORT_AILERON", "Port Aileron", "P AIL", ControlAxis.ROLL, SlotSpec.Symmetry.ANTISYMMETRIC, Categories.SURFACE, true);
        put(m, "STBD_AILERON", "Starboard Aileron", "S AIL", ControlAxis.ROLL, SlotSpec.Symmetry.ANTISYMMETRIC, Categories.SURFACE, true);
        put(m, "PORT_FLAP", "Port Flap", "P FLP", ControlAxis.UP, SlotSpec.Symmetry.SYMMETRIC, Categories.SURFACE, true);
        put(m, "STBD_FLAP", "Starboard Flap", "S FLP", ControlAxis.UP, SlotSpec.Symmetry.SYMMETRIC, Categories.SURFACE, true);
        put(m, "PORT_ELEVATOR", "Port Elevator", "P ELV", ControlAxis.PITCH, SlotSpec.Symmetry.SYMMETRIC, Categories.SURFACE, true);
        put(m, "STBD_ELEVATOR", "Starboard Elevator", "S ELV", ControlAxis.PITCH, SlotSpec.Symmetry.SYMMETRIC, Categories.SURFACE, true);

        put(m, "RUDDER", "Rudder", "RUD", ControlAxis.YAW, SlotSpec.Symmetry.SINGLE, Categories.SURFACE, false);
        put(m, "AIRBRAKE", "Airbrake", "BRK", ControlAxis.AUX, SlotSpec.Symmetry.SINGLE, Categories.SURFACE, false);
        return m;
    }

    private static Map<String, SlotSpec> buildAirship() {
        Map<String, SlotSpec> m = new LinkedHashMap<>();
        put(m, "BURNER", "Burner", "BURNER", ControlAxis.UP, SlotSpec.Symmetry.SINGLE, Categories.LINK, false);
        put(m, "VENT", "Vent", "VENT", ControlAxis.UP, SlotSpec.Symmetry.SINGLE, Categories.LINK, false);
        put(m, "BALLAST", "Ballast", "BALLAST", ControlAxis.AUX, SlotSpec.Symmetry.SINGLE, Categories.LINK, false);
        put(m, "PORT_ENGINE", "Port Engine", "P ENG", ControlAxis.FORWARD, SlotSpec.Symmetry.SYMMETRIC, Categories.THRUST, false);
        put(m, "STBD_ENGINE", "Starboard Engine", "S ENG", ControlAxis.FORWARD, SlotSpec.Symmetry.SYMMETRIC, Categories.THRUST, false);
        put(m, "PORT_ELEVATOR", "Port Elevator", "P ELV", ControlAxis.PITCH, SlotSpec.Symmetry.SYMMETRIC, Categories.SURFACE, true);
        put(m, "STBD_ELEVATOR", "Starboard Elevator", "S ELV", ControlAxis.PITCH, SlotSpec.Symmetry.SYMMETRIC, Categories.SURFACE, true);
        put(m, "UPPER_RUDDER", "Upper Rudder", "U RUD", ControlAxis.YAW, SlotSpec.Symmetry.SYMMETRIC, Categories.SURFACE, false);
        put(m, "LOWER_RUDDER", "Lower Rudder", "L RUD", ControlAxis.YAW, SlotSpec.Symmetry.SYMMETRIC, Categories.SURFACE, false);
        return m;
    }

    private static Map<String, SlotSpec> buildHybrid() {
        Map<String, SlotSpec> m = new LinkedHashMap<>();
        Set<DeviceCategory> anyThrust = Set.of(DeviceCategory.PROPELLER, DeviceCategory.GYRO_BEARING,
                DeviceCategory.WHEEL, DeviceCategory.SIGNAL_LINK);
        Set<DeviceCategory> anything = Set.of(DeviceCategory.PROPELLER, DeviceCategory.GYRO_BEARING,
                DeviceCategory.WHEEL, DeviceCategory.CONTROL_SURFACE, DeviceCategory.SIGNAL_LINK);
        put(m, "LIFT", "Lift", "LIFT", ControlAxis.UP, SlotSpec.Symmetry.SINGLE, anyThrust, false);
        put(m, "THRUST", "Thrust", "THRUST", ControlAxis.FORWARD, SlotSpec.Symmetry.SINGLE, anyThrust, false);
        put(m, "YAW", "Yaw", "YAW", ControlAxis.YAW, SlotSpec.Symmetry.SINGLE, anything, false);
        put(m, "PITCH", "Pitch", "PITCH", ControlAxis.PITCH, SlotSpec.Symmetry.SINGLE, anything, false);
        put(m, "ROLL", "Roll", "ROLL", ControlAxis.ROLL, SlotSpec.Symmetry.SINGLE, anything, false);
        put(m, "AUX", "Auxiliary", "AUX", ControlAxis.AUX, SlotSpec.Symmetry.SINGLE, anything, false);
        return m;
    }

    private static Map<String, SlotSpec> buildGround() {
        Map<String, SlotSpec> m = new LinkedHashMap<>();
        put(m, "FRONT_LEFT", "Front Left", "FL", ControlAxis.FORWARD, SlotSpec.Symmetry.ANTISYMMETRIC, Categories.WHEEL, false);
        put(m, "FRONT_RIGHT", "Front Right", "FR", ControlAxis.FORWARD, SlotSpec.Symmetry.ANTISYMMETRIC, Categories.WHEEL, false);
        put(m, "MID_LEFT", "Mid Left", "ML", ControlAxis.FORWARD, SlotSpec.Symmetry.ANTISYMMETRIC, Categories.WHEEL, false);
        put(m, "MID_RIGHT", "Mid Right", "MR", ControlAxis.FORWARD, SlotSpec.Symmetry.ANTISYMMETRIC, Categories.WHEEL, false);
        put(m, "REAR_LEFT", "Rear Left", "RL", ControlAxis.FORWARD, SlotSpec.Symmetry.ANTISYMMETRIC, Categories.WHEEL, false);
        put(m, "REAR_RIGHT", "Rear Right", "RR", ControlAxis.FORWARD, SlotSpec.Symmetry.ANTISYMMETRIC, Categories.WHEEL, false);
        return m;
    }
}
