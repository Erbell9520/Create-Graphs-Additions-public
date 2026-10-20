package com.createstats.chunkloader.look;

import static com.createstats.chunkloader.look.CrownGeometry.HORN_AZ;
import static com.createstats.chunkloader.look.CrownGeometry.HUB_R;
import static com.createstats.chunkloader.look.CrownGeometry.KNOB;
import static com.createstats.chunkloader.look.CrownGeometry.PER_TIER;
import static com.createstats.chunkloader.look.CrownGeometry.POD;
import static com.createstats.chunkloader.look.CrownGeometry.RIM_H;
import static com.createstats.chunkloader.look.CrownGeometry.RIM_W;
import static com.createstats.chunkloader.look.CrownGeometry.RING_R;
import static com.createstats.chunkloader.look.CrownGeometry.RING_Y;
import static com.createstats.chunkloader.look.CrownGeometry.SECTOR;
import static com.createstats.chunkloader.look.CrownGeometry.TIERS;

public final class CrownMesh {
    public interface Sink {
        void quad(float[] p, float[] uv, float nx, float ny, float nz, CrownMaterial material, boolean glow);
    }

    public static final int LOD_FULL = 0;

    public static final int LOD_FAR = 1;

    public static final int D = 1;
    public static final int U = 2;
    public static final int N = 4;
    public static final int S = 8;
    public static final int W = 16;
    public static final int E = 32;
    public static final int ALL = 63;

    public static final double DIAL_X0 = 8.9;
    public static final double DIAL_Y0 = 3.4;
    public static final double DIAL_X1 = 13.9;
    public static final double DIAL_Y1 = 8.4;
    public static final double DIAL_X = 11.4;
    public static final double DIAL_Y = 5.9;
    public static final double DIAL_FACE_Z = 15.85;
    public static final double NEEDLE_LEN = 1.5;
    public static final double NEEDLE_W = 0.3;

    private static final double[][] HORN = CrownGeometry.hornProfile();

    private static final double YOKE_BODY = 0.7;
    private static final double YOKE_REACH = 0.6;

    private static final double[] HUB_H = {0.8, 0.8, 0.9, 1.15, 1.2};

    private final float[] qp = new float[12];
    private final float[] quv = new float[8];
    private final double[] uvBox = new double[4];
    private final double[] yoke = new double[6];

    private Sink sink;
    private int lod;

    private double m00;
    private double m01;
    private double m02;
    private double m10;
    private double m11;
    private double m12;
    private double m20;
    private double m21;
    private double m22;
    private double tx;
    private double ty;
    private double tz;

    public void build(CrownPose p, CrownAnchors a, Sink s, int levelOfDetail) {
        this.sink = s;
        this.lod = levelOfDetail;
        a.clear();
        iris(p);
        instruments(p);
        stem(p, a);
        for (int t = 0; t < TIERS; t++) {
            ring(p, a, t);
        }
        horns(p, a);
        this.sink = null;
    }

    private void iris(CrownPose p) {
        reset();
        final double r = 2.4 + 1.4 * p.hatch;
        box(8 - r, 11.4, 8 - r, 8 + r, 11.7, 8 + r, CrownMaterial.BRASS_LO, false, U);
        box(8 - r, 11.4, 8 - r, 8 + r, 11.7, 8 + r, CrownMaterial.BRASS, false, N | S | W | E);
    }

