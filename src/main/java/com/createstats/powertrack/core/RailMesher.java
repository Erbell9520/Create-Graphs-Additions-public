package com.createstats.powertrack.core;

import java.util.ArrayList;
import java.util.List;

public final class RailMesher {
    public static final float[] UV_SIDE = {0, 0, 16, 3.5f};
    public static final float[] UV_TOP = {0, 4.5f, 16, 6};
    public static final float[] UV_CAP_SIDE = {0, 4, 16, 5};
    public static final float[] UV_PORCELAIN = {0, 6, 6, 10};
    public static final float[] UV_IRON = {0, 10, 6, 12};
    public static final float[] UV_CABLE = {0, 12, 16, 14};
    public static final float[] UV_LUG = {0, 14, 4, 16};
    public static final float[] UV_WHITE = {4, 14, 8, 16};
    public static final float[] UV_POT = {8, 14, 12, 16};
    public static final float[] UV_END = {12, 14, 16, 16};

    public record Quad(float[] xyz, float nx, float ny, float nz, float[] uv, int lightX, int lightY, int lightZ) {
    }

    public record Tap(int lo, int hi, double at, int side) {
    }

    static final double CLEAR_Y = RailProfile.SLEEPER_TOP + 0.2;

    private final double ox;
    private final double oy;
    private final double oz;
    private final List<Quad> out = new ArrayList<>();

    private RailMesher(double ox, double oy, double oz) {
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
    }

    public static List<Quad> meshEdge(RailLayout.Shape shape, List<SectionGraph.Piece> pieces, List<Tap> taps,
                                      double[] origin) {
        final RailMesher m = new RailMesher(origin[0], origin[1], origin[2]);
        for (SectionGraph.Piece p : pieces) {
            if (p.hasRail()) {
                m.railRun(shape, p);
            }
        }
        for (Tap t : taps) {
            if (t.lo() == shape.lo() && t.hi() == shape.hi()) {
                m.feederTap(shape, t.at(), t.side(), pieces);
            }
        }
        return m.out;
    }

