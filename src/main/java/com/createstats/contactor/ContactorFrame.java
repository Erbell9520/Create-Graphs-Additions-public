package com.createstats.contactor;

import com.createstats.block.MountedPart;
import net.minecraft.core.Direction;

public final class ContactorFrame {
    private ContactorFrame() {
    }

    public static double[] toBlock(Direction facing, int spin, double viewX, double viewY, double viewZ) {
        final double[] turned = spun(spin, (16.0 - viewX) / 16.0, viewZ / 16.0, viewY / 16.0);
        return MountedPart.toBlockSpace(facing, turned[0], turned[1], turned[2]);
    }

    static double[] spun(int spin, double x, double y, double z) {
        double dx = x - 0.5;
        double dz = z - 0.5;
        for (int i = 0; i < Math.floorMod(spin, 4); i++) {
            final double nx = dz;
            dz = -dx;
            dx = nx;
        }
        return new double[] {dx + 0.5, y, dz + 0.5};
    }

    static Direction direction(Direction facing, int spin, int ax, int ay, int az) {
        final double[] origin = MountedPart.toBlockSpace(facing, 0.5, 0.5, 0.5);
        final double[] s = spun(spin, 0.5 + ax, 0.5 + ay, 0.5 + az);
        final double[] p = MountedPart.toBlockSpace(facing, s[0], s[1], s[2]);
        final int x = (int) Math.round(p[0] - origin[0]);
        final int y = (int) Math.round(p[1] - origin[1]);
        final int z = (int) Math.round(p[2] - origin[2]);
        for (Direction d : Direction.values()) {
            if (d.getStepX() == x && d.getStepY() == y && d.getStepZ() == z) {
                return d;
            }
        }
        throw new IllegalStateException("no axis for " + facing + " turned " + spin);
    }

    public static Direction leftOf(Direction facing, int spin) {
        return direction(facing, spin, 1, 0, 0);
    }

    public static Direction rightOf(Direction facing, int spin) {
        return leftOf(facing, spin).getOpposite();
    }

    public static Direction topOf(Direction facing, int spin) {
        return direction(facing, spin, 0, 0, 1);
    }

    public static int spinFor(Direction facing, Direction look) {
        if (facing.getAxis() != Direction.Axis.Y || look.getAxis() == Direction.Axis.Y) {
            return 0;
        }
        final Direction want = facing == Direction.UP ? look : look.getOpposite();
        for (int spin = 0; spin < 4; spin++) {
            if (topOf(facing, spin) == want) {
                return spin;
            }
        }
        return 0;
    }

    public static int effectiveSpin(Direction facing, int spin) {
        return facing.getAxis() == Direction.Axis.Y ? Math.floorMod(spin, 4) : 0;
    }
}