    private void instruments(CrownPose p) {
        reset();

        final CrownMaterial pilot = switch (p.pilot) {
            case CrownPose.PILOT_GREEN -> CrownMaterial.PILOT_GREEN;
            case CrownPose.PILOT_WARN -> CrownMaterial.PILOT_WARN;
            case CrownPose.PILOT_RED -> CrownMaterial.PILOT_RED;
            default -> CrownMaterial.PILOT_OFF;
        };
        box(7.4, 9.8, 15.65, 8.6, 10.7, 15.95, pilot, p.pilot != CrownPose.PILOT_OFF, ALL & ~N);

        corners(DIAL_X0, DIAL_Y0, DIAL_FACE_Z, DIAL_X1, DIAL_Y0, DIAL_FACE_Z, DIAL_X1, DIAL_Y1, DIAL_FACE_Z, DIAL_X0,
                DIAL_Y1, DIAL_FACE_Z);
        uvs(0, 0, 16, 16);
        emit(CrownMaterial.DIAL, p.lamps, 0, 0, 1, false);

        final double ang = Math.toRadians(135.0 + 270.0 * Math.max(0.0, Math.min(1.0, p.store)));
        final double dx = Math.cos(ang);
        final double dy = -Math.sin(ang);
        final double ox = -dy * NEEDLE_W / 2;
        final double oy = dx * NEEDLE_W / 2;
        final double r0 = 0.5;
        final double z = DIAL_FACE_Z + 0.1;
        final double bx = DIAL_X + dx * r0;
        final double by = DIAL_Y + dy * r0;
        final double ex = DIAL_X + dx * NEEDLE_LEN;
        final double ey = DIAL_Y + dy * NEEDLE_LEN;
        corners(bx - ox, by - oy, z, ex - ox, ey - oy, z, ex + ox, ey + oy, z, bx + ox, by + oy, z);
        uvs(4, 4, 12, 12);
        emit(p.needleWarn ? CrownMaterial.NEEDLE_WARN : CrownMaterial.NEEDLE, false, 0, 0, 1, true);

        box(DIAL_X - 0.35, DIAL_Y - 0.35, DIAL_FACE_Z, DIAL_X + 0.35, DIAL_Y + 0.35, DIAL_FACE_Z + 0.14,
                CrownMaterial.BRASS, false, ALL & ~N);

        bar(2.3, 3.2, p.loadA);
        bar(12.8, 13.7, p.loadB);
    }

    private void bar(double x0, double x1, double load) {
        final int segs = 5;
        for (int i = 0; i < segs; i++) {
            final double y0 = 2.4 + i * 1.35;
            final boolean lit = load * segs > i + 0.15 || (i == 0 && load > 1e-3);
            final CrownMaterial m = lit ? (i == segs - 1 ? CrownMaterial.SEG_WARN : CrownMaterial.SEG_GREEN)
                    : CrownMaterial.SEG_OFF;
            box(x0, y0, 0.05, x1, y0 + 1.0, 0.7, m, lit, ALL & ~S);
        }
    }

    private void stem(CrownPose p, CrownAnchors a) {
        reset();
        final double top = p.stemTop;
        stemSection(11.4, Math.min(top, RING_Y[1] + 1.0), 3.0, 0.25);
        stemSection(RING_Y[1] - 1.0, Math.min(top, RING_Y[3] + 1.0), 2.5, 0.35);
        stemSection(RING_Y[3] - 1.0, top, 2.0, 0.35);
        final double capW = 2.6;
        box(8 - capW / 2, top, 8 - capW / 2, 8 + capW / 2, top + 0.6, 8 + capW / 2, CrownMaterial.BRASS_FRAME, false,
                ALL & ~D);
        final double heart = 1.2 + 1.1 * p.capOpen;
        final double hy = top + 0.6 + heart / 2 + 0.3 * p.capOpen;
        final boolean lit = p.capOpen > 0.05;
        box(8 - heart / 2, hy - heart / 2, 8 - heart / 2, 8 + heart / 2, hy + heart / 2, 8 + heart / 2,
                lit ? CrownMaterial.ROSE_4 : CrownMaterial.ROSE_0, lit, ALL);
        final double tilt = 22.0 * (1.0 - p.capOpen) - 82.0 * p.capOpen;
        for (int k = 0; k < 4; k++) {
            reset();
            move(0, 0, -1.05);
            then('x', tilt, 8, top + 0.6, 8 - 1.25);
            then('y', 90.0 * k + 45.0, 8, 0, 8);
            box(8 - 1.0, top + 0.6, 8 - 0.2, 8 + 1.0, top + 1.9, 8 + 0.2, CrownMaterial.BRASS_FRAME, false, ALL);
        }
        a.heartX = 8.0;
        a.heartY = hy;
        a.heartZ = 8.0;
        a.heartSize = heart;
        a.capOpen = p.capOpen;
    }

    private void stemSection(double y0, double y1, double w, double margin) {
        if (y1 - y0 <= 0.05) {
            return;
        }
        box(8 - w / 2, y0, 8 - w / 2, 8 + w / 2, y1, 8 + w / 2, CrownMaterial.STEEL_LO, false, ALL & ~D & ~U);
        box(8 - w / 2 - margin, y1 - 0.7, 8 - w / 2 - margin, 8 + w / 2 + margin, y1, 8 + w / 2 + margin,
                CrownMaterial.BRASS, false, ALL);
    }

