package com.createstats.surveyor;

import com.createstats.surveyor.core.Corner;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.SegmentCheck;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PathwayChecks {
    public static final int DROP = 5;

    public record Result(double[] ground, SegmentCheck.Level[] levels, List<SegmentCheck.Finding> findings) {
    }

    private PathwayChecks() {
    }

    public static Result check(Level level, Pathway p, PathSpline sp, double radius, double halfWidth,
                               double cx, double cz, double near) {
        int n = sp.sampleCount();
        double[] ground = new double[n];
        SegmentCheck.Level[] levels = new SegmentCheck.Level[n];
        String[] why = new String[n];
        java.util.Arrays.fill(levels, SegmentCheck.Level.FINE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double near2 = near * near;
        for (int i = 0; i < n; i++) {
            double x = sp.sampleX(i);
            double z = sp.sampleZ(i);
            if (Double.isFinite(near) && (x - cx) * (x - cx) + (z - cz) * (z - cz) > near2) {
                ground[i] = Double.NaN;
                levels[i] = SegmentCheck.Level.UNKNOWN;
                continue;
            }
            double g = groundAt(level, x, sp.sampleY(i), z, pos);
            ground[i] = g;
            if (Double.isNaN(g)) {
                if (!level.isLoaded(pos.set(x, sp.sampleY(i), z))) {
                    levels[i] = SegmentCheck.Level.UNKNOWN;
                    why[i] = "not loaded here";
                } else {
                    levels[i] = SegmentCheck.Level.RED;
                    why[i] = "a drop: no ground within " + DROP + " blocks";
                }
                continue;
            }

            BlockState under = level.getBlockState(pos.set(Math.floor(x), Math.floor(g - 0.01), Math.floor(z)));
            FluidState fluid = level.getFluidState(pos.set(Math.floor(x), Math.floor(g + 0.2), Math.floor(z)));
            if (fluid.is(FluidTags.LAVA)) {
                levels[i] = SegmentCheck.Level.RED;
                why[i] = "lava";
                continue;
            }
            if (fluid.is(FluidTags.WATER)) {
                levels[i] = SegmentCheck.Level.RED;
                why[i] = "water";
                continue;
            }

            if (i % 2 != 0) continue;
            String blocked = corridor(level, sp, i, g, halfWidth, pos);
            if (blocked != null) {
                levels[i] = SegmentCheck.Level.RED;
                why[i] = blocked;
            }
            if (under.isAir()) {
                levels[i] = levels[i].worst(SegmentCheck.Level.AMBER);
            }
        }
        List<Double> sharp = new ArrayList<>();
        for (int k = 0; k < p.nodes().size() && k < sp.nodeCount(); k++) {
            if (p.nodes().get(k).corner() == Corner.SHARP) sharp.add(sp.nodeS(k));
        }
        double[] sharpS = new double[sharp.size()];
        for (int i = 0; i < sharpS.length; i++) sharpS[i] = sharp.get(i);
        List<SegmentCheck.Finding> findings = new ArrayList<>(SegmentCheck.shape(sp, radius, ground, sharpS));
        for (SegmentCheck.Finding f : findings) {
            int a = (int) Math.floor(f.from() / sp.step());
            int b = (int) Math.ceil(f.to() / sp.step());
            for (int i = Math.max(0, a); i <= Math.min(n - 1, b); i++) {
                if (f.level().ordinal() > levels[i].ordinal()) {
                    levels[i] = f.level();
                    why[i] = f.why();
                }
            }
        }

        int start = -1;
        for (int i = 0; i <= n; i++) {
            boolean same = i < n && start >= 0 && levels[i] == levels[start] && eq(why[i], why[start]);
            if (same) continue;
            final String startWhy = start >= 0 ? why[start] : null;
            if (start >= 0 && startWhy != null && levels[start] != SegmentCheck.Level.FINE
                    && findings.stream().noneMatch(f -> eq(f.why(), startWhy))) {
                findings.add(new SegmentCheck.Finding(levels[start], sp.sampleS(start), sp.sampleS(Math.min(i, n - 1)), why[start]));
            }
            start = i < n ? i : -1;
        }
        return new Result(ground, levels, findings);
    }

    public static double groundAt(Level level, double x, double yHint, double z, BlockPos.MutableBlockPos pos) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = (int) Math.floor(yHint) + 2;
        pos.set(bx, top, bz);
        if (!level.isLoaded(pos)) return Double.NaN;
        for (int y = top; y >= top - 2 - DROP; y--) {
            pos.set(bx, y, bz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) return y + shape.max(net.minecraft.core.Direction.Axis.Y);
            if (!state.getFluidState().isEmpty()) return y + 0.9;
        }
        return Double.NaN;
    }

    private static String corridor(Level level, PathSpline sp, int i, double ground, double halfWidth, BlockPos.MutableBlockPos pos) {
        double tx = sp.tangentX(i);
        double tz = sp.tangentZ(i);
        double rx = -tz;
        double rz = tx;
        double x = sp.sampleX(i);
        double z = sp.sampleZ(i);
        int base = (int) Math.floor(ground + 0.01);
        for (double w = -halfWidth + 0.5; w <= halfWidth - 0.5 + 1.0e-6; w += 1.0) {
            int bx = (int) Math.floor(x + rx * w);
            int bz = (int) Math.floor(z + rz * w);
            for (int h = 1; h <= 3; h++) {
                pos.set(bx, base + h, bz);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                if (!state.getCollisionShape(level, pos).isEmpty()) {
                    String name = state.getBlock().getName().getString().toLowerCase(Locale.ROOT);
                    return name + " at " + bx + " " + (base + h) + " " + bz;
                }
            }
        }
        return null;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
