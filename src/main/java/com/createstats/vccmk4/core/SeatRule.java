package com.createstats.vccmk4.core;

import java.util.UUID;

public final class SeatRule {
    public static final int RADIUS = 2;

    public static final int MAX_PILOTS = 2;

    static final double ON_CRAFT = 1000.0;

    private SeatRule() {
    }

    public static double score(UUID seatCraft, UUID craft, int[] offset) {
        if (offset == null || offset.length < 3) return 0.0;
        if (seatCraft != null && craft != null && !seatCraft.equals(craft)) return 0.0;
        if (reach(offset) > RADIUS) return 0.0;
        double near = 1.0 / (1.0 + Math.sqrt((double) offset[0] * offset[0] + (double) offset[1] * offset[1]
                + (double) offset[2] * offset[2]));
        return (seatCraft != null && seatCraft.equals(craft) ? ON_CRAFT : 0.0) + near;
    }

    static int reach(int[] offset) {
        return Math.max(Math.abs(offset[0]), Math.max(Math.abs(offset[1]), Math.abs(offset[2])));
    }
}
