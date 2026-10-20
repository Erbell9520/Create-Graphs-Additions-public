package com.createstats.client.ponder.powertrack;

import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.RailMesher;
import com.createstats.powertrack.core.SectionGraph;

import java.util.ArrayList;
import java.util.List;

public final class PowerTrackStage {
    private PowerTrackStage() {
    }

    public static final int TRACK_Y = 1;

    public static final int LINE_Z = 7;

    public static final int[] LINE_FLOOR = {21, 15};
    public static final int LINE_PLATE = 15;
    public static final int LINE_PLATE_X = 6;

    public static final int[] LINE_X = {6, 20};

    public static final int[] FEEDER_A = {9, 1, 5};
    public static final int[] CABLE_A = {10, 1, 5};
    public static final int[] BANK_A = {11, 1, 5};
    public static final int[] FEEDER_B = {17, 1, 5};
    public static final int[] CABLE_B = {18, 1, 5};
    public static final int[] BANK_B = {19, 1, 5};

    public static final int[] INSULATOR = {13, 1, 5};

    public static final int LINE_CAR_X = 0;
    public static final int[] CAR_BOGIES = {0, 6};
    public static final int[] CAR_COLLECTORS = {2, 4};
    public static final int CAR_LENGTH = 7;
    public static final int CAR_DECK_Y = 3;
    public static final int CAR_BANK_X = 3;

    public static final float LINE_SCALE = 0.6f;
    public static final float LINE_OFFSET_Y = -1.0f;
    public static final float LINE_YAW = 145f;
    public static final float LINE_PITCH = -30f;

    public static final int[] YARD_FLOOR = {21, 15};
    public static final int YARD_PLATE = 15;
    public static final int YARD_PLATE_X = 3;
    public static final int[] YARD_X = {0, 20};
    public static final int[] YARD_FEEDER_A = {1, 1, 9};
    public static final int[] YARD_CABLE_A = {1, 1, 10};
    public static final int[] YARD_BANK_A = {1, 1, 11};
    public static final int[] YARD_FEEDER_B = {18, 1, 9};
    public static final int[] YARD_CABLE_B = {18, 1, 10};
    public static final int[] YARD_BANK_B = {18, 1, 11};
    public static final int[] YARD_INSULATOR = {4, 1, 5};

    public static final int[] YARD_TURN_FROM = {8, 1, 7};
    public static final int[] YARD_TURN_TO = {14, 1, 1};
    public static final int[] YARD_BRANCH_END = {14, 1, 0};
    public static final int YARD_SLOPE_Z = 12;
    public static final int[] YARD_SLOPE_LOW = {6, 10};
    public static final int[] YARD_SLOPE_RISE = {11, 14};
    public static final int[] YARD_SLOPE_HIGH = {15, 20};
    public static final int YARD_SLOPE_TOP_Y = 5;

    public static final int YARD_CAR_X = 6;
    public static final float YARD_SCALE = 0.5f;
    public static final float YARD_OFFSET_Y = -1.5f;
    public static final float YARD_YAW = 160f;
    public static final float YARD_PITCH = -32f;

    public static final int LINE_WEST = 1;
    public static final int LINE_EAST = 2;

    public static final int YARD_WEST = 1;
    public static final int YARD_JUNCTION = 2;
    public static final int YARD_EAST = 3;
    public static final int YARD_CURVE_END = 4;
    public static final int YARD_BRANCH = 5;

    public static final int YARD_SLOPE_START = 6;
    public static final int YARD_SLOPE_FOOT = 7;
    public static final int YARD_SLOPE_CREST = 8;
    public static final int YARD_SLOPE_END = 9;

    public record Tap(int lo, int hi, double at, int side) {
        public RailMesher.Tap mesh() {
            return new RailMesher.Tap(lo, hi, at, side);
        }
    }

    public record Stage(RailLayout layout, List<Tap> taps, List<SectionGraph.Cut> cuts) {
        public List<RailMesher.Tap> meshTaps() {
            final List<RailMesher.Tap> out = new ArrayList<>();
            for (Tap t : taps) {
                out.add(t.mesh());
            }
            return out;
        }
    }

