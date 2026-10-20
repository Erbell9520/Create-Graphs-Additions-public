package com.createstats.power;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

// PUSH needs their connector on PULL. each block named for its own action. yes it reads backwards
public enum PowerCouplerMode implements StringRepresentable {
    PULL("pull"),

    PUSH("push");

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

    public PowerCouplerMode next() {
        return this == PULL ? PUSH : PULL;
    }

    public static PowerCouplerMode byOrdinal(int ordinal) {
        final PowerCouplerMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PULL;
    }
}
