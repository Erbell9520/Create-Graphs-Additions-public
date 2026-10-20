package com.createstats.surveyor.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PathSurvey {
    public static final double TRACK = 1.5;

    public static final int HEADROOM_MAX = 8;

    public static final double BUMP = 0.3;

    public static final double CROSS_AMBER = 0.3;
    public static final double CROSS_RED = 0.6;

    public enum Fix {
        NONE, MOVE_NODE, FAIR, AMPHIBIOUS, CLEAR, WIDEN
    }

    public record Issue(SegmentCheck.Level level, double from, double to, String why, Fix fix, int node) {
    }

    public record Wall(double ahead, int sample, Ground ground, String what) {
    }

    private final long version;
    private final PathSpline sp;
    private final int n;
    private final float[] ground;
    private final float[] left;
    private final float[] right;
    private final byte[] cls;
    private final byte[] head;
    private final long[] stamp;
    private final Map<Integer, String> detail = new HashMap<>();
    private int surveyed;

    public PathSurvey(long version, PathSpline sp) {
        this.version = version;
        this.sp = sp;
        this.n = sp.sampleCount();
        this.ground = new float[n];
        this.left = new float[n];
        this.right = new float[n];
        this.cls = new byte[n];
        this.head = new byte[n];
        this.stamp = new long[n];
        Arrays.fill(ground, Float.NaN);
        Arrays.fill(left, Float.NaN);
        Arrays.fill(right, Float.NaN);
        Arrays.fill(head, (byte) -1);
        Arrays.fill(stamp, Long.MIN_VALUE);
    }

    public long version() {
        return version;
    }

    public PathSpline spline() {
        return sp;
    }

    public int size() {
        return n;
    }

    public int surveyedCount() {
        return surveyed;
    }

    public boolean complete() {
        return surveyed >= n;
    }

    public void set(int i, double groundY, double leftY, double rightY, Ground g, int headroom, String what, long tick) {
        if (i < 0 || i >= n) return;
        if (stamp[i] == Long.MIN_VALUE) surveyed++;
        ground[i] = (float) groundY;
        left[i] = (float) leftY;
        right[i] = (float) rightY;
        cls[i] = (byte) g.ordinal();
        head[i] = (byte) Math.max(-1, Math.min(HEADROOM_MAX, headroom));
        stamp[i] = tick;
        if (what != null && !what.isEmpty()) detail.put(i, what);
        else detail.remove(i);
    }

    public Ground ground(int i) {
        return i >= 0 && i < n && stamp[i] != Long.MIN_VALUE ? Ground.byOrdinal(cls[i]) : Ground.UNKNOWN;
    }

    public double groundY(int i) {
        return i >= 0 && i < n ? ground[i] : Double.NaN;
    }

    public int headroom(int i) {
        return i >= 0 && i < n ? head[i] : -1;
    }

    public long stamp(int i) {
        return i >= 0 && i < n ? stamp[i] : Long.MIN_VALUE;
    }

    public double grade(int i) {
        int a = offset(i, -4);
        int b = offset(i, 4);
        if (a < 0 || b < 0) return Double.NaN;
        double run = sp.closed() ? 8 * sp.step() : (b - a) * sp.step();
        return run < 1.0e-6 ? 0.0 : (ground[b] - ground[a]) / run;
    }

    public double crossSlope(int i) {
        if (i < 0 || i >= n || Float.isNaN(left[i]) || Float.isNaN(right[i])) return Double.NaN;
        return (left[i] - right[i]) / (2.0 * TRACK);
    }

    public double bump(int i) {
        int a = offset(i, -2);
        int b = offset(i, 2);
        if (a < 0 || b < 0 || Float.isNaN(ground[i])) return Double.NaN;
        return ground[i] - 0.5 * (ground[a] + ground[b]);
    }

    private int offset(int i, int d) {
        int j = i + d;
        if (sp.closed()) j = Math.floorMod(j, n);
        else if (j < 0 || j >= n) return -1;
        return Float.isNaN(ground[j]) ? -1 : j;
    }

    public Wall firstWall(double s, double reach, boolean amphibious, double height, Pathway p) {
        if (n == 0) return null;
        double step = sp.step();
        for (double d = 0.0; d <= reach; d += step) {
            double at = s + d;
            if (!sp.closed() && at > sp.length() + 1.0e-9) break;
            int i = index(at);
            if (stamp[i] == Long.MIN_VALUE) continue;
            Ground g = Ground.byOrdinal(cls[i]);
            if (g.wallFor(amphibious)) return new Wall(d, i, g, words(i, g, p));
            if (head[i] >= 0 && head[i] < HEADROOM_MAX && head[i] + 1.0e-9 < height) {
                return new Wall(d, i, Ground.BLOCKED, "a ceiling " + head[i] + " blocks up " + near(i, p));
            }
        }
        return null;
    }

    public double firstUnknown(double s, double reach) {
        double step = sp.step();
        for (double d = 0.0; d <= reach; d += step) {
            double at = s + d;
            if (!sp.closed() && at > sp.length() + 1.0e-9) break;
            if (stamp[index(at)] == Long.MIN_VALUE) return d;
        }
        return Double.POSITIVE_INFINITY;
    }

    public double grip(int i) {
        return ground(i).grip;
    }

    public SpeedPlan.Terrain terrain() {
        double[] grip = new double[n];
        double[] grade = new double[n];
        double[] cross = new double[n];
        double[] bump = new double[n];
        for (int i = 0; i < n; i++) {
            boolean known = stamp[i] != Long.MIN_VALUE;
            grip[i] = known ? Ground.byOrdinal(cls[i]).grip : 1.0;
            grade[i] = known ? grade(i) : Double.NaN;
            cross[i] = known ? crossSlope(i) : Double.NaN;
            bump[i] = known ? bump(i) : Double.NaN;
        }
        return new SpeedPlan.Terrain(grip, grade, cross, bump);
    }

    public int index(double s) {
        if (n <= 1) return 0;
        int i = (int) Math.floor(sp.wrap(s) / sp.step() + 0.5);
        return sp.closed() ? Math.floorMod(i, n) : Math.max(0, Math.min(n - 1, i));
    }

    private String words(int i, Ground g, Pathway p) {
        String d = detail.get(i);
        if (g == Ground.BLOCKED && d != null) return d;
        String at = near(i, p);
        return switch (g) {
            case WATER -> "water " + at;
            case LAVA -> "lava " + at;
            case DROP -> "a drop " + at;
            case HURT -> (d != null ? d : "a block that hurts") + " " + at;
            case BLOCKED -> "a block in the way " + at;
            default -> g.key + " " + at;
        };
    }

    private String near(int i, Pathway p) {
        if (p == null) return "";
        int k = sp.nearestNode(sp.sampleS(i));
        return k >= 0 && k < p.nodes().size() ? "at " + p.label(p.nodes().get(k).id()) : "";
    }

    public List<Issue> issues(Pathway p, boolean amphibious, double height) {
        SegmentCheck.Level[] level = new SegmentCheck.Level[n];
        String[] why = new String[n];
        Fix[] fix = new Fix[n];
        Arrays.fill(level, SegmentCheck.Level.FINE);
        Arrays.fill(fix, Fix.NONE);
        for (int i = 0; i < n; i++) {
            if (stamp[i] == Long.MIN_VALUE) {
                level[i] = SegmentCheck.Level.UNKNOWN;
                why[i] = "not surveyed yet";
                continue;
            }
            Ground g = Ground.byOrdinal(cls[i]);
            if (g == Ground.WATER && amphibious) {
                level[i] = SegmentCheck.Level.AMBER;
                why[i] = "water: amphibious vehicles only";
            } else if (g.wall) {
                level[i] = SegmentCheck.Level.RED;
                why[i] = words(i, g, p);
                fix[i] = switch (g) {
                    case WATER -> Fix.MOVE_NODE;
                    case LAVA, DROP, HURT -> Fix.MOVE_NODE;
                    case BLOCKED -> Fix.CLEAR;
                    default -> Fix.NONE;
                };
            } else if (head[i] >= 0 && head[i] < HEADROOM_MAX && head[i] < height) {
                level[i] = SegmentCheck.Level.RED;
                why[i] = "a ceiling " + head[i] + " blocks up " + near(i, p);
                fix[i] = Fix.MOVE_NODE;
            } else if (g == Ground.SLIPPERY) {
                level[i] = SegmentCheck.Level.AMBER;
                why[i] = "ice: a tenth of the grip, slow and early";
            } else if (g == Ground.SOFT) {
                level[i] = SegmentCheck.Level.AMBER;
                why[i] = "soft ground: a third of the grip";
            }
            double cross = crossSlope(i);
            if (Double.isFinite(cross) && Math.abs(cross) > CROSS_AMBER && level[i].ordinal() < SegmentCheck.Level.AMBER.ordinal()) {
                boolean red = Math.abs(cross) > CROSS_RED;
                level[i] = red ? SegmentCheck.Level.RED : SegmentCheck.Level.AMBER;
                why[i] = String.format(Locale.ROOT, "a %.0f degree lean across the road", Math.toDegrees(Math.atan(Math.abs(cross))));
                fix[i] = red ? Fix.MOVE_NODE : Fix.NONE;
            }
            double bump = bump(i);
            if (Double.isFinite(bump) && Math.abs(bump) > BUMP && level[i] == SegmentCheck.Level.FINE) {
                level[i] = SegmentCheck.Level.AMBER;
                why[i] = bump > 0 ? "a bump" : "a dip";
            }
        }
        List<Issue> out = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= n; i++) {
            boolean same = i < n && start >= 0 && level[i] == level[start] && eq(why[i], why[start]);
            if (same) continue;
            if (start >= 0 && level[start] != SegmentCheck.Level.FINE) {
                int end = Math.min(i, n) - 1;
                int node = -1;
                if (fix[start] == Fix.MOVE_NODE) node = nodeOn(p, start, end);
                Fix f = fix[start];
                if (f == Fix.MOVE_NODE && node < 0) f = Fix.NONE;
                out.add(new Issue(level[start], sp.sampleS(start), sp.sampleS(end), why[start], f, node));
            }
            start = i < n ? i : -1;
        }
        return out;
    }

    private int nodeOn(Pathway p, int from, int to) {
        if (p == null) return -1;
        double a = sp.sampleS(from);
        double b = sp.sampleS(to);
        int best = -1;
        double bestD = 3.0;
        for (int k = 0; k < p.nodes().size() && k < sp.nodeCount(); k++) {
            double s = sp.nodeS(k);
            double d = s >= a && s <= b ? 0.0 : Math.min(Math.abs(sp.signedDelta(s, a)), Math.abs(sp.signedDelta(s, b)));
            if (d < bestD) {
                bestD = d;
                best = p.nodes().get(k).id();
            }
        }
        return best;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