    private void ring(CrownPose p, CrownAnchors a, int t) {
        final double up = p.tierUp[t];
        if (up <= 0.02) {
            return;
        }
        final double sink = (1.0 - Math.min(1.0, up)) * 2.4;
        final double y = (t == 0 ? p.ring0Y : RING_Y[t]) - sink;
        final double ang = p.tierAngle[t];
        final double shrink = Math.min(1.0, up);
        final double hr = HUB_R[t] * (0.55 + 0.45 * shrink);
        final double rr = RING_R[t];
        final double w = RIM_W[t];
        final double h = RIM_H[t];
        final double pod = POD[t];

        reset();
        then('y', ang + 9.0, 0, 0, 0);
        move(8, 0, 8);
        final double hubH = HUB_H[t];
        box(-hr, y - hubH, -hr, hr, y + hubH, hr, CrownMaterial.BRASS_FRAME, false, ALL);
        box(-hr - 0.2, y - 0.3, -hr - 0.2, hr + 0.2, y + 0.3, hr + 0.2, CrownMaterial.BRASS_LO, false, ALL);

        final double rStow = (HUB_R[t] + w / 2 + 0.45) * shrink + 0.6 * (1.0 - shrink);
        double reach = 0.0;
        for (int s = 0; s < PER_TIER; s++) {
            reach = Math.max(reach, Math.min(1.0, Math.max(0.0, p.mount[t * PER_TIER + s])));
        }
        final double spokeOut = hr + (rStow - w / 2 - hr) * shrink + (rr - rStow) * shrink * reach;
        for (int s = 0; s < PER_TIER; s++) {
            at(ang + s * SECTOR);

            box(hr, y - 0.42, -0.35, spokeOut, y + 0.42, 0.35, CrownMaterial.STEEL_LO, false, ALL & ~W);
            if (up > 0.6 && reach > 0.6 && lod == LOD_FULL) {
                final double cl = Math.min(1.0, (up - 0.6) / 0.4) * Math.min(1.0, (reach - 0.6) / 0.4);
                box(spokeOut - 0.2, y - h / 2 - 0.3 * cl, -0.55, spokeOut + w + 0.2, y + h / 2 + 0.3 * cl, 0.55,
                        CrownMaterial.BRASS, false, ALL);
            }
        }

        final int nseg = 3 + t;
        for (int s = 0; s < PER_TIER; s++) {
            final int k = t * PER_TIER + s + 1;
            final double m = Math.max(0.0, p.mount[k - 1]);
            final double r = rStow + (rr - rStow) * m;
            final CrownMaterial tex = CrownMaterial.electrode(p.ink[k - 1], p.glow[k - 1]);
            final boolean lit = tex.lit();
            final double a0 = s * SECTOR + 3.0;
            final double a1 = (s + 1) * SECTOR - 3.0;
            final double step = (a1 - a0) / nseg;
            final double hh = h * (0.7 + 0.3 * Math.min(1.0, m));
            final double half = Math.toRadians(step / 2);
            final double c = 2 * r * Math.sin(half) + 0.06;
            final double rm = r * Math.cos(half);
            final double hs = step / 2;
            for (int j = 0; j < nseg; j++) {
                final double am = ang + a0 + (j + 0.5) * step;

                int mask = ALL;
                if (j > 0) {
                    mask &= ~S;
                }
                if (j < nseg - 1) {
                    mask &= ~N;
                }
                wedge(r - w / 2, r + w / 2, y - hh / 2, y + hh / 2, am, hs, 1.0,
                        rm - w / 2, y - hh / 2, -c / 2, rm + w / 2, y + hh / 2, c / 2, CrownMaterial.RIM, false, mask);
                final double inset = Math.max(0.0, (c / 2 - 0.05) / (c / 2));

                final boolean far = lod != LOD_FULL;
                wedge(r + w / 2, r + w / 2 + 0.18, y - hh * 0.28, y + hh * 0.28, am, hs, inset,
                        rm + w / 2, y - hh * 0.28, -c / 2 + 0.05, rm + w / 2 + 0.18, y + hh * 0.28, c / 2 - 0.05,
                        tex, lit, far ? E : ALL & ~W);
                wedge(r - w * 0.38, r + w * 0.38, y + hh / 2, y + hh / 2 + 0.12, am, hs, inset,
                        rm - w * 0.38, y + hh / 2, -c / 2 + 0.05, rm + w * 0.38, y + hh / 2 + 0.12, c / 2 - 0.05,
                        tex, lit, far ? U : ALL & ~D);
            }
            final double grow = Math.max(0.0, Math.min(1.0, (m - 0.55) / 0.45));
            final double mid = s * SECTOR + SECTOR / 2;
            if (grow > 0.02) {
                final double pz = pod * grow;
                final double pc = r + w / 2 + pz / 2;
                at(ang + mid);
                box(pc - pz / 2, y - pz / 2, -pz * 0.6, pc + pz / 2, y + pz / 2, pz * 0.6, tex, lit, ALL);
                if (lod == LOD_FULL) {
                    box(r + w / 2 - 0.1, y - pz * 0.32, -pz * 0.75, r + w / 2 + 0.35, y + pz * 0.32, pz * 0.75,
                            CrownMaterial.BRASS, false, ALL & ~W);
                }
                final int i = a.pods++;
                final double az = ang + mid;
                a.k[i] = k;
                a.tier[i] = t;
                a.ink[i] = p.ink[k - 1];
                a.x[i] = CrownGeometry.atX(pc, az);
                a.y[i] = y;
                a.z[i] = CrownGeometry.atZ(pc, az);
                a.size[i] = pz;
                a.glow[i] = lit ? p.glow[k - 1] : 0.0;
                a.mount[i] = m;
                a.az[i] = ((az % 360.0) + 360.0) % 360.0;
                a.r[i] = r;
                a.a0[i] = ang + a0;
                a.a1[i] = ang + a1;
            }
        }
    }

