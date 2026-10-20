package com.createstats.vehiclelight;

import net.minecraft.util.StringRepresentable;

public enum VehicleLightGlow implements StringRepresentable {
    OFF("off"),
    DIM("dim"),
    BRIGHT("bright");

    private final String name;

    VehicleLightGlow(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public float level() {
        return switch (this) {
            case OFF -> 0.0f;
            case DIM -> 0.55f;
            case BRIGHT -> 1.0f;
        };
    }
}
