package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// not a catenary, cords lie on furniture
public final class CordRoute {
    private CordRoute() {
    }

    public static final double MAX_REACH = 5.0;

    private static final int SEGMENTS = 20;

    private static final double SLACK = 1.04;

    private static final double GRAVITY = 0.002;

    private static final int ITERATIONS = 40;

    private static final int CONSTRAINT_PASSES = 8;

    private static final double CLEARANCE = 0.035;

    private static final double MAX_ESCAPE = 0.75;

    @FunctionalInterface
    public interface Solid {
        @Nullable
        AABB at(double x, double y, double z);
    }

    @Nullable
    public static List<Vec3> settle(Solid world, Vec3 from, Vec3 to) {
        final double span = from.distanceTo(to);
        if (span < 1.0e-3 || span > MAX_REACH) {
            return null;
        }
        final double rest = span * SLACK / SEGMENTS;
        final double[][] p = catenary(from, to, span * SLACK);

        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            for (int i = 1; i < SEGMENTS; i++) {
                p[i][1] -= GRAVITY;
            }
            for (int pass = 0; pass < CONSTRAINT_PASSES; pass++) {
                constrain(p, rest);
            }
            for (int i = 1; i < SEGMENTS; i++) {
                push(world, p[i]);
            }
        }

        for (int i = 0; i <= SEGMENTS; i++) {
            if (world.at(p[i][0], p[i][1], p[i][2]) != null) {
                return null;
            }
        }
        return simplify(p);
    }

    private static double[][] catenary(Vec3 from, Vec3 to, double length) {
        final double dx = to.x - from.x;
        final double dy = to.y - from.y;
        final double dz = to.z - from.z;
        final double horizontal = Math.sqrt(dx * dx + dz * dz);
        final double[][] out = new double[SEGMENTS + 1][3];
        final double goal = horizontal < 1.0e-4 ? 0.0
                : Math.sqrt(Math.max(1.0e-9, length * length - dy * dy)) / horizontal;
        if (horizontal < 1.0e-4 || goal <= 1.0) {
            for (int i = 0; i <= SEGMENTS; i++) {
                final double t = (double) i / SEGMENTS;
                out[i] = new double[] {from.x + dx * t, from.y + dy * t, from.z + dz * t};
            }
            return out;
        }
        double lower = 0.0;
        double upper = 1.0;
        while (Math.sinh(upper) / upper < goal) {
            lower = upper;
            upper *= 2.0;
        }
        for (int step = 0; step < 24; step++) {
            final double mid = (lower + upper) / 2.0;
            if (Math.sinh(mid) / mid < goal) {
                lower = mid;
            } else {
                upper = mid;
            }
        }
        final double l = (lower + upper) / 2.0;
        final double scale = horizontal / (2.0 * l);
        final double offsetX = (horizontal - scale * Math.log((length + dy) / (length - dy))) * 0.5;
        final double offsetY = (dy - length * Math.cosh(l) / Math.sinh(l)) * 0.5;
        for (int i = 0; i <= SEGMENTS; i++) {
            final double t = (double) i / SEGMENTS;
            final double y = scale * Math.cosh((horizontal * t - offsetX) / scale) + offsetY;
            out[i] = new double[] {from.x + dx * t, from.y + y, from.z + dz * t};
        }
        return out;
    }

    private static void constrain(double[][] p, double rest) {
        for (int i = 0; i < p.length - 1; i++) {
            final double[] a = p[i];
            final double[] b = p[i + 1];
            final double dx = b[0] - a[0];
            final double dy = b[1] - a[1];
            final double dz = b[2] - a[2];
            final double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d <= rest || d < 1.0e-9) {
                continue;
            }
            final double excess = (d - rest) / d;

            final boolean aFree = i > 0;
            final boolean bFree = i + 1 < p.length - 1;
            final double aShare = aFree ? (bFree ? 0.5 : 1.0) : 0.0;
            final double bShare = bFree ? (aFree ? 0.5 : 1.0) : 0.0;
            a[0] += dx * excess * aShare;
            a[1] += dy * excess * aShare;
            a[2] += dz * excess * aShare;
            b[0] -= dx * excess * bShare;
            b[1] -= dy * excess * bShare;
            b[2] -= dz * excess * bShare;
        }
    }

    private static void push(Solid world, double[] point) {
        final AABB box = world.at(point[0], point[1], point[2]);
        if (box == null) {
            return;
        }
        final double[] outs = {
                box.minX - CLEARANCE - point[0], box.maxX + CLEARANCE - point[0],
                box.minY - CLEARANCE - point[1], box.maxY + CLEARANCE - point[1],
                box.minZ - CLEARANCE - point[2], box.maxZ + CLEARANCE - point[2],
        };
        int best = 0;
        for (int i = 1; i < outs.length; i++) {
            if (Math.abs(outs[i]) < Math.abs(outs[best])) {
                best = i;
            }
        }
        if (Math.abs(outs[best]) > MAX_ESCAPE) {
            return;
        }
        point[best / 2] += outs[best];
    }

    private static final double SIMPLIFY_TOLERANCE = 0.02;

    private static List<Vec3> simplify(double[][] p) {
        final boolean[] keep = new boolean[p.length];
        keep[0] = true;
        keep[p.length - 1] = true;
        douglasPeucker(p, 0, p.length - 1, keep);
        final List<Vec3> out = new ArrayList<>();
        for (int i = 0; i < p.length; i++) {
            if (keep[i]) {
                out.add(new Vec3(p[i][0], p[i][1], p[i][2]));
            }
        }
        return out;
    }

    private static void douglasPeucker(double[][] p, int first, int last, boolean[] keep) {
        if (last <= first + 1) {
            return;
        }
        int worst = -1;
        double worstOff = SIMPLIFY_TOLERANCE;
        for (int i = first + 1; i < last; i++) {
            final double off = offLine(p[first], p[i], p[last]);
            if (off > worstOff) {
                worstOff = off;
                worst = i;
            }
        }
        if (worst < 0) {
            return;
        }
        keep[worst] = true;
        douglasPeucker(p, first, worst, keep);
        douglasPeucker(p, worst, last, keep);
    }

    private static double offLine(double[] a, double[] b, double[] c) {
        final double ax = c[0] - a[0];
        final double ay = c[1] - a[1];
        final double az = c[2] - a[2];
        final double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1.0e-9) {
            return Double.MAX_VALUE;
        }
        final double bx = b[0] - a[0];
        final double by = b[1] - a[1];
        final double bz = b[2] - a[2];
        final double cx = ay * bz - az * by;
        final double cy = az * bx - ax * bz;
        final double cz = ax * by - ay * bx;
        return Math.sqrt(cx * cx + cy * cy + cz * cz) / len;
    }

    public static Solid worldAround(BlockGetter level, BlockPos socket, BlockPos machine) {
        return (x, y, z) -> {
            final BlockPos at = BlockPos.containing(x, y, z);
            if (at.equals(socket) || at.equals(machine)) {
                return null;
            }
            final VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at,
                    CollisionContext.empty());
            if (shape.isEmpty()) {
                return null;
            }
            for (AABB local : shape.toAabbs()) {
                final AABB box = local.move(at);
                if (x > box.minX && x < box.maxX && y > box.minY && y < box.maxY
                        && z > box.minZ && z < box.maxZ) {
                    return box;
                }
            }
            return null;
        };
    }

    @Nullable
    public static List<Vec3> plan(BlockGetter level, BlockPos socket, BlockPos machine,
                                  Vec3 from, Vec3 to) {
        return settle(worldAround(level, socket, machine), from, to);
    }
}
