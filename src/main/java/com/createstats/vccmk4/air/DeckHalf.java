package com.createstats.vccmk4.air;

import net.minecraft.util.StringRepresentable;

public enum DeckHalf implements StringRepresentable {
    LEFT("left"),
    RIGHT("right");

    private final String name;

    DeckHalf(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
