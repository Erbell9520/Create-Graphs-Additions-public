package com.createstats.chunkloader.look;

import static com.createstats.chunkloader.look.CrownGeometry.HORN_AZ;
import static com.createstats.chunkloader.look.CrownGeometry.MAX;
import static com.createstats.chunkloader.look.CrownGeometry.RIM_W;
import static com.createstats.chunkloader.look.CrownGeometry.RING_R;
import static com.createstats.chunkloader.look.CrownGeometry.RING_Y;
import static com.createstats.chunkloader.look.CrownGeometry.TIERS;

// decided world free, so previews show the shipped effects
public final class CrownFx {
    public interface Sink {
        void glow(double x, double y, double z, double radius, int rgb, double alpha, double core, double nudge);

        void stroke(double ax, double ay, double az, double bx, double by, double bz, double core, double sheath,
                    int coreRgb, int sheathRgb, double alpha);

        void band(double y, double r0, double r1, double a0, double a1, int rgb, double alpha0, double alpha1,
                  boolean floor);

        void flare(double x, double y, double z, double halfH, double halfV, double width, int coreRgb, int glowRgb,
                   double alpha);

        void star(double x, double y, double z, double size, double alpha);

        void front(double x0, double y0, double x1, double y1, double z, int rgb, double alpha);

        void spark(double x, double y, double z);
    }

    public static final int ROSE_CORE = 0xFFECEE;
    public static final int ROSE_MID = 0xFF5C94;
    public static final int ROSE_HALO = 0xC42666;
    public static final int ROSE_ARC = 0xFFE4F6;
    public static final int ROSE_ARC_GLOW = 0xDE70FF;
    public static final int WARN_CORE = 0xFFECD6;
    public static final int WARN_MID = 0xFF7A1E;
    public static final int WARN_HALO = 0xB83C0A;
    public static final int WARN_ARC = 0xFFD6AA;
    public static final int WARN_ARC_GLOW = 0xFF6E28;
    public static final int NIXIE = 0xFF9A3C;

    public static final double ARC_WINDOW = 11.0;

    public static final double RING_WINDOW = 7.0;

    public static final double SPARK_CLOSENESS = 0.82;

    public static final double NIXIE_X0 = 2.6;
    public static final double NIXIE_X1 = 8.2;
    public static final double NIXIE_Y0 = 3.8;
    public static final double NIXIE_Y1 = 8.4;
    public static final double NIXIE_GLASS_Z = 15.8;
    public static final double NIXIE_DIGIT_Z = 15.95;

    public static final double FLOOR_Y = 0.15;

    static final String[][] DIGITS = {
            {"01110", "10001", "10011", "10101", "11001", "10001", "01110"},
            {"00100", "01100", "00100", "00100", "00100", "00100", "01110"},
            {"01110", "10001", "00001", "00010", "00100", "01000", "11111"},
            {"11111", "00010", "00100", "00010", "00001", "10001", "01110"},
            {"00010", "00110", "01010", "10010", "11111", "00010", "00010"},
            {"11111", "10000", "11110", "00001", "00001", "10001", "01110"},
            {"00110", "01000", "10000", "11110", "10001", "10001", "01110"},
            {"11111", "00001", "00010", "00100", "01000", "01000", "01000"},
            {"01110", "10001", "10001", "01110", "10001", "10001", "01110"},
            {"01110", "10001", "10001", "01111", "00001", "00010", "01100"}};

    private final double[] px = new double[17];
    private final double[] py = new double[17];
    private final double[] pz = new double[17];
    private final double[] fx = new double[5];
    private final double[] fy = new double[5];
    private final double[] fz = new double[5];
    private final double[] pa = new double[3];
    private final double[] pb = new double[3];
    private long rng;