    public static double[] lineNode(int id) {
        return id == LINE_WEST ? new double[] {LINE_X[0], TRACK_Y, LINE_Z + 0.5}
                : new double[] {LINE_X[1] + 1, TRACK_Y, LINE_Z + 0.5};
    }

    public static double lineLength() {
        return LINE_X[1] + 1 - LINE_X[0];
    }

    public static double lineAt(int blockX) {
        return blockX + 0.5 - LINE_X[0];
    }

    public static Stage line(boolean cut) {
        final double[] w = lineNode(LINE_WEST);
        final double[] e = lineNode(LINE_EAST);
        final List<RailLayout.EdgeIn> edges = List.of(
                new RailLayout.EdgeIn(LINE_WEST, LINE_EAST, lineLength(), true, straight(w, e)));
        final List<SectionGraph.Cut> cuts = cut
                ? List.of(new SectionGraph.Cut(LINE_WEST, LINE_EAST, lineAt(INSULATOR[0]))) : List.of();
        final List<Tap> taps = List.of(
                new Tap(LINE_WEST, LINE_EAST, lineAt(FEEDER_A[0]), side(FEEDER_A, LINE_Z)),
                new Tap(LINE_WEST, LINE_EAST, lineAt(FEEDER_B[0]), side(FEEDER_B, LINE_Z)));
        return new Stage(RailLayout.build(edges, cuts), taps, cuts);
    }

    public static double[] yardNode(int id) {
        return switch (id) {
            case YARD_WEST -> new double[] {YARD_X[0], TRACK_Y, LINE_Z + 0.5};
            case YARD_JUNCTION -> new double[] {YARD_TURN_FROM[0] + 1, TRACK_Y, LINE_Z + 0.5};
            case YARD_EAST -> new double[] {YARD_X[1] + 1, TRACK_Y, LINE_Z + 0.5};
            case YARD_CURVE_END -> new double[] {YARD_TURN_TO[0] + 0.5, TRACK_Y, YARD_TURN_TO[2] + 1};
            case YARD_BRANCH -> new double[] {YARD_BRANCH_END[0] + 0.5, TRACK_Y, YARD_BRANCH_END[2]};
            case YARD_SLOPE_START -> new double[] {YARD_SLOPE_LOW[0], TRACK_Y, YARD_SLOPE_Z + 0.5};
            case YARD_SLOPE_FOOT -> new double[] {YARD_SLOPE_RISE[0], TRACK_Y, YARD_SLOPE_Z + 0.5};
            case YARD_SLOPE_CREST -> new double[] {YARD_SLOPE_RISE[1] + 1, YARD_SLOPE_TOP_Y, YARD_SLOPE_Z + 0.5};
            case YARD_SLOPE_END -> new double[] {YARD_SLOPE_HIGH[1] + 1, YARD_SLOPE_TOP_Y, YARD_SLOPE_Z + 0.5};
            default -> throw new IllegalArgumentException("no yard node " + id);
        };
    }

    public static Curve yardCurve() {
        final double[] from = yardNode(YARD_JUNCTION);
        final double[] to = yardNode(YARD_CURVE_END);
        return new Curve(from, new double[] {1, 0, 0}, to, new double[] {0, 0, 1});
    }

    public static double yardWestAt(int blockX) {
        return blockX + 0.5 - YARD_X[0];
    }

    public static double yardEastAt(int blockX) {
        return blockX + 0.5 - yardNode(YARD_JUNCTION)[0];
    }

