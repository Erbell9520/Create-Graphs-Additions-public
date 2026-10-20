package com.createstats.client.ponder.fe;

import com.createstats.power.CableTier;
import com.createstats.power.PduStuds;
import com.createstats.power.WireTension;
import com.createstats.renderer.PowerConnectorRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class FePaths {
    private FePaths() {
    }

    public static final class Path {
        private final Vec3[] points;
        private final double[] along;

        public Path(List<Vec3> points) {
            this.points = points.toArray(new Vec3[0]);
            this.along = new double[this.points.length];
            for (int i = 1; i < this.points.length; i++) {
                along[i] = along[i - 1] + this.points[i].distanceTo(this.points[i - 1]);
            }
        }

        public double length() {
            return along.length == 0 ? 0.0 : along[along.length - 1];
        }

        public List<Vec3> points() {
            return List.of(points);
        }

        public Vec3 at(double s) {
            if (points.length == 0) return Vec3.ZERO;
            if (s <= 0.0) return points[0];
            if (s >= length()) return points[points.length - 1];
            int lo = 0;
            int hi = along.length - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (along[mid] <= s) lo = mid;
                else hi = mid;
            }
            final double span = along[hi] - along[lo];
            final double k = span <= 1.0e-9 ? 0.0 : (s - along[lo]) / span;
            return points[lo].lerp(points[hi], k);
        }

        public Path from(double from) {
            final List<Vec3> out = new ArrayList<>();
            out.add(at(from));
            for (int i = 0; i < points.length; i++) if (along[i] > from) out.add(points[i]);
            return new Path(out);
        }

        public Vec3 start() {
            return at(0.0);
        }

        public Vec3 end() {
            return at(length());
        }
    }

    public static double beadHeight(CableTier tier) {
        return (tier.thicknessPx() + 0.9) / 16.0;
    }

    public static Path floorRun(Vec3 from, BlockPos[] blocks, Vec3 to, CableTier tier) {
        final double lift = beadHeight(tier);
        final List<Vec3> pts = new ArrayList<>();
        pts.add(new Vec3(from.x, blocks[0].getY() + lift, from.z));
        for (BlockPos b : blocks) pts.add(new Vec3(b.getX() + 0.5, b.getY() + lift, b.getZ() + 0.5));
        pts.add(new Vec3(to.x, blocks[blocks.length - 1].getY() + lift, to.z));
        return new Path(pts);
    }

    public static Vec3 faceCentre(BlockPos pos, Direction face) {
        return new Vec3(pos.getX() + 0.5 + face.getStepX() * 0.5, pos.getY() + 0.5 + face.getStepY() * 0.5,
                pos.getZ() + 0.5 + face.getStepZ() * 0.5);
    }

    public static Vec3 studPoint(BlockPos board, Direction facing, int stud) {
        final double[] box = PduStuds.boxOf(facing, stud);
        return new Vec3(board.getX() + (box[0] + box[2]) * 0.5, board.getY() + PduStuds.topY(),
                board.getZ() + (box[1] + box[3]) * 0.5);
    }

    public static Vec3 attach(BlockGetter level, BlockPos pos) {
        final Vec3 local = PowerConnectorRenderer.attachPoint(level.getBlockState(pos));
        return new Vec3(pos.getX() + local.x, pos.getY() + local.y, pos.getZ() + local.z);
    }

    public static Path leg(BlockGetter level, BlockPos board, Direction facing, int stud, BlockPos far) {
        return new Path(catenary(studPoint(board, facing, stud), attach(level, far), WireTension.STANDARD.slack()));
    }

    public static int segments(double dist) {
        return Math.max(6, Math.min(48, (int) Math.round(dist / 0.5)));
    }

    public static List<Vec3> catenary(Vec3 a, Vec3 b, double slack) {
        final double dx = b.x - a.x;
        final double dy = b.y - a.y;
        final double dz = b.z - a.z;
        final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        final int segs = segments(dist);
        final List<Vec3> out = new ArrayList<>(segs + 1);
        final double horLength = Math.sqrt(dx * dx + dz * dz);
        final double wireLength = dist * slack;
        final double goal = horLength < 1.0e-4 ? 0.0
                : Math.sqrt(Math.max(1.0e-9, wireLength * wireLength - dy * dy)) / horLength;
        if (horLength < 1.0e-4 || goal <= 1.0) {
            for (int i = 0; i <= segs; i++) {
                final double t = (double) i / segs;
                out.add(new Vec3(a.x + dx * t, a.y + dy * t, a.z + dz * t));
            }
            return out;
        }
        double lower = 0.0;
        double upper = 1.0;
        while (Math.sinh(upper) / upper < goal) {
            lower = upper;
            upper *= 2.0;
        }
        for (int it = 0; it < 24; it++) {
            final double mid = (lower + upper) / 2.0;
            if (Math.sinh(mid) / mid < goal) lower = mid;
            else upper = mid;
        }
        final double l = (lower + upper) / 2.0;
        final double scale = horLength / (2.0 * l);
        final double offsetX = (horLength - scale * Math.log((wireLength + dy) / (wireLength - dy))) * 0.5;
        final double offsetY = (dy - wireLength * Math.cosh(l) / Math.sinh(l)) * 0.5;
        for (int i = 0; i <= segs; i++) {
            final double t = (double) i / segs;
            final double y = scale * Math.cosh((horLength * t - offsetX) / scale) + offsetY;
            out.add(new Vec3(a.x + dx * t, a.y + y, a.z + dz * t));
        }
        return out;
    }
}
