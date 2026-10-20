package com.createstats.power;

public final class PduBankDraw {
    private PduBankDraw() {
    }

    public static int shortfall(int wanted, int ceiling, int energy, int floorEnergy) {
        final int spend = Math.min(Math.max(0, wanted), Math.max(0, ceiling));
        final int aboveFloor = Math.max(0, energy - Math.max(0, floorEnergy));
        return Math.max(0, spend - aboveFloor);
    }
}
