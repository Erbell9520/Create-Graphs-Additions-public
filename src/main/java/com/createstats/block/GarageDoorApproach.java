package com.createstats.block;

public final class GarageDoorApproach {
    public static final int DEFAULT_DEPTH = 4;

    public static final double CLOSING_SPEED = 0.05;

    public static final double DOORWAY_HALF_DEPTH = 0.5;

    private GarageDoorApproach() {
    }

    public static boolean inApproachBox(double along, double across, double up,
                                        int width, int height, int depth) {
        return Math.abs(along) <= depth
                && Math.abs(across) <= width / 2.0
                && Math.abs(up) <= height / 2.0;
    }

    public static boolean closing(double along, double alongVelocity) {
        if (Math.abs(along) <= DOORWAY_HALF_DEPTH) {
            return true;
        }
        return Math.signum(along) * alongVelocity <= -CLOSING_SPEED;
    }

    public static boolean arriving(double along, double across, double up,
                                   double alongVelocity, int width, int height, int depth) {
        return inApproachBox(along, across, up, width, height, depth)
                && closing(along, alongVelocity);
    }

    public static boolean inDoorway(double along, double across, double up, int width, int height) {
        return Math.abs(along) <= DOORWAY_HALF_DEPTH
                && Math.abs(across) <= width / 2.0
                && Math.abs(up) <= height / 2.0;
    }
}