    private void horns(CrownPose p, CrownAnchors a) {
        for (int side = 0; side < 2; side++) {
            final double az = HORN_AZ[side];
            a.knob(0, side, CrownGeometry.atX(CrownGeometry.knobInner(0) + 0.15, az), CrownGeometry.knobHeight(0),
                    CrownGeometry.atZ(CrownGeometry.knobInner(0) + 0.15, az));
        }
        final double top = p.hornTop;
        if (top <= CrownGeometry.HORN_ROOT_Y + 0.05) {
            return;
        }
        final double[][] pts = HORN;
        for (int side = 0; side < 2; side++) {
            final double az = HORN_AZ[side];
            double pr = pts[0][0];
            double py = pts[0][1];
            for (int i = 1; i < TIERS; i++) {
                if (py >= top) {
                    break;
                }
                double er = pts[i][0];
                double ey = pts[i][1];
                boolean whole = true;
                if (ey > top) {
                    final double u = (top - py) / (ey - py);
                    er = pr + (er - pr) * u;
                    ey = top;
                    whole = false;
                }
                insulator(pr, py, er, ey, az, 0.9, 1.75, true);
                final double kn = KNOB[i];
                at(az);
                box(er - kn / 2, ey - kn / 2, -kn / 2, er + kn / 2, ey + kn / 2, kn / 2, CrownMaterial.BRASS_FRAME,
                        false, ALL);
                if (whole) {
                    a.knob(i, side, CrownGeometry.atX(er - kn / 2, az), ey, CrownGeometry.atZ(er - kn / 2, az));
                }
                pr = er;
                py = ey;
            }
            if (top >= CrownGeometry.knobHeight(TIERS - 1) - 0.01 && (p.yokeStub > 0.02 || p.yokeExt > 0.02)) {
                CrownGeometry.yokeArm(p.yokeStub, p.yokeSwing, p.yokeExt, yoke);
                insulator(yoke[0], yoke[1], yoke[2], yoke[3], az, YOKE_BODY, 1.1, true);
                final double reachLen = Math.hypot(yoke[4] - yoke[2], yoke[5] - yoke[3]);
                if (reachLen > 0.1) {
                    if (reachLen > 1.7) {
                        at(az);
                        box(yoke[2] - 0.8, yoke[3] - 0.8, -0.8, yoke[2] + 0.8, yoke[3] + 0.8, 0.8,
                                CrownMaterial.BRASS_FRAME, false, ALL);
                    }
                    insulator(yoke[2], yoke[3], yoke[4], yoke[5], az, YOKE_REACH, 1.1, false);
                }
                at(az);
                box(yoke[4] - 0.8, yoke[5] - 0.8, -0.8, yoke[4] + 0.8, yoke[5] + 0.8, 0.8, CrownMaterial.BRASS_FRAME,
                        false, ALL);
                a.yokeEnd[side] = true;
                a.yokeX[side] = CrownGeometry.atX(yoke[4], az);
                a.yokeY[side] = yoke[5];
                a.yokeZ[side] = CrownGeometry.atZ(yoke[4], az);
            }
        }
        a.yoke = Math.min(p.yokeSwing, p.yokeExt);
    }

