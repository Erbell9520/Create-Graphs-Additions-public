package com.createstats.surveyor.core;

import java.util.List;

public final class RoadPaint {
    public static final float LIFT = 0.035f;

    public static final double NEAR = 48.0;

    public static final double ARROW_EVERY = 16.0;
    static final double EDGE_W = 0.14;
    static final double DASH_W = 0.14;
    static final double BAR_W = 0.32;
    static final double BAY_W = 0.1;

    static final double CELL = 0.24;

    static final double HATCH_PITCH = 1.0;
    static final double HATCH_W = 0.45;
    static final double HATCH_LEAN = 1.2;

    static final int WHITE = 0xF2F2EE;
    static final int YELLOW = 0xF2C94C;
    static final int AMBER = 0xFFAA00;
    static final int RED = 0xE03A2A;
    static final int GREY = 0x9A9A9A;

    private RoadPaint() {
    }

    public interface Sink {
        void quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                  float x3, float y3, float z3, int argb);
    }

    public record StopMark(double s, double front, double rear) {
    }

    public record LimitMark(double s, double speed) {
    }

    public record Scene(PathSpline sp, double[] height, int[] level, double[] flow, double time, int colour, int alpha,
                        double half, boolean warnings, double camX, double camZ, double range, List<StopMark> stops,
                        List<LimitMark> limits, double period) {
    }

    public static void paint(Scene sc, Sink out) {
        PathSpline sp = sc.sp();
        int n = sp.sampleCount();
        if (n < 2) return;
        boolean closed = sp.closed();
        int last = closed ? n : n - 1;
        double range2 = sc.range() * sc.range();
        double near2 = NEAR * NEAR;
        int base = sc.alpha() << 24;
        int edgeColour = base | (sc.colour() & 0xFFFFFF);

        int coarse = Math.max(1, (int) Math.round(1.0 / Math.max(1e-6, sp.step())));
        for (int i = 0; i < last; i++) {
            double d2 = dist2(sp, i, sc.camX(), sc.camZ());
            if (d2 > range2) continue;
            boolean near = d2 <= near2;
            int step = near ? 1 : coarse;
            if (!near && i % coarse != 0) continue;
            int j = closed ? (i + step) % n : Math.min(n - 1, i + step);
            if (j == i) continue;
            int lv = sc.level().length > i ? sc.level()[i] : 0;
            int c = edgeColour;
            if (lv == 1) {
                if ((i / 4) % 2 == 1 && near) continue;
                c = base | GREY;
            } else if (lv == 3) {
                c = base | RED;
            } else if (lv == 2 && sc.warnings()) {
                c = base | AMBER;
            }
            strip(sc, i, j, -sc.half() - EDGE_W / 2, -sc.half() + EDGE_W / 2, 0.0, c, out);
            strip(sc, i, j, sc.half() - EDGE_W / 2, sc.half() + EDGE_W / 2, 0.0, c, out);
            if (!near || lv == 1 || lv == 3) continue;
            double phase = (sc.flow()[i] - sc.time()) / sc.period();
            if (phase - Math.floor(phase) < 0.5) {
                strip(sc, i, j, -DASH_W / 2, DASH_W / 2, 0.001, (Math.min(255, sc.alpha() + 20) << 24) | WHITE, out);
            }
        }
        hatching(sc, out);
        for (StopMark st : sc.stops()) stopMarks(sc, st, out);
        arrows(sc, out);
        for (LimitMark l : sc.limits()) limit(sc, l, out);
    }

    private static void hatching(Scene sc, Sink out) {
        PathSpline sp = sc.sp();
        int n = sp.sampleCount();
        double near2 = NEAR * NEAR;
        int i = 0;
        while (i < n) {
            if (sc.level().length <= i || sc.level()[i] != 3) {
                i++;
                continue;
            }
            int j = i;
            while (j + 1 < n && sc.level().length > j + 1 && sc.level()[j + 1] == 3) j++;
            double s0 = sp.sampleS(i);
            double s1 = sp.sampleS(Math.min(n - 1, j + 1));
            if (dist2(sp, i, sc.camX(), sc.camZ()) <= near2 || dist2(sp, j, sc.camX(), sc.camZ()) <= near2) {
                int fill = (Math.max(0, sc.alpha() - 90) << 24) | RED;
                for (int k = i; k <= j && k + 1 < n; k++) strip(sc, k, k + 1, -sc.half(), sc.half(), 0.0005, fill, out);
                int stripe = (Math.min(255, sc.alpha() + 20) << 24) | WHITE;
                for (double s = Math.floor(s0 / HATCH_PITCH) * HATCH_PITCH; s < s1; s += HATCH_PITCH) {
                    double a = Math.max(s0, s);
                    double b = Math.min(s1, s + HATCH_W);
                    if (b <= a) continue;

                    double a2 = Math.min(s1, a + HATCH_LEAN);
                    double b2 = Math.min(s1, b + HATCH_LEAN);
                    quadAt(sc, a, -sc.half(), b, -sc.half(), b2, sc.half(), a2, sc.half(), 0.0015, stripe, out);
                }
            }
            i = j + 1;
        }
    }

    private static void stopMarks(Scene sc, StopMark st, Sink out) {
        PathSpline sp = sc.sp();
        if (!near(sc, st.s())) return;
        int white = (Math.min(255, sc.alpha() + 30) << 24) | WHITE;
        int yellow = (Math.min(255, sc.alpha() + 20) << 24) | YELLOW;
        quadAt(sc, st.s() - BAR_W / 2, -sc.half(), st.s() + BAR_W / 2, -sc.half(), st.s() + BAR_W / 2, sc.half(),
                st.s() - BAR_W / 2, sc.half(), 0.002, white, out);
        double from = st.s() - st.rear();
        double to = st.s() + st.front();
        if (!sp.closed()) {
            from = Math.max(0.0, from);
            to = Math.min(sp.length(), to);
        }
        double o0 = sc.half() + 0.06;
        double o1 = o0 + BAY_W;
        for (double s = from; s < to - 1e-6; s += 0.5) {
            double e = Math.min(to, s + 0.5);
            quadAt(sc, s, o0, e, o0, e, o1, s, o1, 0.002, yellow, out);
            quadAt(sc, s, -o1, e, -o1, e, -o0, s, -o0, 0.002, yellow, out);
        }

        quadAt(sc, from, -o1, from + BAY_W, -o1, from + BAY_W, o1, from, o1, 0.002, yellow, out);
        quadAt(sc, to - BAY_W, -o1, to, -o1, to, o1, to - BAY_W, o1, 0.002, yellow, out);
    }

    private static void arrows(Scene sc, Sink out) {
        PathSpline sp = sc.sp();
        int white = (Math.min(255, sc.alpha() + 20) << 24) | WHITE;
        for (double s = ARROW_EVERY / 2; s < sp.length() - 2.0; s += ARROW_EVERY) {
            if (!near(sc, s) || clear(sc, s, 4.0)) continue;

            quadAt(sc, s - 0.8, -0.07, s + 0.15, -0.07, s + 0.15, 0.07, s - 0.8, 0.07, 0.002, white, out);
            quadAt(sc, s + 0.15, -0.45, s + 0.9, 0.0, s + 0.9, 0.0, s + 0.15, 0.45, 0.002, white, out);
        }
    }

    private static boolean clear(Scene sc, double s, double room) {
        for (StopMark st : sc.stops()) if (Math.abs(st.s() - s) < room || (s > st.s() - st.rear() && s < st.s() + st.front())) return true;
        for (LimitMark l : sc.limits()) if (Math.abs(paintAt(sc, l) - s) < room) return true;
        PathSpline sp = sc.sp();
        int i0 = (int) Math.floor((s - 1.0) / sp.step());
        int i1 = (int) Math.ceil((s + 1.0) / sp.step());
        for (int i = Math.max(0, i0); i <= Math.min(sp.sampleCount() - 1, i1); i++) {
            if (sc.level().length > i && sc.level()[i] == 3) return true;
        }
        return false;
    }

    static double paintAt(Scene sc, LimitMark l) {
        double at = l.s() - 2.5;
        if (at < 1.0 && !sc.sp().closed()) at = l.s() + 1.5;
        return sc.sp().closed() ? sc.sp().wrap(at) : at;
    }

    private static void limit(Scene sc, LimitMark l, Sink out) {
        double at = paintAt(sc, l);
        if (!near(sc, at)) return;
        String text = l.speed() == Math.rint(l.speed()) ? String.valueOf((long) l.speed()) : String.valueOf(l.speed());
        int white = (Math.min(255, sc.alpha() + 20) << 24) | WHITE;
        int cols = 0;
        for (char ch : text.toCharArray()) cols += glyphWidth(ch) + 1;
        cols -= 1;
        double left = -cols * CELL / 2.0;
        int col = 0;
        for (char ch : text.toCharArray()) {
            String[] g = glyph(ch);
            int w = glyphWidth(ch);
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < w; c++) {
                    if (g[r].charAt(c) != '#') continue;
                    double u0 = left + (col + c) * CELL;
                    double u1 = u0 + CELL;

                    double v1 = at + (2.5 - r) * CELL;
                    double v0 = v1 - CELL;
                    quadAt(sc, v0, u0, v1, u0, v1, u1, v0, u1, 0.002, white, out);
                }
            }
            col += w + 1;
        }
    }

    private static int glyphWidth(char ch) {
        return ch == '.' ? 1 : 3;
    }

    static String[] glyph(char ch) {
        return switch (ch) {
            case '0' -> new String[]{"###", "#.#", "#.#", "#.#", "###"};
            case '1' -> new String[]{".#.", "##.", ".#.", ".#.", "###"};
            case '2' -> new String[]{"###", "..#", "###", "#..", "###"};
            case '3' -> new String[]{"###", "..#", ".##", "..#", "###"};
            case '4' -> new String[]{"#.#", "#.#", "###", "..#", "..#"};
            case '5' -> new String[]{"###", "#..", "###", "..#", "###"};
            case '6' -> new String[]{"###", "#..", "###", "#.#", "###"};
            case '7' -> new String[]{"###", "..#", "..#", ".#.", ".#."};
            case '8' -> new String[]{"###", "#.#", "###", "#.#", "###"};
            case '9' -> new String[]{"###", "#.#", "###", "..#", "###"};
            case '.' -> new String[]{".", ".", ".", ".", "#"};
            default -> new String[]{"...", "...", "...", "...", "..."};
        };
    }

    private static double dist2(PathSpline sp, int i, double cx, double cz) {
        double dx = sp.sampleX(i) - cx;
        double dz = sp.sampleZ(i) - cz;
        return dx * dx + dz * dz;
    }

    private static boolean near(Scene sc, double s) {
        PathSpline sp = sc.sp();
        double dx = sp.x(s) - sc.camX();
        double dz = sp.z(s) - sc.camZ();
        return dx * dx + dz * dz <= NEAR * NEAR;
    }

    private static void strip(Scene sc, int i, int j, double a, double b, double dy, int argb, Sink out) {
        PathSpline sp = sc.sp();
        double y0 = sc.height()[i] + dy;
        double y1 = sc.height()[j] + dy;
        double rx0 = -sp.tangentZ(i), rz0 = sp.tangentX(i);
        double rx1 = -sp.tangentZ(j), rz1 = sp.tangentX(j);
        double x0 = sp.sampleX(i), z0 = sp.sampleZ(i);
        double x1 = sp.sampleX(j), z1 = sp.sampleZ(j);
        out.quad((float) (x0 + rx0 * a), (float) y0, (float) (z0 + rz0 * a),
                (float) (x0 + rx0 * b), (float) y0, (float) (z0 + rz0 * b),
                (float) (x1 + rx1 * b), (float) y1, (float) (z1 + rz1 * b),
                (float) (x1 + rx1 * a), (float) y1, (float) (z1 + rz1 * a), argb);
    }

    private static void quadAt(Scene sc, double s0, double o0, double s1, double o1, double s2, double o2, double s3, double o3,
                               double dy, int argb, Sink out) {
        float[] a = at(sc, s0, o0, dy);
        float[] b = at(sc, s1, o1, dy);
        float[] c = at(sc, s2, o2, dy);
        float[] d = at(sc, s3, o3, dy);
        out.quad(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], argb);
    }

    private static float[] at(Scene sc, double s, double off, double dy) {
        PathSpline sp = sc.sp();
        double q = sp.closed() ? sp.wrap(s) : Math.max(0.0, Math.min(sp.length(), s));
        double[] t = sp.tangent(q);
        double x = sp.x(q) - t[1] * off;
        double z = sp.z(q) + t[0] * off;
        return new float[]{(float) x, (float) (height(sc, q) + dy), (float) z};
    }

    static double height(Scene sc, double s) {
        PathSpline sp = sc.sp();
        int n = sp.sampleCount();
        double f = s / Math.max(1e-9, sp.step());
        int i = (int) Math.floor(f);
        double t = f - i;
        if (sp.closed()) {
            int a = Math.floorMod(i, n);
            int b = (a + 1) % n;
            return sc.height()[a] * (1 - t) + sc.height()[b] * t;
        }
        if (i < 0) return sc.height()[0];
        if (i >= n - 1) return sc.height()[n - 1];
        return sc.height()[i] * (1 - t) + sc.height()[i + 1] * t;
    }
}