    public static Stage yard() {
        final double[] w = yardNode(YARD_WEST);
        final double[] j = yardNode(YARD_JUNCTION);
        final double[] e = yardNode(YARD_EAST);
        final double[] c = yardNode(YARD_CURVE_END);
        final double[] n = yardNode(YARD_BRANCH);
        final double[] s1 = yardNode(YARD_SLOPE_START);
        final double[] f = yardNode(YARD_SLOPE_FOOT);
        final double[] k = yardNode(YARD_SLOPE_CREST);
        final double[] s2 = yardNode(YARD_SLOPE_END);
        final Curve curve = yardCurve();
        final List<RailLayout.EdgeIn> edges = List.of(
                new RailLayout.EdgeIn(YARD_WEST, YARD_JUNCTION, dist(w, j), true, straight(w, j)),
                new RailLayout.EdgeIn(YARD_JUNCTION, YARD_EAST, dist(j, e), true, straight(j, e)),
                new RailLayout.EdgeIn(YARD_JUNCTION, YARD_CURVE_END, curve.length(), true, curve.samples()),
                new RailLayout.EdgeIn(YARD_CURVE_END, YARD_BRANCH, dist(c, n), true, straight(c, n)),
                new RailLayout.EdgeIn(YARD_SLOPE_START, YARD_SLOPE_FOOT, dist(s1, f), true, straight(s1, f)),
                new RailLayout.EdgeIn(YARD_SLOPE_FOOT, YARD_SLOPE_CREST, dist(f, k), true, straight(f, k)),
                new RailLayout.EdgeIn(YARD_SLOPE_CREST, YARD_SLOPE_END, dist(k, s2), true, straight(k, s2)));
        final List<SectionGraph.Cut> cuts = List.of(new SectionGraph.Cut(YARD_WEST, YARD_JUNCTION, yardWestAt(YARD_INSULATOR[0])));
        final List<Tap> taps = List.of(
                new Tap(YARD_WEST, YARD_JUNCTION, yardWestAt(YARD_FEEDER_A[0]), side(YARD_FEEDER_A, LINE_Z)),
                new Tap(YARD_JUNCTION, YARD_EAST, yardEastAt(YARD_FEEDER_B[0]), side(YARD_FEEDER_B, LINE_Z)));
        return new Stage(RailLayout.build(edges, cuts), taps, cuts);
    }

    public static int[] cableOf(int[] feeder) {
        for (int[][] st : new int[][][] {{FEEDER_A, CABLE_A}, {FEEDER_B, CABLE_B}, {YARD_FEEDER_A, YARD_CABLE_A},
                {YARD_FEEDER_B, YARD_CABLE_B}}) {
            if (java.util.Arrays.equals(st[0], feeder)) {
                return st[1];
            }
        }
        throw new IllegalArgumentException("no station at " + java.util.Arrays.toString(feeder));
    }

    public static double[] onDoor(int[] feeder, double u, double v, double w) {
        final int[] cable = cableOf(feeder);
        final int dx = feeder[0] - cable[0];
        final int dz = feeder[2] - cable[2];
        final double x;
        final double z;
        if (dz < 0) {
            x = u;
            z = w;
        } else if (dx < 0) {
            x = w;
            z = 1.0 - u;
        } else if (dz > 0) {
            x = 1.0 - u;
            z = 1.0 - w;
        } else {
            x = 1.0 - w;
            z = u;
        }
        return new double[] {feeder[0] + x, feeder[1] + v, feeder[2] + z};
    }

    public static int side(int[] feeder, int lineZ) {
        return -(feeder[2] + 0.5 - (lineZ + 0.5)) >= 0 ? 1 : -1;
    }

    public static double[] straight(double[] a, double[] b) {
        return new double[] {a[0], a[1], a[2], b[0], b[1], b[2]};
    }

    public static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    public static boolean inside(int[][] box, int x, int y, int z) {
        return x >= box[0][0] && x <= box[1][0] && y >= box[0][1] && y <= box[1][1] && z >= box[0][2] && z <= box[1][2];
    }

    public static int trailSign(int collectorX) {
        int best = Integer.MAX_VALUE;
        int nearest = 0;
        for (int b : CAR_BOGIES) {
            final int d = Math.abs(b - collectorX);
            if (d < best) {
                best = d;
                nearest = b;
            }
        }
        return collectorX >= nearest ? 1 : -1;
    }

    public static final class Curve {
        private final double[] start1;
        private final double[] start2;
        private final double[] finish1;
        private final double[] finish2;
        private final double handle;
        private final double length;