    public void emit(CrownPose p, CrownAnchors a, Sink s, int lod) {
        final double intensity = p.intensity;
        final double dim = p.dim;
        int tips = 0;
        int warn = 0;
        for (int i = 0; i < a.pods; i++) {
            if (tip(a, i)) {
                tips++;
                if (a.ink[i] == CrownPose.INK_WARN) {
                    warn++;
                }
            }
        }
        final boolean amber = warn * 2 > tips;
        final int mid = amber ? WARN_MID : ROSE_MID;
        final int halo = amber ? WARN_HALO : ROSE_HALO;
        final int arc = amber ? WARN_ARC : ROSE_ARC;
        final int arcGlow = amber ? WARN_ARC_GLOW : ROSE_ARC_GLOW;

        if (tips > 0) {
            final double reach = 8.0 + 20.0 * intensity;
            final double base = (0.10 + 0.22 * intensity) * dim;
            final int rings = 6;
            for (int i = 0; i < rings; i++) {
                final double r0 = reach * i / rings;
                final double r1 = reach * (i + 1) / rings;

                s.band(FLOOR_Y, r0, r1, 0.0, 360.0, halo, base * Math.pow(Math.max(0.0, 1.0 - r0 / reach), 2.4),
                        base * Math.pow(Math.max(0.0, 1.0 - r1 / reach), 2.4), true);
            }
        }

        if (p.sigilAlpha > 0.01 && tips > 0) {
            final double k = 0.30 * dim * p.sigilAlpha;
            for (int t = 0; t < TIERS; t++) {
                line(s, RING_R[t] + 1.6, 0.45, mid, 0.55 * k);
            }
            line(s, 21.0, 0.5, mid, 0.8 * k);
            line(s, 23.2, 0.35, mid, 0.8 * k);
            for (int i = 0; i < tips; i++) {
                final double a0 = p.sigilAngle + i * 360.0 / MAX;
                s.band(FLOOR_Y, 21.4, 22.9, a0 - 2.2, a0 + 2.2, mid, 0.7 * k, 0.7 * k, true);
            }
        }

        double pulseY = Double.NaN;
        if (p.pulse >= 0.0) {
            final double top = Math.max(p.stemTop, 11.6) + 2.0;
            pulseY = 11.4 + (top - 11.4) * p.pulse;
        }

        if (lod == CrownMesh.LOD_FULL) {
            for (int i = 0; i < a.pods; i++) {
                if (!tip(a, i) || a.mount[i] < 0.5) {
                    continue;
                }
                final int m = a.ink[i] == CrownPose.INK_WARN ? WARN_MID : ROSE_MID;
                final double rOut = a.r[i] + RIM_W[a.tier[i]] / 2 + 0.1;
                double boost = 1.0;
                if (!Double.isNaN(pulseY)) {
                    boost += (1.0 + 1.2 * intensity) * Math.max(0.0, 1.0 - Math.abs(pulseY - a.y[i]) / 3.0);
                }
                final double st = (0.10 + 0.10 * intensity) * a.glow[i] * dim * boost;
                for (int j = 0; j < 6; j++) {
                    final double az = a.a0[i] + (j + 0.5) * (a.a1[i] - a.a0[i]) / 6;
                    s.glow(CrownGeometry.atX(rOut, az), a.y[i], CrownGeometry.atZ(rOut, az), 0.55, m, st, 0.01, 0.2);
                }
            }
        }

        for (int i = 0; i < a.pods; i++) {
            if (!tip(a, i)) {
                continue;
            }
            final boolean w = a.ink[i] == CrownPose.INK_WARN;
            final double size = a.size[i] * (0.42 + 0.22 * intensity);
            final double st = a.glow[i] * (0.45 + 0.35 * intensity) * dim;
            s.glow(a.x[i], a.y[i], a.z[i], size, w ? WARN_MID : ROSE_MID, st * 0.75, 0.2, a.size[i] * 0.6);
            s.glow(a.x[i], a.y[i], a.z[i], size * 2.2, w ? WARN_HALO : ROSE_HALO, st * 0.16, 0.01, a.size[i] * 0.6);
        }

        arcs(p, a, s);

        if (lod == CrownMesh.LOD_FULL && p.spin > 1.0) {
            final double span = Math.min(40.0, 2.6 * p.spin);
            for (int i = 0; i < a.pods; i++) {
                if (!tip(a, i) || a.mount[i] < 0.95) {
                    continue;
                }
                final boolean w = a.ink[i] == CrownPose.INK_WARN;
                final int sign = CrownGeometry.dir(a.tier[i]);
                final double r = Math.hypot(a.x[i] - 8.0, a.z[i] - 8.0);
                final int n = 7;
                for (int j = 0; j < n; j++) {
                    final double f = 1.0 - j / (double) n;
                    final double b0 = a.az[i] - sign * j * span / n;
                    final double b1 = a.az[i] - sign * (j + 1) * span / n;
                    s.stroke(CrownGeometry.atX(r, b0), a.y[i], CrownGeometry.atZ(r, b0),
                            CrownGeometry.atX(r, b1), a.y[i], CrownGeometry.atZ(r, b1),
                            0.5 * a.size[i] / 2.4 * f, 1.4 * f, w ? WARN_MID : ROSE_MID, w ? WARN_HALO : ROSE_HALO,
                            0.5 * f * (0.4 + 0.6 * intensity) * dim);
                }
            }
        }

        if (a.capOpen > 0.02) {
            final double st = a.capOpen * dim;
            final double nudge = a.heartSize / 2 + 0.2;
            s.glow(a.heartX, a.heartY, a.heartZ, 0.9, mid, 0.55 * st, 0.01, nudge);
            s.glow(a.heartX, a.heartY, a.heartZ, 3.2, halo, 0.18 * st, 0.01, nudge);
            s.flare(a.heartX, a.heartY, a.heartZ, 4.6 * st, 3.4 * st, 0.16, 0xFFF0F5, mid, 0.7 * st);
            final double r = a.heartSize / 2 + 1.7;
            for (int j = 0; j < 18; j++) {
                final double b0 = Math.toRadians(p.haloPhase + j * 20.0);
                final double b1 = Math.toRadians(p.haloPhase + j * 20.0 + 11.0);
                s.stroke(a.heartX + r * Math.cos(b0), a.heartY, a.heartZ - r * Math.sin(b0),
                        a.heartX + r * Math.cos(b1), a.heartY, a.heartZ - r * Math.sin(b1), 0.22, 0.8, 0xFFE1EB, mid,
                        0.6 * st);
            }
        }

        if (p.arch > 0.02 && a.yoke >= 0.95 && a.yokeEnd[0] && a.yokeEnd[1] && a.capOpen > 0.5) {
            final double hh = a.heartSize / 2;
            for (int j = 0; j < 2; j++) {
                final double toward = a.yokeX[j] < a.heartX ? 1.0 : -1.0;
                pa[0] = a.yokeX[j];
                pa[1] = a.yokeY[j];
                pa[2] = a.yokeZ[j];
                pb[0] = a.heartX - toward * hh * 0.6;
                pb[1] = a.heartY + hh * 0.4;
                pb[2] = a.heartZ;
                final int n = jagged(pa, pb, p.arcSeed * 211 + j * 7, 0.3, 2, px, py, pz);
                strokes(s, n, px, py, pz, 0.36, 1.4, arc, arcGlow, Math.min(1.0, 1.2 * p.arch) * dim);
                s.glow(pa[0], pa[1], pa[2], 1.0, arcGlow, 0.7 * p.arch * dim, 0.01, 0.9);
                final double pvr = CrownGeometry.yokePivotR();
                final double sx = pvr - CrownGeometry.YOKE_SHOULDER;
                final double sy = CrownGeometry.YOKE_END_Y - 0.2;
                final double az = HORN_AZ[j];
                for (int c = 0; c < 3; c++) {
                    final double u = ((p.arcSeed * 0.17 + c / 3.0) % 1.0 + 1.0) % 1.0;
                    final double r = sx + (CrownGeometry.YOKE_END_R - sx) * u;
                    final double yy = sy + (CrownGeometry.YOKE_END_Y - sy) * u;
                    pa[0] = CrownGeometry.atX(r, az);
                    pa[1] = yy + 0.9;
                    pa[2] = CrownGeometry.atZ(r, az);
                    final double tr = r + 0.6 * (c % 2 == 1 ? 1 : -1);
                    pb[0] = CrownGeometry.atX(tr, az);
                    pb[1] = yy + 2.3;
                    pb[2] = CrownGeometry.atZ(tr, az);
                    final int m = jagged(pa, pb, p.arcSeed * 31 + c * 5 + j * 3, 0.35, 2, px, py, pz);
                    strokes(s, m, px, py, pz, 0.22, 0.8, arc, arcGlow, 0.6 * p.arch * dim);
                }
            }
        }

        if (!Double.isNaN(pulseY)) {
            final double w = pulseY < RING_Y[1] + 1.0 ? 1.5 : pulseY < RING_Y[3] + 1.0 ? 1.25 : 1.0;
            s.glow(8.0, pulseY, 8.0, 1.4, mid, 0.7 * (0.4 + 0.6 * intensity) * dim, 0.01, w + 0.4);
        }

        if (p.shockAlpha > 0.0) {
            final double y = RING_Y[TIERS - 1];
            final int n = 48;
            for (int j = 0; j < n; j++) {
                final double b0 = Math.toRadians(j * 360.0 / n);
                final double b1 = Math.toRadians((j + 1) * 360.0 / n);
                s.stroke(8 + p.shockRadius * Math.cos(b0), y, 8 - p.shockRadius * Math.sin(b0),
                        8 + p.shockRadius * Math.cos(b1), y, 8 - p.shockRadius * Math.sin(b1), 0.35, 2.2, 0xFFEBF5,
                        mid, p.shockAlpha * dim);
            }
        }

        if (p.flash > 0.0) {
            for (int i = 0; i < a.pods; i++) {
                if (a.mount[i] >= 0.5) {
                    s.star(a.x[i], a.y[i], a.z[i], a.size[i], p.flash);
                }
            }
        }

        if (p.nixieLit && p.count > 0) {
            final int c = Math.min(99, p.count);
            if (c >= 10) {
                digit(s, c / 10, 0);
            }
            digit(s, c % 10, 1);
        }

        if (p.blocked) {
            blocked(p, s);
        }
    }

