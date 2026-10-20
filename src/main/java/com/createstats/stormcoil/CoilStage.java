package com.createstats.stormcoil;

// height is the one setting, reach and catch follow it
public enum CoilStage {
    ONE(1, 6, 32, 20_000),
    TWO(2, 10, 64, 40_000),
    THREE(3, 16, 96, 80_000),

    FOUR(4, 24, 128, 160_000);

    public final int height;

    public final int reach;

    public final int catchRadius;

    public final int zapCost;

    CoilStage(int height, int reach, int catchRadius, int zapCost) {
        this.height = height;
        this.reach = reach;
        this.catchRadius = catchRadius;
        this.zapCost = zapCost;
    }

    public int number() {
        return ordinal() + 1;
    }

    public int targets() {
        return number();
    }

    public int capacitorSize() {
        return zapCost * targets() * CoilLaw.CAPACITOR_VOLLEYS;
    }

    public boolean hasGroundRods() {
        return this == FOUR;
    }

    public static CoilStage of(int number) {
        return values()[Math.max(1, Math.min(4, number)) - 1];
    }
}
