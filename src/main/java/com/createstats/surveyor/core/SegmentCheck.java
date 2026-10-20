package com.createstats.surveyor.core;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// amber = uncomfortable, red = can't
public final class SegmentCheck {
    public static final double DEFAULT_RADIUS = 4.0;
    public static final double STEP_AMBER = 0.55;
    public static final double STEP_RED = 1.05;
    public static final double GRADE_RED = 1.0;

    public enum Level {
        FINE, UNKNOWN, AMBER, RED;

        public Level worst(Level other) {
            return other != null && other.ordinal() > ordinal() ? other : this;
        }
    }

    public record Finding(Level level, double from, double to, String why) {
    }

    private SegmentCheck() {
    }

    public static List<Finding> shape(PathSpline sp, double radius, @Nullable double[] ground, double[] sharp) {
        int n = sp.sampleCount();
        List<Finding> out = new ArrayList<>();
        if (n < 2) return out;
        double r = radius > 0.5 ? radius : DEFAULT_RADIUS;
        Level[] level = new Level[n];
        String[] why = new String[n];
        for (int i = 0; i < n; i++) {
            level[i] = Level.FINE;
            double k = Math.abs(sp.curvature(i));
            if (k > 1.0e-6) {
                double bend = 1.0 / k;
                boolean corner = nearAny(sp, sp.sampleS(i), sharp, 1.5);
                if (bend < r && !corner) {
                    level[i] = Level.RED;
                    why[i] = String.format(Locale.ROOT, "bend of %.1f blocks, tighter than the vehicle's %.1f", bend, r);
                } else if (bend < r * 1.5) {
                    level[i] = Level.AMBER;
                    why[i] = corner ? "sharp corner, crawled round" : String.format(Locale.ROOT, "tight bend of %.1f blocks", bend);
                }
            }
        }
        double step = sp.step();
        int reach = Math.max(1, (int) Math.round(0.5 / step));
        for (int i = 0; i + reach < n || (sp.closed() && i < n); i++) {
            int j = sp.closed() ? (i + reach) % n : i + reach;
            if (j >= n) break;
            double a = height(sp, ground, i);
            double b = height(sp, ground, j);
            if (!Double.isFinite(a) || !Double.isFinite(b)) continue;
            double rise = b - a;
            double run = reach * step;
            Level l = Level.FINE;
            String w = null;
            if (Math.abs(rise) > STEP_RED) {
                l = Level.RED;
                w = String.format(Locale.ROOT, "a step of %.1f blocks", Math.abs(rise));
            } else if (Math.abs(rise) > STEP_AMBER) {
                l = Level.AMBER;
                w = String.format(Locale.ROOT, "a step of %.1f blocks", Math.abs(rise));
            }
            double grade = gradeOver(sp, ground, i, (int) Math.round(2.0 / step));
            if (Double.isFinite(grade)) {
                if (Math.abs(grade) > GRADE_RED && l != Level.RED) {
                    l = Level.RED;
                    w = String.format(Locale.ROOT, "%d%% grade, steeper than any wheel holds", Math.round(Math.abs(grade) * 100));
                } else if (Math.abs(grade) > SpeedPlan.VERY_STEEP && l == Level.FINE) {
                    l = Level.AMBER;
                    w = String.format(Locale.ROOT, "%d%% grade", Math.round(Math.abs(grade) * 100));
                }
            }
            if (l.ordinal() > level[i].ordinal()) {
                level[i] = l;
                why[i] = w;
            }
            if (run <= 0.0) break;
        }

        int start = -1;
        for (int i = 0; i <= n; i++) {
            boolean same = i < n && start >= 0 && level[i] == level[start] && eq(why[i], why[start]);
            if (same) continue;
            if (start >= 0 && level[start] != Level.FINE) {
                out.add(new Finding(level[start], sp.sampleS(start), sp.sampleS(Math.min(i, n - 1)), why[start]));
            }
            start = i < n ? i : -1;
        }
        return out;
    }

    public static Level worst(List<Finding> findings) {
        Level l = Level.FINE;
        for (Finding f : findings) l = l.worst(f.level());
        return l;
    }

    private static double height(PathSpline sp, @Nullable double[] ground, int i) {
        if (ground != null && i < ground.length) return ground[i];
        return sp.sampleY(i);
    }

    private static double gradeOver(PathSpline sp, @Nullable double[] ground, int i, int span) {
        int n = sp.sampleCount();
        int j = sp.closed() ? (i + span) % n : Math.min(n - 1, i + span);
        if (j == i) return Double.NaN;
        double a = height(sp, ground, i);
        double b = height(sp, ground, j);
        double run = (sp.closed() ? span : j - i) * sp.step();
        return run < 1.0e-6 ? Double.NaN : (b - a) / run;
    }

    private static boolean nearAny(PathSpline sp, double s, double[] marks, double within) {
        for (double m : marks) if (Math.abs(sp.signedDelta(m, s)) <= within) return true;
        return false;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
