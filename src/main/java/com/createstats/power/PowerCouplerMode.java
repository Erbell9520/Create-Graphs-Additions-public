package com.createstats.power;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum PowerCouplerMode implements StringRepresentable {
    PULL("pull"),

    PUSH("push"),

    OFF("off");

    private final String serialized;

    PowerCouplerMode(String serialized) {
        this.serialized = serialized;
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.serialized;
    }

    public boolean seamReceives() {
        return this == PULL;
    }

    public boolean seamGives() {
        return this == PUSH;
    }

    public boolean gridReceives() {
        return this == PUSH;
    }

    public boolean gridGives() {
        return this == PULL;
    }

    public boolean isDirection() {
        return this != OFF;
    }

    public PowerCouplerMode next() {
        return this == PULL ? PUSH : PULL;
    }

    public PowerCouplerMode click(PowerCouplerMode remembered, boolean sneaking) {
        final PowerCouplerMode back = remembered != null && remembered.isDirection() ? remembered : PULL;
        if (this == OFF) {
            return back;
        }
        return sneaking ? OFF : this.next();
    }

    public static PowerCouplerMode byOrdinal(int ordinal) {
        final PowerCouplerMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PULL;
    }
}
