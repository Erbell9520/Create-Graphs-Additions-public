package com.createstats.block;

import net.minecraft.util.StringRepresentable;

// not DoubleBlockHalf, there's a middle now
public enum ShutterSegment implements StringRepresentable {
    LOWER("lower"),

    MIDDLE("middle"),

    UPPER("upper");

    private final String name;

    ShutterSegment(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public boolean slave() {
        return this != LOWER;
    }
}
