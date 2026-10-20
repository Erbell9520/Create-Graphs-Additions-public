package com.createstats.power;

import com.createstats.block.MainBreakerBlock;
import com.createstats.block.MountedPart;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// same reason as CouplerSockets
public final class MainBreakerStuds {
    private MainBreakerStuds() {
    }

    public enum Stud {
        INPUT,

        OUTPUT;

        public String key() {
            return this == INPUT ? "input" : "output";
        }

        public int index() {
            return ordinal();
        }

        public String label() {
            return this == INPUT ? "INPUT" : "OUTPUT";
        }

        public Stud other() {
            return this == INPUT ? OUTPUT : INPUT;
        }
    }

    public static final int COUNT = 2;

    public static boolean isStudIndex(int index) {
        return index >= 0 && index < COUNT;
    }

    public static int otherStud(int stud) {
        final Stud one = byIndex(stud);
        return one == null ? -1 : one.other().index();
    }

    @Nullable
    public static Stud byIndex(int index) {
        return isStudIndex(index) ? Stud.values()[index] : null;
    }

    private static final double INPUT_X = 0.28750;
    private static final double OUTPUT_X = 0.71250;
    private static final double STUD_Z = 0.71250;
    private static final double STUD_Y = 0.48438;

    private static final double HALF_XZ = 0.06688;
    private static final double BOX_Y0 = 0.44375;
    private static final double BOX_Y1 = 0.51250;

    private static final double PICK_REACH = 0.11;

    @Nullable
    public static Direction facingOf(@Nullable BlockState state) {
        return state != null && state.getBlock() instanceof MainBreakerBlock
                ? state.getValue(MainBreakerBlock.FACING) : null;
    }

    private static double[] authored(Stud stud) {
        return new double[] {stud == Stud.INPUT ? INPUT_X : OUTPUT_X, STUD_Y, STUD_Z};
    }

    public static Vec3 anchor(Direction facing, Stud stud) {
        final double[] p = authored(stud);
        final double[] b = MountedPart.toBlockSpace(facing, p[0], p[1], p[2]);
        return new Vec3(b[0], b[1], b[2]);
    }

    @Nullable
    public static Vec3 anchor(@Nullable BlockState state, Stud stud) {
        final Direction facing = facingOf(state);
        return facing == null ? null : anchor(facing, stud);
    }

    public static AABB box(Direction facing, Stud stud) {
        final double[] p = authored(stud);
        final double[] a = MountedPart.toBlockSpace(facing, p[0] - HALF_XZ, BOX_Y0, p[2] - HALF_XZ);
        final double[] b = MountedPart.toBlockSpace(facing, p[0] + HALF_XZ, BOX_Y1, p[2] + HALF_XZ);
        return new AABB(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
    }

    @Nullable
    public static AABB box(@Nullable BlockState state, Stud stud) {
        final Direction facing = facingOf(state);
        return facing == null ? null : box(facing, stud);
    }

    @Nullable
    public static Stud pick(@Nullable Direction facing, Vec3 local) {
        if (facing == null) {
            return null;
        }
        Stud best = null;
        double bestDistance = PICK_REACH * PICK_REACH;
        for (Stud stud : Stud.values()) {
            final Vec3 at = anchor(facing, stud);
            final double d = at.distanceToSqr(local);
            if (d < bestDistance) {
                bestDistance = d;
                best = stud;
            }
        }
        return best;
    }

    @Nullable
    public static Stud pick(@Nullable BlockState state, Vec3 local) {
        return pick(facingOf(state), local);
    }

    private static final double SELECT_X = 0.72500;
    private static final double SELECT_Y = 0.37187;
    private static final double SELECT_Z = 0.27500;
    private static final double SELECT_HALF_X = 0.06875;
    private static final double SELECT_HALF_Z = 0.05625;
    private static final double SELECT_Y0 = 0.32500;
    private static final double SELECT_Y1 = 0.41875;

    private static final double SELECT_REACH = 0.16;

    public static Vec3 selectorAnchor(Direction facing) {
        final double[] b = MountedPart.toBlockSpace(facing, SELECT_X, SELECT_Y, SELECT_Z);
        return new Vec3(b[0], b[1], b[2]);
    }

    public static AABB selectorBox(Direction facing) {
        final double[] a = MountedPart.toBlockSpace(facing, SELECT_X - SELECT_HALF_X, SELECT_Y0,
                SELECT_Z - SELECT_HALF_Z);
        final double[] b = MountedPart.toBlockSpace(facing, SELECT_X + SELECT_HALF_X, SELECT_Y1,
                SELECT_Z + SELECT_HALF_Z);
        return new AABB(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
    }

    public static boolean isOnSelector(@Nullable Direction facing, Vec3 local) {
        return facing != null
                && selectorAnchor(facing).distanceToSqr(local) < SELECT_REACH * SELECT_REACH;
    }

    public static boolean isOnSelector(@Nullable BlockState state, Vec3 local) {
        return isOnSelector(facingOf(state), local);
    }

    public static int pickIndex(@Nullable Direction facing, double lx, double ly, double lz) {
        final Stud stud = pick(facing, new Vec3(lx, ly, lz));
        return stud == null ? -1 : stud.index();
    }
}