    private static boolean tip(CrownAnchors a, int i) {
        return a.glow[i] > 0.01 && a.ink[i] != CrownPose.INK_OFF;
    }

    private static void line(Sink s, double r, double half, int rgb, double alpha) {
        s.band(FLOOR_Y, r - half, r, 0.0, 360.0, rgb, 0.0, alpha, true);
        s.band(FLOOR_Y, r, r + half, 0.0, 360.0, rgb, alpha, 0.0, true);
    }

    private void arcs(CrownPose p, CrownAnchors a, Sink s) {
        final double intensity = p.intensity;
        if (p.feed) {
            for (int i = 0; i < a.pods; i++) {
                if (!tip(a, i) || a.mount[i] < 0.9) {
                    continue;
                }
                final int t = a.tier[i];
                for (int side = 0; side < 2; side++) {
                    if (!a.knob[t * 2 + side]) {
                        continue;
                    }
                    final double d = CrownGeometry.angDiff(a.az[i], HORN_AZ[side]);
                    if (Math.abs(d) > ARC_WINDOW) {
                        continue;
                    }
                    final double closeness = 1.0 - Math.abs(d) / ARC_WINDOW;
                    final double rOut = Math.hypot(a.x[i] - 8.0, a.z[i] - 8.0) + a.size[i] / 2;
                    final int k = t * 2 + side;
                    pa[0] = a.knobX[k];
                    pa[1] = a.knobY[k];
                    pa[2] = a.knobZ[k];
                    pb[0] = CrownGeometry.atX(rOut, a.az[i]);
                    pb[1] = a.y[i];
                    pb[2] = CrownGeometry.atZ(rOut, a.az[i]);
                    arc(p, s, a.k[i], a.ink[i], closeness, intensity, true);
                }
            }
        }
        for (int i = 0; i < a.pods; i++) {
            if (!tip(a, i) || a.mount[i] < 0.9) {
                continue;
            }
            for (int j = 0; j < a.pods; j++) {
                if (a.tier[j] != a.tier[i] + 1 || !tip(a, j) || a.mount[j] < 0.9) {
                    continue;
                }
                final double d = CrownGeometry.angDiff(a.az[i], a.az[j]);
                if (Math.abs(d) > RING_WINDOW) {
                    continue;
                }
                pa[0] = a.x[i];
                pa[1] = a.y[i] + a.size[i] / 2;
                pa[2] = a.z[i];
                pb[0] = a.x[j];
                pb[1] = a.y[j] - a.size[j] / 2;
                pb[2] = a.z[j];
                arc(p, s, a.k[j], a.ink[j], 1.0 - Math.abs(d) / RING_WINDOW, intensity, false);
            }
        }
    }

