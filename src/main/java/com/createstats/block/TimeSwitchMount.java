package com.createstats.block;

import net.minecraft.core.Direction;

public final class TimeSwitchMount {
    private TimeSwitchMount() {
    }

    public static double[] toAuthored(Direction facing, double x, double y, double z, boolean point) {
        final double[] ex = MountedPart.toBlockSpace(facing, 1.5, 0.5, 0.5);
        final double[] ey = MountedPart.toBlockSpace(facing, 0.5, 1.5, 0.5);
        final double[] ez = MountedPart.toBlockSpace(facing, 0.5, 0.5, 1.5);
        final double bx = point ? x - 0.5 : x;
        final double by = point ? y - 0.5 : y;
        final double bz = point ? z - 0.5 : z;
        final double offset = point ? 0.5 : 0.0;
        return new double[] {
                offset + (ex[0] - 0.5) * bx + (ex[1] - 0.5) * by + (ex[2] - 0.5) * bz,
                offset + (ey[0] - 0.5) * bx + (ey[1] - 0.5) * by + (ey[2] - 0.5) * bz,
                offset + (ez[0] - 0.5) * bx + (ez[1] - 0.5) * by + (ez[2] - 0.5) * bz};
    }
}
