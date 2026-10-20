package com.createstats.vccmk3.model;

import com.createstats.vccmk3.hardware.DeviceCategory;

import java.util.Set;

public record SlotSpec(
        String key,
        String label,
        String shortLabel,
        ControlAxis axis,
        Symmetry symmetry,
        Set<DeviceCategory> accepts,
        boolean wantsLift
) {
    public enum Symmetry {
        SYMMETRIC,

        ANTISYMMETRIC,

        SINGLE
    }

    public SlotSpec {
        if (accepts == null) {
            throw new IllegalArgumentException(
                    "Slot '" + key + "' was built with no accepted categories. If this came from an "
                            + "enum catalogue, its category sets were read before they were assigned.");
        }
        accepts = Set.copyOf(accepts);
    }

    public boolean accepts(DeviceCategory category) {
        return category != null && accepts.contains(category);
    }

    public boolean isSurface() {
        return accepts.contains(DeviceCategory.CONTROL_SURFACE);
    }
}
