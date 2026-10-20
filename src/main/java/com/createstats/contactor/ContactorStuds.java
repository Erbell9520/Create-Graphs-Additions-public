package com.createstats.contactor;

import com.createstats.block.ContactorBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

// geometry here not in the renderer, three things hang off it
public final class ContactorStuds {
    private ContactorStuds() {
    }

    public static final double PICK_REACH = 2.0 / 16.0;

    @Nullable
    public static Direction facingOf(@Nullable BlockState state) {
        return state != null && state.getBlock() instanceof ContactorBlock ? state.getValue(ContactorBlock.FACING) : null;
    }

    public static int spinOf(@Nullable BlockState state) {
        return state != null && state.getBlock() instanceof ContactorBlock ? ContactorBlock.spin(state) : 0;
    }

    public static Vec3 anchor(Direction facing, int spin, int stud) {
        final double[] a = ContactorGeometry.studAnchor(stud);
        final double[] b = ContactorFrame.toBlock(facing, spin, a[0], a[1], a[2]);
        return new Vec3(b[0], b[1], b[2]);
    }

    @Nullable
    public static Vec3 anchor(@Nullable BlockState state, int stud) {
        final Direction facing = facingOf(state);
        return facing == null || !ContactorLogic.isStud(stud) ? null : anchor(facing, spinOf(state), stud);
    }

    public static AABB box(Direction facing, int spin, int stud) {
        final double[] a = ContactorGeometry.studAnchor(stud);
        final double h = ContactorGeometry.BOX_HALF;
        return turned(facing, spin, a[0] - h, a[1] - h, ContactorGeometry.BOX_Z0,
                a[0] + h, a[1] + h, ContactorGeometry.BOX_Z1);
    }

    public static int pick(Direction facing, int spin, Vec3 local) {
        int best = -1;
        double bestDistance = PICK_REACH * PICK_REACH;
        for (int stud = 0; stud < ContactorLogic.STUDS; stud++) {
            final double d = anchor(facing, spin, stud).distanceToSqr(local);
            if (d < bestDistance) {
                bestDistance = d;
                best = stud;
            }
        }
        return best;
    }

    public static int pick(@Nullable BlockState state, Vec3 local) {
        final Direction facing = facingOf(state);
        return facing == null ? -1 : pick(facing, spinOf(state), local);
    }

    private static final Map<Direction, VoxelShape[]> SHAPES = new EnumMap<>(Direction.class);

    public static synchronized VoxelShape shape(Direction facing, int spin) {
        final VoxelShape[] turns = SHAPES.computeIfAbsent(facing, f -> new VoxelShape[4]);
        final int s = ContactorFrame.effectiveSpin(facing, spin);
        if (turns[s] == null) {
            VoxelShape out = Shapes.empty();
            for (double[] b : ContactorGeometry.SHAPE) {
                final AABB box = turned(facing, s, b[0], b[1], b[2], b[3], b[4], b[5]);
                out = Shapes.or(out, Shapes.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
            }
            turns[s] = out.optimize();
        }
        return turns[s];
    }

    static AABB turned(Direction facing, int spin, double x0, double y0, double z0, double x1, double y1, double z1) {
        final double[] a = ContactorFrame.toBlock(facing, spin, x0, y0, z0);
        final double[] b = ContactorFrame.toBlock(facing, spin, x1, y1, z1);
        return new AABB(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
    }
}