    private void arc(CrownPose p, Sink s, int k, int ink, double closeness, double intensity, boolean horn) {
        final boolean w = ink == CrownPose.INK_WARN;
        final double weight = 0.75 + 0.5 * Math.sqrt(CrownGeometry.chunkCost(k) / 1024.0) / 3.14;
        final double strength = (0.5 + 0.5 * closeness) * (0.55 + 0.45 * intensity) * p.arcScale * p.dim;
        final long seed = p.arcSeed * 1009 + k * 31L + (horn ? 7 : 13) + (long) (pa[0] * 3);
        final int core = w ? WARN_ARC : ROSE_ARC;
        final int sheath = w ? WARN_ARC_GLOW : ROSE_ARC_GLOW;
        final double hx = pa[0];
        final double hy = pa[1];
        final double hz = pa[2];
        final int n = jagged(pa, pb, seed, 0.2, 3, px, py, pz);
        strokes(s, n, px, py, pz, 0.30 * weight, 1.15 * weight, core, sheath, strength);

        seed(seed + 77);
        if (uniform() < 0.55 && n > 6) {
            final int j = n / 2 + (int) Math.floor(uniform() * 5) - 2;
            final double dx = pb[0] - hx;
            final double dy = pb[1] - hy;
            final double dz = pb[2] - hz;
            final double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            final double g = gauss() * 0.35 * len;
            perpendicular(dx, dy, dz, uniform() * Math.PI * 2);
            final double bx = px[j];
            final double by = py[j];
            final double bz = pz[j];
            pa[0] = bx;
            pa[1] = by;
            pa[2] = bz;
            pb[0] = bx + dx * 0.25 + perp[0] * g;
            pb[1] = by + dy * 0.25 + perp[1] * g;
            pb[2] = bz + dz * 0.25 + perp[2] * g;
            final int m = jagged(pa, pb, seed + 5, 0.25, 2, fx, fy, fz);
            strokes(s, m, fx, fy, fz, Math.max(0.1, 0.30 * weight - 0.1), 0.6 * weight, core, sheath, strength * 0.6);
        }
        if (horn) {
            s.glow(hx, hy, hz, 0.8, sheath, 0.5 * closeness * p.arcScale * p.dim, 0.01, 0.6);
        }
        if (closeness >= SPARK_CLOSENESS) {
            if (horn) {
                s.spark(hx, hy, hz);
            } else {
                s.spark(px[n / 2], py[n / 2], pz[n / 2]);
            }
        }
    }

