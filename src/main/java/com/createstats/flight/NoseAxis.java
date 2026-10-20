package com.createstats.flight;

import net.minecraft.core.Direction;

public enum NoseAxis {
    POS_X(Direction.EAST),
    NEG_X(Direction.WEST),
    POS_Z(Direction.SOUTH),
    NEG_Z(Direction.NORTH);

    private final Direction asDirection;

    NoseAxis(Direction asDirection) {
        this.asDirection = asDirection;
    }

    public Direction asDirection() {
        return asDirection;
    }

    public static NoseAxis safeValueOf(String name, NoseAxis fallback) {
        if (name == null) return fallback;
        try {
            return NoseAxis.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
