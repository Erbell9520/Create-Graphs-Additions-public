package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailerSteerTest {
    private static final double LB = 3.0;
    private static final double E = 0.5;
    private static final double L = 4.0;
    private static final double W = 1.0;
    private static final double DT = 0.05;

    private static final class Sim {
        double px;
        double py;
        double t0 = Math.PI / 2;
        double tt = Math.PI / 2;

        double hx() {
            return px - E * Math.cos(t0);
        }

        double hy() {
            return py - E * Math.sin(t0);
        }

        double ax() {
            return hx() - L * Math.cos(tt);
        }

        double ay() {
            return hy() - L * Math.sin(tt);
        }

        double articulationDeg() {
            return Math.toDegrees(TrailerSteer.wrapPi(tt - t0));
        }

        void step(double v, double frontSteer, boolean assisted) {
            double omega0 = v * Math.tan(frontSteer) / LB;
            double fx = Math.cos(t0);
            double fy = Math.sin(t0);

            double vhx = v * fx + omega0 * E * fy;
            double vhy = v * fy - omega0 * E * fx;
            double delta = 0.0;
            if (assisted) {
                double[] angles = TrailerSteer.angles(input(v, frontSteer, vhx, vhy, omega0));
                if (angles != null) delta = -Math.toRadians((angles[0] + angles[1]) / 2.0);
            }
            double h = tt + delta;
            double omegaT = (vhx * -Math.sin(h) + vhy * Math.cos(h)) / (L * Math.cos(delta));
            px += v * fx * DT;
            py += v * fy * DT;
            t0 += omega0 * DT;
            tt += omegaT * DT;
        }

        TrailerSteer.Input input(double v, double frontSteer, double vhx, double vhy, double omega0) {
            double fx = Math.cos(t0);
            double fy = Math.sin(t0);
            double rx = fy;
            double ry = -fx;
            double[] h = frame(hx(), hy(), fx, fy, rx, ry);
            double nx = -Math.sin(tt);
            double ny = Math.cos(tt);
            double[] w1 = frame(ax() + W * nx, ay() + W * ny, fx, fy, rx, ry);
            double[] w2 = frame(ax() - W * nx, ay() - W * ny, fx, fy, rx, ry);
            double[] centre = Math.abs(frontSteer) < 1e-9 ? null : new double[]{-LB / Math.tan(frontSteer), 0.0};
            return new TrailerSteer.Input(h[0], h[1], vhx * rx + vhy * ry, vhx * fx + vhy * fy, tt - t0 + Math.PI / 2,
                    omega0, centre, Math.PI / 2, new double[]{w1[0], w2[0]}, new double[]{w1[1], w2[1]}, LinkKind.HITCH, 30.0);
        }

        private double[] frame(double x, double y, double fx, double fy, double rx, double ry) {
            double dx = x - px;
            double dy = y - py;
            return new double[]{dx * rx + dy * ry, dx * fx + dy * fy};
        }
    }

    @Test
    void reversingStraightAPassiveTrailerJackknifesAndAnAssistedOneStraightens() {
        Sim passive = new Sim();
        Sim assisted = new Sim();
        passive.tt += Math.toRadians(10);
        assisted.tt += Math.toRadians(10);
        for (int i = 0; i < 300; i++) {
            passive.step(-2.0, 0.0, false);
            assisted.step(-2.0, 0.0, true);
        }
        assertTrue(Math.abs(passive.articulationDeg()) > 45.0, "passive: " + passive.articulationDeg());
        assertTrue(Math.abs(assisted.articulationDeg()) < 2.0, "assisted: " + assisted.articulationDeg());
    }

    @Test
    void reversingAroundABendTheTrailerSettlesInsteadOfFolding() {
        Sim assisted = new Sim();
        double last = 0;
        for (int i = 0; i < 600; i++) {
            assisted.step(-2.0, Math.toRadians(10), true);
            if (i == 580) last = assisted.articulationDeg();
            assertTrue(Math.abs(assisted.articulationDeg()) < 40.0, "folded to " + assisted.articulationDeg() + " at tick " + i);
        }
        assertEquals(last, assisted.articulationDeg(), 0.5, "settled");
    }

    @Test
    void goingForwardAnAssistedTrailerRunsInTheHitchesTrackWhereAPassiveOneCutsIn() {
        double steer = Math.atan(LB / 8.0);
        Sim passive = new Sim();
        Sim assisted = new Sim();
        for (int i = 0; i < 800; i++) {
            passive.step(3.0, steer, false);
            assisted.step(3.0, steer, true);
        }
        double passiveCut = cut(passive, steer);
        double assistedCut = cut(assisted, steer);
        assertTrue(passiveCut > 0.8, "a passive trailer cuts the corner by about L^2 / 2R: " + passiveCut);
        assertTrue(Math.abs(assistedCut) < 0.3, "assisted cut " + assistedCut);
    }

    private static double cut(Sim s, double steer) {
        double r = LB / Math.tan(steer);
        double cx = s.px - r * Math.sin(s.t0);
        double cy = s.py + r * Math.cos(s.t0);
        double hitch = Math.hypot(s.hx() - cx, s.hy() - cy);
        double axle = Math.hypot(s.ax() - cx, s.ay() - cy);
        return hitch - axle;
    }

    @Test
    void aStraightTrailerGoingStraightKeepsItsWheelsStraight() {
        Sim s = new Sim();
        double[] a = TrailerSteer.angles(s.input(3.0, 0.0, 0.0, 3.0, 0.0));
        assertNotNull(a);
        assertEquals(0.0, a[0], 1e-6);
        assertEquals(0.0, a[1], 1e-6);
    }

    @Test
    void atACrawlTheWheelsAreLeftAlone() {
        Sim s = new Sim();
        assertNull(TrailerSteer.angles(s.input(0.2, 0.0, 0.0, 0.2, 0.0)));
    }

    @Test
    void aDockedTrailerCounterSteersBehindTheTurnCentreLikeTheTractorsOwnRearAxle() {
        double radius = 15.0;
        TrailerSteer.Input in = new TrailerSteer.Input(0, -1, 0, 3, Math.PI / 2, 0.3, new double[]{radius, 0.0}, Math.PI / 2,
                new double[]{1.0}, new double[]{-6.0}, LinkKind.DOCK, 30.0);
        double[] a = TrailerSteer.angles(in);
        AxleLayout.WheelSpec spec = new AxleLayout.WheelSpec(1.0, -6.0);
        double expected = GroundSteer.headingDeg(spec, 1.0 / radius, 0.0);
        assertEquals(expected, a[0], 1e-9, "the same Ackermann angle the tractor's own geometry gives");
        assertTrue(a[0] < 0.0, "turning right, a wheel behind the centre points left");
    }

    @Test
    void theWheelAngleNeverPassesTheLock() {
        TrailerSteer.Input in = new TrailerSteer.Input(0, -1, 0, 3, Math.PI / 2, 0.3, new double[]{1.5, 0.0}, Math.PI / 2,
                new double[]{1.0}, new double[]{-6.0}, LinkKind.DOCK, 20.0);
        assertEquals(-20.0, TrailerSteer.angles(in)[0], 1e-9);
    }

    private static final class RopeSim {
        static final double ROPE = 3.0;
        static final double[] THREE_AXLES = {3.0, 7.0, 11.0};
        final double[] axles;
        final double track;
        double px;
        double py;
        double tx;
        double ty;
        double th;

        double[] motion;
        double[] wheelDeg;

        RopeSim(double[] axles, double track, double lateral, double headingErrorDeg, double speed) {
            this.axles = axles;
            this.track = track;
            tx = lateral;
            ty = -Math.sqrt(ROPE * ROPE - lateral * lateral);
            th = Math.PI / 2 + Math.toRadians(headingErrorDeg);
            motion = new double[]{speed * Math.cos(th), speed * Math.sin(th), 0.0};
            wheelDeg = new double[axles.length * 2];
        }

        static RopeSim offTheLine(double speed) {
            return new RopeSim(THREE_AXLES, 4.0, 0.5, -4.0, speed);
        }

        double ropeAngleDeg(double heading) {
            return Math.toDegrees(Math.abs(TrailerSteer.wrapPi(Math.atan2(py - ty, px - tx) - heading)));
        }

        void step(double pvx, double pvy, double parentRate, double[] centre, double tractorHeading, boolean drawbar,
                  double nextX, double nextY) {
            double cx = Math.cos(th);
            double cy = Math.sin(th);
            int n = axles.length * 2;
            double[] wx = new double[n];
            double[] wy = new double[n];
            for (int i = 0; i < n; i++) {
                double side = (i % 2 == 0 ? 1.0 : -1.0) * track / 2.0;
                wx[i] = tx - axles[i / 2] * cx - side * cy;
                wy[i] = ty - axles[i / 2] * cy + side * cx;
            }
            TrailerSteer.Input in = new TrailerSteer.Input(tx, ty, motion[0], motion[1], th, parentRate, centre, tractorHeading,
                    wx, wy, LinkKind.ROPE, 30.0);
            if (drawbar) in = TrailerSteer.drawbar(in, px, py, pvx, pvy);
            double[] a = TrailerSteer.angles(in);
            if (a != null) wheelDeg = a;

            double[][] m = new double[3][3];
            double[] r = new double[3];
            for (int i = 0; i < n; i++) {
                double steps = Math.signum(wheelDeg[i]) * GroundSteer.signalSteps(wheelDeg[i]) * GroundSteer.STEP_DEG;
                double h = th - Math.toRadians(steps);
                double rx = wx[i] - tx;
                double ry = wy[i] - ty;

                accumulate(m, r, new double[]{-Math.sin(h), Math.cos(h), rx * Math.cos(h) + ry * Math.sin(h)}, 0.0, 1.0);
            }
            double ux = px - tx;
            double uy = py - ty;
            double len = Math.hypot(ux, uy);
            accumulate(m, r, new double[]{ux / len, uy / len, 0.0}, (pvx * ux + pvy * uy) / len, 1.0e4);
            for (int k = 0; k < 3; k++) {
                m[k][k] += 1.0e-4;
                r[k] += 1.0e-4 * motion[k];
            }
            motion = solve3(m, r);
            tx += motion[0] * DT;
            ty += motion[1] * DT;
            th += motion[2] * DT;
            px = nextX;
            py = nextY;

            double dx = tx - px;
            double dy = ty - py;
            double d = Math.hypot(dx, dy);
            tx = px + dx * ROPE / d;
            ty = py + dy * ROPE / d;
        }
    }

    private static void accumulate(double[][] m, double[] r, double[] row, double rhs, double weight) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) m[i][j] += weight * row[i] * row[j];
            r[i] += weight * row[i] * rhs;
        }
    }

    private static double[] solve3(double[][] a, double[] b) {
        double[][] m = new double[3][4];
        for (int i = 0; i < 3; i++) {
            System.arraycopy(a[i], 0, m[i], 0, 3);
            m[i][3] = b[i];
        }
        for (int c = 0; c < 3; c++) {
            int pivot = c;
            for (int i = c + 1; i < 3; i++) if (Math.abs(m[i][c]) > Math.abs(m[pivot][c])) pivot = i;
            double[] swap = m[c];
            m[c] = m[pivot];
            m[pivot] = swap;
            for (int i = 0; i < 3; i++) {
                if (i == c) continue;
                double f = m[i][c] / m[c][c];
                for (int j = c; j < 4; j++) m[i][j] -= f * m[c][j];
            }
        }
        return new double[]{m[0][3] / m[0][0], m[1][3] / m[1][1], m[2][3] / m[2][2]};
    }

    private static double driveStraight(RopeSim s, double speed, int ticks, boolean drawbar) {
        double worst = 0.0;
        for (int i = 0; i < ticks; i++) {
            s.step(0.0, speed, 0.0, null, Math.PI / 2, drawbar, s.px, s.py + speed * DT);
            double angle = s.ropeAngleDeg(Math.PI / 2);
            if (!Double.isFinite(angle)) return Double.POSITIVE_INFINITY;
            worst = Math.max(worst, angle);
            if (angle > 60.0) break;
        }
        return worst;
    }

    @Test
    void aRopeTrailerWhoseWheelsFollowItsOwnTowPointDriftsOffTheTractorsLine() {
        RopeSim s = RopeSim.offTheLine(3.0);
        double worst = driveStraight(s, 3.0, 400, false);
        assertTrue(worst > 20.0, "the offset keeps growing: " + worst);
    }

    @Test
    void theDrawbarBringsARopeTrailerBackOntoTheTractorsLine() {
        RopeSim s = RopeSim.offTheLine(3.0);
        driveStraight(s, 3.0, 400, true);

        assertTrue(s.ropeAngleDeg(Math.PI / 2) < 2.0, "rope " + s.ropeAngleDeg(Math.PI / 2));
        assertEquals(s.px, s.tx, 0.15, "on the line");
        assertEquals(90.0, Math.toDegrees(s.th), 2.0, "and square to it");
    }

    @Test
    void backingAPoweredRopeTrailerTheDrawbarStillHoldsItOnTheLine() {
        RopeSim s = RopeSim.offTheLine(-2.0);
        double worst = driveStraight(s, -2.0, 400, true);
        assertTrue(worst < 15.0, "it never ran away: " + worst);
        assertTrue(s.ropeAngleDeg(Math.PI / 2) < 2.0, "rope " + s.ropeAngleDeg(Math.PI / 2));
        assertEquals(s.px, s.tx, 0.15, "on the line");
    }

    @Test
    void roundABendARopeTrailerSettlesInTheTractorsTrack() {
        double radius = 12.0;
        double speed = 3.0;
        double rate = speed / radius;
        double[] c = {-radius, 0.0};
        RopeSim s = new RopeSim(RopeSim.THREE_AXLES, 4.0, 0.0, 0.0, speed);
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (int i = 0; i < 1200; i++) {
            double alpha = rate * i * DT;
            double next = rate * (i + 1) * DT;
            s.step(-speed * Math.sin(alpha), speed * Math.cos(alpha), rate, c, alpha + Math.PI / 2, true,
                    c[0] + radius * Math.cos(next), c[1] + radius * Math.sin(next));
            if (i >= 1100) {
                double angle = s.ropeAngleDeg(next + Math.PI / 2);
                lo = Math.min(lo, angle);
                hi = Math.max(hi, angle);
            }
        }
        assertEquals(radius, Math.hypot(s.tx - c[0], s.ty - c[1]), 0.3, "the tow point runs in the tractor's track");
        assertTrue(hi - lo < 1.0, "and stays there: the rope's angle moved " + (hi - lo) + " degrees");
    }

    @Test
    void aSingleAxleRopeTrailerIsHeldNearTheLine() {
        RopeSim s = new RopeSim(new double[]{3.0}, 2.0, 0.5, -4.0, 3.0);
        double worst = 0.0;
        for (int i = 0; i < 400; i++) {
            s.step(0.0, 3.0, 0.0, null, Math.PI / 2, true, s.px, s.py + 3.0 * DT);
            if (i > 100) worst = Math.max(worst, s.ropeAngleDeg(Math.PI / 2));
        }
        assertTrue(worst < 8.0, "worst after settling " + worst);
    }

    @Test
    void theDrawbarLeavesTheWheelsAloneAtACrawlAndPinsAShortLink() {
        TrailerSteer.Input in = new TrailerSteer.Input(0.0, -3.0, 0.0, 0.1, Math.PI / 2, 0.0, null, Math.PI / 2,
                new double[]{1.0, -1.0}, new double[]{-6.0, -6.0}, LinkKind.ROPE, 30.0);
        assertNull(TrailerSteer.angles(TrailerSteer.drawbar(in, 0.4, 0.0, 0.0, 0.2)), "a crawl steers nothing");
        TrailerSteer.Input pinned = TrailerSteer.drawbar(in, 0.1, -2.8, 0.0, 3.0);
        assertEquals(0.0, pinned.vx(), 1e-12, "a link shorter than half a block is a pin: the parent's velocity");
        assertEquals(3.0, pinned.vy(), 1e-12);
    }

    @Test
    void theTurnCentreComesFromTheMotionWhenThereIsEnoughOfIt() {
        double[] geometric = {10.0, -2.0};

        double[] measured = TrailerSteer.turnCentre(geometric, 0.0, 1.0, 0.0, 4.0, 0.5);
        assertEquals(-8.0, measured[0], 1e-9);
        assertEquals(1.0, measured[1], 1e-9);
        assertEquals(geometric, TrailerSteer.turnCentre(geometric, 0.0, 1.0, 0.0, 0.5, 0.5), "at a crawl: the geometry");
        assertEquals(geometric, TrailerSteer.turnCentre(geometric, 0.0, 1.0, 0.0, 4.0, 0.01), "barely turning: the geometry");
        assertNull(TrailerSteer.turnCentre(null, 0.0, 0.0, 0.0, 4.0, 0.0), "straight on both counts");
        double[] reversing = TrailerSteer.turnCentre(null, 0.0, 0.0, 0.0, -4.0, -0.5);
        assertEquals(-8.0, reversing[0], 1e-9, "backing round the same arc turns about the same centre");
    }
}