        public Curve(double[] start1, double[] axis1, double[] start2, double[] axis2) {
            this.start1 = start1.clone();
            this.start2 = start2.clone();
            final double[] a1 = unit(axis1);
            final double[] a2 = unit(axis2);
            this.handle = handleLength(start1, start2, a1, a2);
            this.finish1 = new double[] {start1[0] + a1[0] * handle, start1[1] + a1[1] * handle, start1[2] + a1[2] * handle};
            this.finish2 = new double[] {start2[0] + a2[0] * handle, start2[1] + a2[1] * handle, start2[2] + a2[2] * handle};
            double len = 0.0;
            double[] prev = at(0.0);
            for (int i = 1; i <= 16; i++) {
                final double[] p = at(i / 16.0);
                len += dist(p, prev);
                prev = p;
            }
            this.length = len;
        }

        public double handle() {
            return handle;
        }

        public double length() {
            return length;
        }

        public double[] at(double t) {
            final double[] v1 = lerp(t, start1, finish1);
            final double[] v2 = lerp(t, finish1, finish2);
            final double[] v3 = lerp(t, finish2, start2);
            final double[] i1 = lerp(t, v1, v2);
            final double[] i2 = lerp(t, v2, v3);
            return lerp(t, i1, i2);
        }

        public double[] samples() {
            final int n = Math.max(3, (int) Math.ceil(length * 4.0) + 1);
            final double[] xyz = new double[3 * n];
            for (int k = 0; k < n; k++) {
                final double[] p = at(k / (double) (n - 1));
                xyz[3 * k] = p[0];
                xyz[3 * k + 1] = p[1];
                xyz[3 * k + 2] = p[2];
            }
            return xyz;
        }

        private static double handleLength(double[] end1, double[] end2, double[] axis1, double[] axis2) {
            final double a1 = Math.atan2(-axis2[2], -axis2[0]);
            final double a2 = Math.atan2(axis1[2], axis1[0]);
            final double circle = Math.PI * 2;
            double angle = (a1 - a2 + circle) % circle;
            if (Math.abs(circle - angle) < Math.abs(angle)) {
                angle = circle - angle;
            }

            final double[] cross1 = {-axis1[2], 0, axis1[0]};
            final double[] cross2 = {-axis2[2], 0, axis2[0]};
            if (Math.abs(angle) < 1.0e-5) {
                final double[] hit = intersect(end1, end2, axis1, cross2);
                if (hit != null) {
                    final double min = Math.min(Math.abs(hit[0]), Math.abs(hit[1]));
                    final double max = Math.max(Math.abs(hit[0]), Math.abs(hit[1]));
                    if (min > 1.2 && max / min > 1.0 && max / min < 3.0) {
                        return max - min;
                    }
                }
                return dist(end1, end2) / 3.0;
            }
            final double n = circle / angle;
            final double factor = 4.0 / 3.0 * Math.tan(Math.PI / (2.0 * n));
            final double[] hit = intersect(end1, end2, cross1, cross2);
            if (hit == null) {
                return dist(end1, end2) / 3.0;
            }
            final double radius = Math.abs(hit[1]);
            final double h = radius * factor;
            return Math.abs(h) < 1.0e-5 ? 1.0 : h;
        }

        private static double[] intersect(double[] p1, double[] p2, double[] r, double[] s) {
            final double qx = p2[0] - p1[0];
            final double qz = p2[2] - p1[2];
            final double rcs = r[0] * s[2] - r[2] * s[0];
            if (Math.abs(rcs) < 1.0e-5) {
                return null;
            }
            final double t = qx * (s[2] / rcs) - qz * (s[0] / rcs);
            final double u = qx * (r[2] / rcs) - qz * (r[0] / rcs);
            return new double[] {t, u};
        }

        private static double[] lerp(double t, double[] a, double[] b) {
            return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
        }

        private static double[] unit(double[] v) {
            final double m = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            return m < 1.0e-9 ? v.clone() : new double[] {v[0] / m, v[1] / m, v[2] / m};
        }
    }
}
