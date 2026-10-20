package com.createstats.block;

import net.minecraft.core.Direction;

public final class MountedPart {
    private MountedPart() {
    }

    public static float[] rotationDegrees(Direction facing) {
        return switch (facing) {
            case UP -> new float[] {0.0f, 0.0f};
            case DOWN -> new float[] {0.0f, 180.0f};
            case NORTH -> new float[] {0.0f, -90.0f};
            case SOUTH -> new float[] {180.0f, -90.0f};
            case WEST -> new float[] {90.0f, -90.0f};
            case EAST -> new float[] {270.0f, -90.0f};
        };
    }

    public static Direction baseFace(Direction facing) {
        final float[] r = rotationDegrees(facing);
        int[] v = {0, -1, 0};
        v = aboutX(v, Math.round(r[1]));
        v = aboutY(v, Math.round(r[0]));
        return axis(v, facing);
    }

    public static float spinDegrees(int spin) {
        return 90.0f * Math.floorMod(spin, 4);
    }

    public static Direction authoredFront(Direction facing, int spin) {
        final float[] r = rotationDegrees(facing);
        int[] v = {0, 0, 1};
        v = aboutY(v, Math.round(spinDegrees(spin)));
        v = aboutX(v, Math.round(r[1]));
        v = aboutY(v, Math.round(r[0]));
        return axis(v, facing);
    }

    public static int spinTowards(Direction facing, Direction towards) {
        for (int spin = 0; spin < 4; spin++) {
            if (authoredFront(facing, spin) == towards) {
                return spin;
            }
        }
        return 0;
    }

    public static double[] toBlockSpace(Direction facing, double x, double y, double z) {
        final float[] r = rotationDegrees(facing);
        double[] v = {x - 0.5, y - 0.5, z - 0.5};
        v = pointAboutX(v, Math.round(r[1]));
        v = pointAboutY(v, Math.round(r[0]));
        return new double[] {v[0] + 0.5, v[1] + 0.5, v[2] + 0.5};
    }

    private static double[] pointAboutX(double[] v, int degrees) {
        final int c = cos(degrees);
        final int s = sin(degrees);
        return new double[] {v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c};
    }

    private static double[] pointAboutY(double[] v, int degrees) {
        final int c = cos(degrees);
        final int s = sin(degrees);
        return new double[] {v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c};
    }

    private static Direction axis(int[] v, Direction facing) {
        for (Direction dir : Direction.values()) {
            if (dir.getStepX() == v[0] && dir.getStepY() == v[1] && dir.getStepZ() == v[2]) {
                return dir;
            }
        }
        throw new IllegalStateException("rotation produced no axis direction for " + facing);
    }

    private static int[] aboutX(int[] v, int degrees) {
        final int c = cos(degrees);
        final int s = sin(degrees);
        return new int[] {v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c};
    }

    private static int[] aboutY(int[] v, int degrees) {
        final int c = cos(degrees);
        final int s = sin(degrees);
        return new int[] {v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c};
    }

    private static int cos(int degrees) {
        return switch (Math.floorMod(degrees, 360)) {
            case 0 -> 1;
            case 180 -> -1;
            default -> 0;
        };
    }

    private static int sin(int degrees) {
        return switch (Math.floorMod(degrees, 360)) {
            case 90 -> 1;
            case 270 -> -1;
            default -> 0;
        };
    }
}
