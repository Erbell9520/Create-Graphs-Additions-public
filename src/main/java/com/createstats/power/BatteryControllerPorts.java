package com.createstats.power;

import net.minecraft.core.Direction;

// two columns, ins and outs, wiring a bank to an output by mistake should look wrong
public final class BatteryControllerPorts {
    private BatteryControllerPorts() {
    }

    public static final int INPUTS = 3;

    public static final int OUTPUTS = 3;

    public static final int COUNT = INPUTS + OUTPUTS;

    private static final double INPUT_X = 3.5 / 16.0;
    private static final double OUTPUT_X = 12.5 / 16.0;

    private static final double[] ROW_Z = {3.5 / 16.0, 8.0 / 16.0, 12.5 / 16.0};

    private static final double PICK_RADIUS = 1.9 / 16.0;

    public static boolean isPortIndex(int port) {
        return port >= 0 && port < COUNT;
    }

    public static boolean isInput(int port) {
        return port >= 0 && port < INPUTS;
    }

    public static boolean isOutput(int port) {
        return port >= INPUTS && port < COUNT;
    }

    public static int outputIndex(int port) {
        return port - INPUTS;
    }

    public static int outputPort(int index) {
        return INPUTS + index;
    }

    public static String label(int port) {
        if (!isPortIndex(port)) {
            return "?";
        }
        return isInput(port) ? String.valueOf((char) ('A' + port))
                : String.valueOf(outputIndex(port) + 1);
    }

    public static String describe(int port) {
        if (!isPortIndex(port)) {
            return "no terminal";
        }
        return (isInput(port) ? "Input " : "Output ") + label(port);
    }

    public static double localX(int port) {
        return isInput(port) ? INPUT_X : OUTPUT_X;
    }

    public static double localZ(int port) {
        return ROW_Z[Math.floorMod(isInput(port) ? port : outputIndex(port), ROW_Z.length)];
    }

    public static double[] toModelSpace(Direction facing, double x, double z) {
        final double cx = x - 0.5;
        final double cz = z - 0.5;
        final double mx;
        final double mz;
        switch (facing == null ? Direction.NORTH : facing) {
            case EAST -> {
                mx = cz;
                mz = -cx;
            }
            case SOUTH -> {
                mx = -cx;
                mz = -cz;
            }
            case WEST -> {
                mx = -cz;
                mz = cx;
            }
            default -> {
                mx = cx;
                mz = cz;
            }
        }
        return new double[]{mx + 0.5, mz + 0.5};
    }

    public static double[] toBlockSpace(Direction facing, double mx, double mz) {
        final double cx = mx - 0.5;
        final double cz = mz - 0.5;
        final double x;
        final double z;
        switch (facing == null ? Direction.NORTH : facing) {
            case EAST -> {
                x = -cz;
                z = cx;
            }
            case SOUTH -> {
                x = -cx;
                z = -cz;
            }
            case WEST -> {
                x = cz;
                z = -cx;
            }
            default -> {
                x = cx;
                z = cz;
            }
        }
        return new double[]{x + 0.5, z + 0.5};
    }

    public static int pick(Direction facing, double x, double z) {
        final double[] local = toModelSpace(facing, x, z);
        int best = -1;
        double bestDistance = PICK_RADIUS * PICK_RADIUS;
        for (int port = 0; port < COUNT; port++) {
            final double dx = local[0] - localX(port);
            final double dz = local[1] - localZ(port);
            final double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = port;
            }
        }
        return best;
    }

    public static double[] boxOf(Direction facing, int port) {
        final double half = 0.75 / 16.0;
        final double[] a = toBlockSpace(facing, localX(port) - half, localZ(port) - half);
        final double[] b = toBlockSpace(facing, localX(port) + half, localZ(port) + half);
        return new double[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1])};
    }

    public static double topY() {
        return 18.0 / 16.0;
    }
}
