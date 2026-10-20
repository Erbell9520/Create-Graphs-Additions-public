package com.createstats.home;

public final class ColumnRule {
    private ColumnRule() {
    }

    public static final double BARRIER_ABOVE = 0.5;

    public static RoomFill.Cell classify(boolean loaded, boolean door, double feetTop, boolean floorSturdy,
                                         boolean roofed) {
        if (!loaded) {
            return RoomFill.Cell.OPEN;
        }
        if (door) {
            return RoomFill.Cell.DOOR;
        }
        if (feetTop > BARRIER_ABOVE) {
            return RoomFill.Cell.WALL;
        }
        if (!floorSturdy) {
            return RoomFill.Cell.WALL;
        }
        return roofed ? RoomFill.Cell.FLOOR : RoomFill.Cell.OPEN;
    }

    public static boolean roofed(int headY, int motionTopY) {
        return motionTopY > headY;
    }
}
