package com.createstats.ground;

import com.createstats.vccmk4.core.SteerPreset;

public enum VehicleKind {
    EMPTY("vehicle"),

    CAR("car"),

    TRUCK("truck"),

    FORKLIFT("forklift"),

    TRACKED("tracked"),

    ARTICULATED("articulated"),

    ROAD_TRAIN("road train"),

    HOVER("hovercraft"),

    WORK("work vehicle");

    public final String noun;

    VehicleKind(String noun) {
        this.noun = noun;
    }

    public static VehicleKind classify(int axles, int wheels, int jets, SteerPreset preset, boolean articulated,
                                       boolean jetDrive, int trailers, int machines) {
        if (wheels <= 0) {
            return jets > 0 ? HOVER : EMPTY;
        }
        if (trailers > 0) {
            return ROAD_TRAIN;
        }
        if (articulated) {
            return ARTICULATED;
        }
        if (jetDrive) {
            return HOVER;
        }
        if (preset == SteerPreset.SKID) {
            return TRACKED;
        }
        if (preset == SteerPreset.REAR) {
            return FORKLIFT;
        }
        if (machines > 0) {
            return WORK;
        }
        if (axles >= SteerPreset.TWIN_FRONT_FROM_AXLES || wheels >= 8) {
            return TRUCK;
        }
        return CAR;
    }

    public static VehicleKind byOrdinal(int i) {
        final VehicleKind[] v = values();
        return i >= 0 && i < v.length ? v[i] : EMPTY;
    }
}