    private void insulator(double ar, double ay, double br, double by, double az, double body, double shed,
                           boolean ferrule) {
        final double dr = br - ar;
        final double dy = by - ay;
        final double len = Math.hypot(dr, dy);
        if (len <= 0.05) {
            return;
        }
        reset();
        then('z', Math.toDegrees(Math.atan2(dy, dr)), 0, 0, 0);
        move(ar, ay, 0);
        then('y', az, 0, 0, 0);
        move(8, 0, 8);
        box(0, -body, -body, len, body, body, CrownMaterial.INSULATOR, false, ferrule ? ALL & ~W : ALL);
        // no shed on a short string, a half folded yoke would swing it past the reach

        if (lod == LOD_FULL && len >= 1.6) {
            final int sheds = Math.max(1, (int) (len / 2.0));
            for (int j = 0; j < sheds; j++) {
                final double sx = (j + 0.5) * len / sheds;
                box(sx - 0.3, -shed, -shed, sx + 0.3, shed, shed, CrownMaterial.INSULATOR, false, ALL);
            }
        }
        if (ferrule) {
            box(0.0, -body - 0.1, -body - 0.1, 0.7, body + 0.1, body + 0.1, CrownMaterial.BRASS_LO, false, ALL);
        }
    }

    private void reset() {
        m00 = 1;
        m01 = 0;
        m02 = 0;
        m10 = 0;
        m11 = 1;
        m12 = 0;
        m20 = 0;
        m21 = 0;
        m22 = 1;
        tx = 0;
        ty = 0;
        tz = 0;
    }

    private void at(double az) {
        reset();
        then('y', az, 0, 0, 0);
        move(8, 0, 8);
    }

    private void then(char axis, double deg, double cx, double cy, double cz) {
        if (Math.abs(deg) < 1e-9) {
            return;
        }
        final double a = Math.toRadians(deg);
        final double c = Math.cos(a);
        final double s = Math.sin(a);
        double r00 = 1;
        double r01 = 0;
        double r02 = 0;
        double r10 = 0;
        double r11 = 1;
        double r12 = 0;
        double r20 = 0;
        double r21 = 0;
        double r22 = 1;
        switch (axis) {
            case 'x' -> {
                r11 = c;
                r12 = -s;
                r21 = s;
                r22 = c;
            }
            case 'y' -> {
                r00 = c;
                r02 = s;
                r20 = -s;
                r22 = c;
            }
            default -> {
                r00 = c;
                r01 = -s;
                r10 = s;
                r11 = c;
            }
        }
        final double n00 = r00 * m00 + r01 * m10 + r02 * m20;
        final double n01 = r00 * m01 + r01 * m11 + r02 * m21;
        final double n02 = r00 * m02 + r01 * m12 + r02 * m22;
        final double n10 = r10 * m00 + r11 * m10 + r12 * m20;
        final double n11 = r10 * m01 + r11 * m11 + r12 * m21;
        final double n12 = r10 * m02 + r11 * m12 + r12 * m22;
        final double n20 = r20 * m00 + r21 * m10 + r22 * m20;
        final double n21 = r20 * m01 + r21 * m11 + r22 * m21;
        final double n22 = r20 * m02 + r21 * m12 + r22 * m22;
        final double px = tx - cx;
        final double py = ty - cy;
        final double pz = tz - cz;
        tx = r00 * px + r01 * py + r02 * pz + cx;
        ty = r10 * px + r11 * py + r12 * pz + cy;
        tz = r20 * px + r21 * py + r22 * pz + cz;
        m00 = n00;
        m01 = n01;
        m02 = n02;
        m10 = n10;
        m11 = n11;
        m12 = n12;
        m20 = n20;
        m21 = n21;
        m22 = n22;
    }

    private void move(double dx, double dy, double dz) {
        tx += dx;
        ty += dy;
        tz += dz;
    }

    private void put(int i, double x, double y, double z) {
        qp[i * 3] = (float) (m00 * x + m01 * y + m02 * z + tx);
        qp[i * 3 + 1] = (float) (m10 * x + m11 * y + m12 * z + ty);
        qp[i * 3 + 2] = (float) (m20 * x + m21 * y + m22 * z + tz);
    }

