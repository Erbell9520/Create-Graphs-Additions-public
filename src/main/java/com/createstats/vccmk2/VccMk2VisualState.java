package com.createstats.vccmk2;

import net.minecraft.util.StringRepresentable;

public enum VccMk2VisualState implements StringRepresentable {
    OFF("off"),
    STANDBY("standby"),
    ACTIVE("active"),
    WARNING("warning");

    private final String name;

    VccMk2VisualState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public VccMk2VisualState next() {
        return switch (this) {
            case OFF -> STANDBY;
            case STANDBY -> ACTIVE;
            case ACTIVE -> WARNING;
            case WARNING -> OFF;
        };
    }
}