    private static void strokes(Sink s, int n, double[] x, double[] y, double[] z, double core, double sheath,
                                int coreRgb, int sheathRgb, double alpha) {
        for (int i = 0; i + 1 < n; i++) {
            s.stroke(x[i], y[i], z[i], x[i + 1], y[i + 1], z[i + 1], core, sheath, coreRgb, sheathRgb, alpha);
        }
    }

    private int jagged(double[] a, double[] b, long seed, double amp, int levels, double[] x, double[] y, double[] z) {
        seed(seed);
        final double lx = b[0] - a[0];
        final double ly = b[1] - a[1];
        final double lz = b[2] - a[2];
        final double length = Math.max(1e-6, Math.sqrt(lx * lx + ly * ly + lz * lz));
        x[0] = a[0];
        y[0] = a[1];
        z[0] = a[2];
        int n = 1 << levels;
        x[n] = b[0];
        y[n] = b[1];
        z[n] = b[2];
        int stride = n;
        for (int level = 0; level < levels; level++) {
            final double sigma = amp * length / (1 << level) * 0.5;
            for (int i = 0; i < n; i += stride) {
                final int m = i + stride / 2;
                final double dx = x[i + stride] - x[i];
                final double dy = y[i + stride] - y[i];
                final double dz = z[i + stride] - z[i];
                perpendicular(dx, dy, dz, uniform() * Math.PI * 2);
                final double g = gauss() * sigma;
                x[m] = (x[i] + x[i + stride]) / 2 + perp[0] * g;
                y[m] = (y[i] + y[i + stride]) / 2 + perp[1] * g;
                z[m] = (z[i] + z[i + stride]) / 2 + perp[2] * g;
            }
            stride /= 2;
        }
        return n + 1;
    }

    private final double[] perp = new double[3];

    private void perpendicular(double dx, double dy, double dz, double turn) {
        final double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-9) {
            perp[0] = 1;
            perp[1] = 0;
            perp[2] = 0;
            return;
        }
        final double ux = dx / l;
        final double uy = dy / l;
        final double uz = dz / l;