    private void corners(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy,
                         double cz, double dx, double dy, double dz) {
        put(0, ax, ay, az);
        put(1, bx, by, bz);
        put(2, cx, cy, cz);
        put(3, dx, dy, dz);
    }

    private void uvs(double u0, double v0, double u1, double v1) {
        quv[0] = (float) u0;
        quv[1] = (float) v1;
        quv[2] = (float) u1;
        quv[3] = (float) v1;
        quv[4] = (float) u1;
        quv[5] = (float) v0;
        quv[6] = (float) u0;
        quv[7] = (float) v0;
    }

    private void emit(CrownMaterial m, boolean glow, double nx, double ny, double nz, boolean fromCorners) {
        double ox;
        double oy;
        double oz;
        if (fromCorners) {
            final double ax = qp[3] - qp[0];
            final double ay = qp[4] - qp[1];
            final double az = qp[5] - qp[2];
            final double bx = qp[9] - qp[0];
            final double by = qp[10] - qp[1];
            final double bz = qp[11] - qp[2];
            ox = ay * bz - az * by;
            oy = az * bx - ax * bz;
            oz = ax * by - ay * bx;
            final double l = Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (l < 1e-9) {
                return;
            }
            ox /= l;
            oy /= l;
            oz /= l;
        } else {
            ox = m00 * nx + m01 * ny + m02 * nz;
            oy = m10 * nx + m11 * ny + m12 * nz;
            oz = m20 * nx + m21 * ny + m22 * nz;
        }
        sink.quad(qp, quv, (float) ox, (float) oy, (float) oz, m, glow);
    }

    private static double wrapLo(double a0, double a1) {
        final double span = a1 - a0;
        if (span >= 16) {
            return 0.0;
        }
        double lo = ((a0 % 16.0) + 16.0) % 16.0;
        if (lo + span > 16.0) {
            lo = 16.0 - span;
        }
        return lo;
    }

    private void faceUv(int face, double x0, double y0, double z0, double x1, double y1, double z1) {
        double u0;
        double u1;
        double v0;
        double v1;
        if (face == D || face == U) {
            u0 = wrapLo(x0, x1);
            u1 = x1 - x0 >= 16 ? 16.0 : u0 + (x1 - x0);
            v0 = wrapLo(z0, z1);
            v1 = z1 - z0 >= 16 ? 16.0 : v0 + (z1 - z0);
        } else {
            if (face == N || face == S) {
                u0 = wrapLo(x0, x1);
                u1 = x1 - x0 >= 16 ? 16.0 : u0 + (x1 - x0);
            } else {
                u0 = wrapLo(z0, z1);
                u1 = z1 - z0 >= 16 ? 16.0 : u0 + (z1 - z0);
            }
            if (y1 - y0 < 16) {
                v0 = wrapLo(16 - y1, 16 - y0);
                v1 = v0 + (y1 - y0);
            } else {
                v0 = 0.0;
                v1 = 16.0;
            }
        }
        uvBox[0] = u0;
        uvBox[1] = v0;
        uvBox[2] = u1;
        uvBox[3] = v1;
    }