    public static boolean sameRail(List<SectionGraph.Piece> a, List<SectionGraph.Piece> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            final SectionGraph.Piece p = a.get(i);
            final SectionGraph.Piece q = b.get(i);
            if (Double.compare(p.from(), q.from()) != 0 || Double.compare(p.to(), q.to()) != 0
                    || Double.compare(p.railFrom(), q.railFrom()) != 0 || Double.compare(p.railTo(), q.railTo()) != 0
                    || p.fromEnd() != q.fromEnd() || p.toEnd() != q.toEnd()) {
                return false;
            }
        }
        return true;
    }

    private void railRun(RailLayout.Shape path, SectionGraph.Piece piece) {
        final double from = piece.railFrom();
        final double to = piece.railTo();
        final double lenPx = (to - from) * 16.0;
        final int slices = Math.max(1, (int) Math.ceil(lenPx / RailProfile.SLICE));
        Slice prev = null;
        for (int k = 0; k <= slices; k++) {
            final double s = from + (to - from) * k / slices;
            final Slice cur = slice(path, piece, s);
            if (prev != null) {
                for (int side = -1; side <= 1; side += 2) {
                    final double[] a0 = off(prev, side * RailProfile.BODY_W / 2);
                    final double[] a1 = off(cur, side * RailProfile.BODY_W / 2);
                    quad(new double[][]{{a0[0], prev.bottom, a0[1]}, {a1[0], cur.bottom, a1[1]},
                            {a1[0], cur.body, a1[1]}, {a0[0], prev.body, a0[1]}},
                            side * cur.nx, 0, side * cur.nz, UV_SIDE, cur, side < 0);

                    final double[] c0 = off(prev, side * RailProfile.CAP_W / 2);
                    final double[] c1 = off(cur, side * RailProfile.CAP_W / 2);
                    quad(new double[][]{{c0[0], prev.body, c0[1]}, {c1[0], cur.body, c1[1]},
                            {c1[0], cur.top, c1[1]}, {c0[0], prev.top, c0[1]}},
                            side * cur.nx, 0, side * cur.nz, UV_CAP_SIDE, cur, side < 0);

                    final double[] o0 = off(prev, side * RailProfile.BODY_W / 2);
                    final double[] i0 = off(prev, side * RailProfile.CAP_W / 2);
                    final double[] o1 = off(cur, side * RailProfile.BODY_W / 2);
                    final double[] i1 = off(cur, side * RailProfile.CAP_W / 2);
                    quad(new double[][]{{o0[0], prev.body, o0[1]}, {o1[0], cur.body, o1[1]},
                            {i1[0], cur.body, i1[1]}, {i0[0], prev.body, i0[1]}},
                            0, 1, 0, UV_SIDE, cur, side > 0);
                }

                final double[] l0 = off(prev, -RailProfile.CAP_W / 2);
                final double[] r0 = off(prev, RailProfile.CAP_W / 2);
                final double[] l1 = off(cur, -RailProfile.CAP_W / 2);
                final double[] r1 = off(cur, RailProfile.CAP_W / 2);
                final double[] n = slopeNormal(prev, cur);
                quad(new double[][]{{l0[0], prev.top, l0[1]}, {l1[0], cur.top, l1[1]},
                        {r1[0], cur.top, r1[1]}, {r0[0], prev.top, r0[1]}}, n[0], n[1], n[2], UV_TOP, cur, true);
            }
            prev = cur;
        }
        endOf(path, piece, from, -1, piece.fromEnd());
        endOf(path, piece, to, 1, piece.toEnd());
        for (double s : chairPositions(path, from, to)) {
            final Slice c = slice(path, piece, s);

            if (c.top - RailProfile.RAIL_DEPTH < RailProfile.SADDLE_TOP - 0.05) {
                continue;
            }
            box(c, 0, RailProfile.CHAIR_W, RailProfile.CHAIR_W, RailProfile.SLEEPER_TOP, RailProfile.CHAIR_TOP,
                    UV_PORCELAIN, UV_PORCELAIN, null, false);
            box(c, 0, RailProfile.SADDLE_L, RailProfile.SADDLE_W, RailProfile.CHAIR_TOP, RailProfile.SADDLE_TOP,
                    UV_IRON, UV_IRON, null, true);
        }
    }

    private void endOf(RailLayout.Shape path, SectionGraph.Piece piece, double s, int sign, SectionGraph.RailEnd end) {
        if (end == SectionGraph.RailEnd.THROUGH) {
            return;
        }
        final Slice c = slice(path, piece, s);
        final double[] a = off(c, -RailProfile.BODY_W / 2);
        final double[] b = off(c, RailProfile.BODY_W / 2);

        if (end != SectionGraph.RailEnd.RAMP_INSULATED) {
            quad(new double[][]{{a[0], c.bottom, a[1]}, {b[0], c.bottom, b[1]}, {b[0], c.top, b[1]},
                    {a[0], c.top, a[1]}}, sign * c.tx, 0, sign * c.tz, UV_END, c, sign < 0);
        }
        if (end == SectionGraph.RailEnd.RAMP_INSULATED) {
            final Slice capAt = slice(path, piece, s - sign * (1.0 / 16.0));
            box(capAt, 0, 2.0, RailProfile.BODY_W + 0.6, Math.max(c.bottomPx - 0.2, CLEAR_Y), c.topPx + 0.3, UV_WHITE,
                    UV_WHITE, null, true);
        } else if (end == SectionGraph.RailEnd.RAMP_JUMPERED) {
            jumper(path, piece, s, sign);
        }
    }

    private void jumper(RailLayout.Shape path, SectionGraph.Piece piece, double s, int sign) {
        final Slice c = slice(path, piece, s);
        final double lugAlong = -sign * 1.2 / 16.0;
        final double lugSide = (RailProfile.BODY_W / 2 + 0.3) / 16.0;

        final double lugY = Math.max(CLEAR_Y + 0.5, RailProfile.CAP_TOP - RailProfile.RAMP_DROP * 0.85 - 1.4);
        final double[] lug = {c.x + c.tx * lugAlong + c.nx * lugSide, c.z + c.tz * lugAlong + c.nz * lugSide};
        boxAt(lug[0], lug[1], c, 1.6, 0.6, lugY - 0.5, lugY + 0.5, UV_LUG, c.lx, c.ly, c.lz);

        final double potS = crib(path, s + sign * 3.0 / 16.0, s, sign);
        final Frame f = frame(path, potS);
        final double[] pot = {f.x + f.nx * 3.6 / 16.0, f.z + f.nz * 3.6 / 16.0};
        cable(lug, lugY, pot, RailProfile.SLEEPER_TOP + 1.0, -1.6 * 0.6, c, 7);
        orientedBox(pot[0], pot[1], f.tx, f.tz, 3.6, 3.0, RailProfile.SLEEPER_TOP - 2.0, RailProfile.SLEEPER_TOP + 1.4,
                f.baseY, UV_POT, UV_POT, UV_POT, c.lx, c.ly, c.lz);
    }

    private void feederTap(RailLayout.Shape path, double at, int side, List<SectionGraph.Piece> pieces) {
        SectionGraph.Piece piece = null;
        for (SectionGraph.Piece p : pieces) {
            if (p.railAt(at)) {
                piece = p;
                break;
            }
        }
        if (piece == null) {
            return;
        }
        final Slice c = slice(path, piece, at);
        final double lugSide = side * (RailProfile.BODY_W / 2 + 0.3) / 16.0;
        final double[] lug = {c.x + c.nx * lugSide, c.z + c.nz * lugSide};

        boxAt(lug[0], lug[1], c, 2.0, 0.6, RailProfile.SADDLE_TOP + 0.1, RailProfile.BODY_TOP - 0.1, UV_LUG,
                c.lx, c.ly, c.lz);
        final double potS = crib(path, at, at, 0);
        final Frame f = frame(path, potS);
        final double[] pot = {f.x + f.nx * side * 5.0 / 16.0, f.z + f.nz * side * 5.0 / 16.0};
        orientedBox(pot[0], pot[1], f.tx, f.tz, 3.2, 2.6, RailProfile.SLEEPER_TOP - 2.0, RailProfile.SLEEPER_TOP + 1.2,
                f.baseY, UV_POT, UV_POT, UV_POT, c.lx, c.ly, c.lz);
        cable(lug, 4.3, pot, RailProfile.SLEEPER_TOP + 1.2, -0.8, c, 6);
    }

    private void cable(double[] from, double fromY, double[] to, double toY, double sag, Slice c, int points) {
        final double half = 0.6;
        final double[][] pts = new double[points][];
        for (int k = 0; k < points; k++) {
            final double f = k / (double) (points - 1);
            double y = fromY + (toY - fromY) * f + sag * Math.sin(Math.PI * f);
            if (k < points - 1) {
                y = Math.max(y, CLEAR_Y + half + 0.15);
            }
            pts[k] = new double[]{from[0] + (to[0] - from[0]) * f, y, from[1] + (to[1] - from[1]) * f};
        }

        final double[][][] rings = new double[points][4][];
        final double[][] sides = new double[points][];
        for (int k = 0; k < points; k++) {
            final double[] a = pts[Math.max(0, k - 1)];
            final double[] b = pts[Math.min(points - 1, k + 1)];
            double dx = b[0] - a[0];
            double dz = b[2] - a[2];
            final double m = Math.max(1e-9, Math.sqrt(dx * dx + dz * dz));
            dx /= m;
            dz /= m;
            final double nx = dz;
            final double nz = -dx;
            sides[k] = new double[]{nx, nz, dx, dz};
            final double[] p = pts[k];
            final double w = half / 16.0;
            final double y0 = c.baseY + (p[1] - half) / 16.0;
            final double y1 = c.baseY + (p[1] + half) / 16.0;
            rings[k][0] = new double[]{p[0] - nx * w, y0, p[2] - nz * w};
            rings[k][1] = new double[]{p[0] + nx * w, y0, p[2] + nz * w};
            rings[k][2] = new double[]{p[0] + nx * w, y1, p[2] + nz * w};
            rings[k][3] = new double[]{p[0] - nx * w, y1, p[2] - nz * w};
        }
        for (int k = 0; k + 1 < points; k++) {
            final double[][] r0 = rings[k];
            final double[][] r1 = rings[k + 1];
            final double nx = (sides[k][0] + sides[k + 1][0]) / 2;
            final double nz = (sides[k][1] + sides[k + 1][1]) / 2;
            quadAt(new double[][]{r0[0], r1[0], r1[1], r0[1]}, 0, -1, 0, UV_CABLE, c.lx, c.ly, c.lz);
            quadAt(new double[][]{r0[1], r1[1], r1[2], r0[2]}, nx, 0, nz, UV_CABLE, c.lx, c.ly, c.lz);
            quadAt(new double[][]{r0[2], r1[2], r1[3], r0[3]}, 0, 1, 0, UV_CABLE, c.lx, c.ly, c.lz);
            quadAt(new double[][]{r0[3], r1[3], r1[0], r0[0]}, -nx, 0, -nz, UV_CABLE, c.lx, c.ly, c.lz);
        }
        final double[][] first = rings[0];
        quadAt(new double[][]{first[0], first[1], first[2], first[3]}, -sides[0][2], 0, -sides[0][3], UV_CABLE,
                c.lx, c.ly, c.lz);
    }

    static double crib(RailLayout.Shape path, double target, double from, int sign) {
        final double[] phase = cribPhase(path);
        if (phase == null) {
            return target;
        }
        final double step = phase[1];
        final double base = Math.round((target * 16.0 - phase[0]) / step);
        double best = target;
        double bestD = Double.MAX_VALUE;
        for (int k = -3; k <= 3; k++) {
            final double s = (phase[0] + step * (base + k)) / 16.0;
            if (s < 0 || s > path.length()) {
                continue;
            }
            if (sign != 0 && sign * (s - from) < 1.5 / 16.0) {
                continue;
            }
            final double d = Math.abs(s - target);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    static double[] cribPhase(RailLayout.Shape path) {
        if (path.samples() > 2) {
            final int segments = Math.max(1, (int) (path.length() * 2));
            return new double[]{0.0, path.length() * 16.0 / segments};
        }
        final double[] t0 = path.tangent(0);
        final double[] p0 = path.point(0);
        final double[] p1 = path.point(path.length());

        if (Math.abs(p1[1] - p0[1]) < 1e-6 && (Math.abs(t0[0]) > 0.999 || Math.abs(t0[1]) > 0.999)) {
            final boolean alongX = Math.abs(t0[0]) > 0.999;
            final double along0 = (alongX ? p0[0] : p0[2]) * 16.0;
            final double sign = alongX ? t0[0] : t0[1];
            return new double[]{floorMod(sign > 0 ? -along0 : along0, 8.0), 8.0};
        }
        return null;
    }

    static List<Double> chairPositions(RailLayout.Shape path, double from, double to) {
        final List<Double> outPositions = new ArrayList<>();
        if (path.samples() > 2) {
            final int segments = Math.max(1, (int) (path.length() * 2));
            final double step = path.length() * 16.0 / segments;
            for (int i = 0; i < segments; i += 2) {
                final double s = i * step + RailProfile.CHAIR_PHASE;
                if (s >= from * 16.0 + 1.5 && s <= to * 16.0 - 1.5) {
                    outPositions.add(s / 16.0);
                }
            }
            return outPositions;
        }
        final double[] p0 = path.point(0);
        final double[] t0 = path.tangent(0);
        double phasePx;
        if (Math.abs(t0[0]) > 0.999 || Math.abs(t0[1]) > 0.999) {
            final boolean alongX = Math.abs(t0[0]) > 0.999;
            final double along0 = (alongX ? p0[0] : p0[2]) * 16.0;
            final double sign = alongX ? t0[0] : t0[1];
            phasePx = sign > 0 ? floorMod(RailProfile.CHAIR_PHASE - along0, RailProfile.CHAIR_SPACING)
                    : floorMod(along0 - RailProfile.CHAIR_PHASE, RailProfile.CHAIR_SPACING);
        } else {
            phasePx = RailProfile.CHAIR_PHASE;
        }
        final double fromPx = from * 16.0;
        final double toPx = to * 16.0;
        for (double s = phasePx; s < toPx; s += RailProfile.CHAIR_SPACING) {
            if (s >= fromPx + 1.5 && s <= toPx - 1.5) {
                outPositions.add(s / 16.0);
            }
        }
        return outPositions;
    }

    private static double floorMod(double v, double m) {
        final double r = v % m;
        return r < 0 ? r + m : r;
    }

    private static final class Slice {
        double x;
        double z;
        double baseY;
        double tx;
        double tz;
        double nx;
        double nz;

        double topPx;
        double bottomPx;

        double top;
        double body;
        double bottom;
        int lx;
        int ly;
        int lz;
    }

    private static final class Frame {
        double x;
        double z;
        double baseY;
        double tx;
        double tz;
        double nx;
        double nz;
    }

    private static Frame frame(RailLayout.Shape path, double s) {
        final double[] p = path.point(s);
        final double[] t = path.tangent(s);
        final Frame f = new Frame();
        f.x = p[0];
        f.z = p[2];
        f.baseY = p[1];
        f.tx = t[0];
        f.tz = t[1];
        f.nx = t[1];
        f.nz = -t[0];
        return f;
    }

    private Slice slice(RailLayout.Shape path, SectionGraph.Piece piece, double s) {
        final double[] p = path.point(s);
        final double[] t = path.tangent(s);
        final Slice c = new Slice();
        c.x = p[0];
        c.z = p[2];
        c.baseY = p[1];
        c.tx = t[0];
        c.tz = t[1];

        c.nx = t[1];
        c.nz = -t[0];
        final double topPx = RailProfile.topPx(piece, s);
        c.topPx = topPx;
        c.bottomPx = RailProfile.bottomPx(topPx);
        c.top = p[1] + topPx / 16.0;
        c.body = p[1] + RailProfile.bodyTopPx(topPx) / 16.0;
        c.bottom = p[1] + RailProfile.bottomPx(topPx) / 16.0;
        c.lx = (int) Math.floor(p[0]);
        c.ly = (int) Math.floor(p[1] + 0.3);
        c.lz = (int) Math.floor(p[2]);
        return c;
    }

    private static double[] off(Slice c, double sidePx) {
        return new double[]{c.x + c.nx * sidePx / 16.0, c.z + c.nz * sidePx / 16.0};
    }

    private static double[] slopeNormal(Slice a, Slice b) {
        final double dx = b.x - a.x;
        final double dy = b.top - a.top;
        final double dz = b.z - a.z;
        double nx = dy * b.nz - dz * 0.0;
        double ny = dz * b.nx - dx * b.nz;
        double nz = dx * 0.0 - dy * b.nx;
        if (ny < 0) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        final double m = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return m < 1e-9 ? new double[]{0, 1, 0} : new double[]{nx / m, ny / m, nz / m};
    }

    private void box(Slice c, double alongPx, double lengthPx, double widthPx, double y0Px, double y1Px,
                     float[] uvSide, float[] uvTop, float[] uvEnd, boolean bottomFace) {
        orientedBox(c.x + c.tx * alongPx / 16.0, c.z + c.tz * alongPx / 16.0, c.tx, c.tz, lengthPx, widthPx,
                y0Px, y1Px, c.baseY, uvSide, uvTop, uvEnd == null ? uvSide : uvEnd, c.lx, c.ly, c.lz, bottomFace);
    }

    private void boxAt(double x, double z, Slice c, double lengthPx, double widthPx, double y0Px, double y1Px,
                       float[] uv, int lx, int ly, int lz) {
        orientedBox(x, z, c.tx, c.tz, lengthPx, widthPx, y0Px, y1Px, c.baseY, uv, uv, uv, lx, ly, lz, true);
    }

    private void orientedBox(double cx, double cz, double tx, double tz, double lengthPx, double widthPx,
                             double y0Px, double y1Px, double baseY, float[] uvSide, float[] uvTop, float[] uvEnd,
                             int lx, int ly, int lz) {
        orientedBox(cx, cz, tx, tz, lengthPx, widthPx, y0Px, y1Px, baseY, uvSide, uvTop, uvEnd, lx, ly, lz, true);
    }

    private void orientedBox(double cx, double cz, double tx, double tz, double lengthPx, double widthPx,
                             double y0Px, double y1Px, double baseY, float[] uvSide, float[] uvTop, float[] uvEnd,
                             int lx, int ly, int lz, boolean bottomFace) {
        final double nx = tz;
        final double nz = -tx;
        final double hl = lengthPx / 32.0;
        final double hw = widthPx / 32.0;
        final double y0 = baseY + y0Px / 16.0;
        final double y1 = baseY + y1Px / 16.0;
        final double[][] c = new double[4][];

        final double[][] lw = {{-hl, -hw}, {hl, -hw}, {hl, hw}, {-hl, hw}};
        for (int i = 0; i < 4; i++) {
            c[i] = new double[]{cx + tx * lw[i][0] + nx * lw[i][1], cz + tz * lw[i][0] + nz * lw[i][1]};
        }

        quadAt(new double[][]{{c[0][0], y1, c[0][1]}, {c[3][0], y1, c[3][1]}, {c[2][0], y1, c[2][1]},
                {c[1][0], y1, c[1][1]}}, 0, 1, 0, uvTop, lx, ly, lz);
        if (bottomFace) {
            quadAt(new double[][]{{c[0][0], y0, c[0][1]}, {c[1][0], y0, c[1][1]}, {c[2][0], y0, c[2][1]},
                    {c[3][0], y0, c[3][1]}}, 0, -1, 0, uvTop, lx, ly, lz);
        }

        final double[][] normals = {{-nx, -nz}, {tx, tz}, {nx, nz}, {-tx, -tz}};
        for (int i = 0; i < 4; i++) {
            final double[] a = c[i];
            final double[] b = c[(i + 1) % 4];
            quadAt(new double[][]{{a[0], y0, a[1]}, {b[0], y0, b[1]}, {b[0], y1, b[1]}, {a[0], y1, a[1]}},
                    normals[i][0], 0, normals[i][1], (i % 2 == 0) ? uvSide : uvEnd, lx, ly, lz);
        }
    }

    private void quad(double[][] pts, double nx, double ny, double nz, float[] uv, Slice c, boolean reverse) {
        quadAt(pts, nx, ny, nz, uv, c.lx, c.ly, c.lz);
    }

    private void quadAt(double[][] pts, double nx, double ny, double nz, float[] uv, int lx, int ly, int lz) {
        final double[] e1 = {pts[1][0] - pts[0][0], pts[1][1] - pts[0][1], pts[1][2] - pts[0][2]};
        final double[] e2 = {pts[2][0] - pts[0][0], pts[2][1] - pts[0][1], pts[2][2] - pts[0][2]};
        final double cx = e1[1] * e2[2] - e1[2] * e2[1];
        final double cy = e1[2] * e2[0] - e1[0] * e2[2];
        final double cz = e1[0] * e2[1] - e1[1] * e2[0];
        final boolean flip = cx * nx + cy * ny + cz * nz < 0;
        final float[] xyz = new float[12];
        for (int i = 0; i < 4; i++) {
            final double[] p = pts[flip ? 3 - i : i];
            xyz[3 * i] = (float) (p[0] - ox);
            xyz[3 * i + 1] = (float) (p[1] - oy);
            xyz[3 * i + 2] = (float) (p[2] - oz);
        }
        out.add(new Quad(xyz, (float) nx, (float) ny, (float) nz, uv, lx, ly, lz));
    }
}