        double ax;
        double ay;
        double az;
        if (Math.abs(uy) < 0.9) {
            ax = -uz;
            ay = 0;
            az = ux;
        } else {
            ax = 0;
            ay = uz;
            az = -uy;
        }
        final double al = Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= al;
        ay /= al;
        az /= al;
        final double bx = uy * az - uz * ay;
        final double by = uz * ax - ux * az;
        final double bz = ux * ay - uy * ax;
        final double c = Math.cos(turn);
        final double s = Math.sin(turn);
        perp[0] = ax * c + bx * s;
        perp[1] = ay * c + by * s;
        perp[2] = az * c + bz * s;
    }

    private void digit(Sink s, int d, int place) {
        final String[] rows = DIGITS[d];
        final double sx = (NIXIE_X1 - NIXIE_X0) / 32.0;
        final double sy = (NIXIE_Y1 - NIXIE_Y0) / 24.0;
        final int ox = 4 + place * 14;
        final int oy = 5;
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 5; c++) {
                if (rows[r].charAt(c) != '1') {
                    continue;
                }
                final double tx0 = ox + c * 2;
                final double ty0 = oy + r * 2;
                final double x0 = NIXIE_X0 + tx0 * sx;
                final double x1 = NIXIE_X0 + (tx0 + 2) * sx;
                final double y1 = NIXIE_Y1 - ty0 * sy;
                final double y0 = NIXIE_Y1 - (ty0 + 2) * sy;
                s.front(x0 - sx, y0 - sy, x1 + sx, y1 + sy, NIXIE_DIGIT_Z, NIXIE, 0.45);
                s.front(x0, y0, x1, y1, NIXIE_DIGIT_Z, NIXIE, 1.0);
            }
        }
    }

    private void blocked(CrownPose p, Sink s) {
        final double fl = (p.tick % 7 == 3 ? 0.35 : 1.0) * (0.55 + 0.45 * CrownEase.flicker(p.tick));
        final double alpha = 0.6 * fl;
        final double x0 = -16.0 * p.blockRight;
        final double y0 = 16.0 * p.blockUp;
        final double z0 = 16.0 * p.blockFront;
        final double x1 = x0 + 16;
        final double y1 = y0 + 16;
        final double z1 = z0 + 16;
        edge(s, x0, y0, z0, x1, y0, z0, alpha);
        edge(s, x0, y0, z1, x1, y0, z1, alpha);
        edge(s, x0, y1, z0, x1, y1, z0, alpha);
        edge(s, x0, y1, z1, x1, y1, z1, alpha);
        edge(s, x0, y0, z0, x0, y1, z0, alpha);
        edge(s, x1, y0, z0, x1, y1, z0, alpha);
        edge(s, x0, y0, z1, x0, y1, z1, alpha);
        edge(s, x1, y0, z1, x1, y1, z1, alpha);
        edge(s, x0, y0, z0, x0, y0, z1, alpha);
        edge(s, x1, y0, z0, x1, y0, z1, alpha);
        edge(s, x0, y1, z0, x0, y1, z1, alpha);
        edge(s, x1, y1, z0, x1, y1, z1, alpha);
        int up = 0;
        for (int t = 0; t < TIERS; t++) {
            if (p.tierUp[t] > 0.5) {
                up = t + 1;
            }
        }
        final int want = CrownGeometry.tiersFor(p.wantForm);
        for (int t = up; t < want; t++) {
            final int n = 24;
            for (int j = 0; j < n; j += 2) {
                final double b0 = Math.toRadians(p.tierAngle[t] + j * 360.0 / n);
                final double b1 = Math.toRadians(p.tierAngle[t] + (j + 1) * 360.0 / n);
                final double r = RING_R[t];
                s.stroke(8 + r * Math.cos(b0), RING_Y[t], 8 - r * Math.sin(b0), 8 + r * Math.cos(b1), RING_Y[t],
                        8 - r * Math.sin(b1), 0.18, 0.7, ROSE_ARC, ROSE_MID, alpha * 0.8);
            }
        }
    }

    private static void edge(Sink s, double ax, double ay, double az, double bx, double by, double bz, double alpha) {
        s.stroke(ax, ay, az, bx, by, bz, 0.16, 0.7, ROSE_ARC, ROSE_MID, alpha);
    }

    private void seed(long seed) {
        rng = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
    }

    private double uniform() {
        long z = (rng += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }

    private double gauss() {
        final double u1 = Math.max(1e-12, uniform());
        final double u2 = uniform();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }
}