    void box(double x0, double y0, double z0, double x1, double y1, double z1, CrownMaterial m, boolean glow,
             int mask) {
        final double dx = x1 - x0;
        final double dy = y1 - y0;
        final double dz = z1 - z0;
        if ((mask & D) != 0 && dx > 1e-6 && dz > 1e-6) {
            corners(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            faceUv(D, x0, y0, z0, x1, y1, z1);
            uvs(uvBox[0], uvBox[1], uvBox[2], uvBox[3]);
            emit(m, glow, 0, -1, 0, false);
        }
        if ((mask & U) != 0 && dx > 1e-6 && dz > 1e-6) {
            corners(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
            faceUv(U, x0, y0, z0, x1, y1, z1);
            uvs(uvBox[0], uvBox[1], uvBox[2], uvBox[3]);
            emit(m, glow, 0, 1, 0, false);
        }
        if ((mask & N) != 0 && dx > 1e-6 && dy > 1e-6) {
            corners(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
            faceUv(N, x0, y0, z0, x1, y1, z1);
            uvs(uvBox[0], uvBox[1], uvBox[2], uvBox[3]);
            emit(m, glow, 0, 0, -1, false);
        }
        if ((mask & S) != 0 && dx > 1e-6 && dy > 1e-6) {
            corners(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            faceUv(S, x0, y0, z0, x1, y1, z1);
            uvs(uvBox[0], uvBox[1], uvBox[2], uvBox[3]);
            emit(m, glow, 0, 0, 1, false);
        }
        if ((mask & W) != 0 && dz > 1e-6 && dy > 1e-6) {
            corners(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            faceUv(W, x0, y0, z0, x1, y1, z1);
            uvs(uvBox[0], uvBox[1], uvBox[2], uvBox[3]);
            emit(m, glow, -1, 0, 0, false);
        }
        if ((mask & E) != 0 && dz > 1e-6 && dy > 1e-6) {
            corners(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
            faceUv(E, x0, y0, z0, x1, y1, z1);
            uvs(uvBox[0], uvBox[1], uvBox[2], uvBox[3]);
            emit(m, glow, 1, 0, 0, false);
        }
    }

    private void wedge(double r0, double r1, double y0, double y1, double am, double hs, double fraction,
                       double bx0, double by0, double bz0, double bx1, double by1, double bz1, CrownMaterial m,
                       boolean glow, int mask) {
        if (r1 - r0 <= 1e-6 || y1 - y0 <= 1e-6) {
            return;
        }
        final double lo = Math.toRadians(am - hs * fraction);
        final double hi = Math.toRadians(am + hs * fraction);
        final double cl = Math.cos(lo);
        final double sl = Math.sin(lo);
        final double ch = Math.cos(hi);
        final double sh = Math.sin(hi);
        reset();

        final double xi0 = 8 + r0 * cl;
        final double zi0 = 8 - r0 * sl;
        final double xo0 = 8 + r1 * cl;
        final double zo0 = 8 - r1 * sl;
        final double xi1 = 8 + r0 * ch;
        final double zi1 = 8 - r0 * sh;
        final double xo1 = 8 + r1 * ch;
        final double zo1 = 8 - r1 * sh;

        if ((mask & D) != 0) {
            corners(xi1, y0, zi1, xo1, y0, zo1, xo0, y0, zo0, xi0, y0, zi0);
            faceUv(D, bx0, by0, bz0, bx1, by1, bz1);
            uvs(uvBox[0], uvBox[1], uvBox[2], uvBox[3]);
            emit(m, glow, 0, 0, 0, true);
        }

        if ((mask & U) != 0) {
            corners(xi0, y1, zi0, xo0, y1, zo0, xo1, y1, zo1, xi1, y1, zi1);
            faceUv(U, bx0, by0, bz0, bx1, by1, bz1);
            uvs(uvBox[0], uvBox[1], uvBox[2], uvBox[3]);
            emit(m, glow, 0, 0, 0, true);
        }

        if ((mask & N) != 0) {
            corners(xo1, y0, zo1, xi1, y0, zi1, xi1, y1, zi1, xo1, y1, zo1);
            faceUv(N, bx0, by0, bz0, bx1, by1, bz1);
            uvs(uvBox[0], uvBox[1], uvBox[2], uvBox[3]);
            emit(m, glow, 0, 0, 0, true);
        }

        if ((mask & S) != 0) {
            corners(xi0, y0, zi0, xo0, y0, zo0, xo0, y1, zo0, xi0, y1, zi0);
            faceUv(S, bx0, by0, bz0, bx1, by1, bz1);
            uvs(uvBox[0], uvBox[1], uvBox[2], uvBox[3]);
            emit(m, glow, 0, 0, 0, true);
        }

        if ((mask & W) != 0) {
            corners(xi1, y0, zi1, xi0, y0, zi0, xi0, y1, zi0, xi1, y1, zi1);
            faceUv(W, bx0, by0, bz0, bx1, by1, bz1);
            uvs(uvBox[0], uvBox[1], uvBox[2], uvBox[3]);
            emit(m, glow, 0, 0, 0, true);
        }

        if ((mask & E) != 0) {
            corners(xo0, y0, zo0, xo1, y0, zo1, xo1, y1, zo1, xo0, y1, zo0);
            faceUv(E, bx0, by0, bz0, bx1, by1, bz1);
            uvs(uvBox[0], uvBox[1], uvBox[2], uvBox[3]);
            emit(m, glow, 0, 0, 0, true);
        }
    }
}
