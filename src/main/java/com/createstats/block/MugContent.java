package com.createstats.block;

import net.minecraft.util.StringRepresentable;

public enum MugContent implements StringRepresentable {
    EMPTY("empty"),
    COFFEE("coffee"),
    MILK("milk"),
    LATTE("latte"),

    SPRING_WATER("spring_water"),
    CHILLED_WATER("chilled_water"),
    SPARKLING_WATER("sparkling_water");

    private final String name;

    MugContent(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static MugContent of(boolean coffee, boolean milk) {
        if (coffee && milk) {
            return LATTE;
        }
        if (coffee) {
            return COFFEE;
        }
        return milk ? MILK : EMPTY;
    }

    public static MugContent byOrdinal(int ordinal) {
        final MugContent[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : EMPTY;
    }

    public boolean isDrinkable() {
        return this != EMPTY;
    }

    public boolean isWater() {
        return this == SPRING_WATER || this == CHILLED_WATER || this == SPARKLING_WATER;
    }

    public boolean isCold() {
        return this == CHILLED_WATER || this == SPARKLING_WATER;
    }
}
