package com.createstats.seismic.archive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanCameraTest {
    private static double[] at(ScanCamera c, double x, double y, double z) {
        final double[] p = new double[3];
        c.project(x, y, z, p);
        return p;
    }

    @Test
    @DisplayName("a plan facing north puts north at the top and east on the right, and higher ground nearer")
    void plan() {
        final ScanCamera c = new ScanCamera(0.0, ScanCamera.PLAN, 2.0, 0.0, 0.0, 0.0, 100.0, 100.0);
        assertEquals(100.0, at(c, 0, 0, -10)[0], 1e-9);
        assertEquals(80.0, at(c, 0, 0, -10)[1], 1e-9, "north is up");
        assertEquals(120.0, at(c, 10, 0, 0)[0], 1e-9, "east is right");
        assertEquals(100.0, at(c, 0, 30, 0)[1], 1e-9, "height does not move a point on a plan");
        assertTrue(at(c, 0, 30, 0)[2] < at(c, 0, -30, 0)[2], "the higher block is nearer the viewer");
    }

    @Test
    @DisplayName("facing east, east is up the page and north is to the left")
    void yawTurnsTheMap() {
        final ScanCamera c = new ScanCamera(90.0, ScanCamera.PLAN, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        assertEquals(-10.0, at(c, 10, 0, 0)[1], 1e-9);
        assertEquals(-10.0, at(c, 0, 0, -10)[0], 1e-9);
    }

    @Test
    @DisplayName("a section shows height as height and depth as distance along the view")
    void section() {
        final ScanCamera c = new ScanCamera(0.0, ScanCamera.SECTION, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        assertEquals(-20.0, at(c, 0, 20, 0)[1], 1e-9, "up is up");
        assertEquals(10.0, at(c, 0, 0, -10)[2], 1e-9, "north is away when facing north");
        assertEquals(0.0, at(c, 0, 0, -10)[1], 1e-9, "and does not move on the page");
    }

    @Test
    @DisplayName("an oblique view tips the far side up and stays a parallel projection")
    void oblique() {
        final ScanCamera c = new ScanCamera(30.0, 35.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        final double[] a = at(c, 0, 0, 0);
        final double[] b = at(c, 10, 0, 0);
        final double[] d = at(c, 10, -20, 0);
        final double[] e = at(c, 0, -20, 0);
        assertEquals(b[0] - a[0], d[0] - e[0], 1e-9, "parallel lines stay parallel");
        assertEquals(b[1] - a[1], d[1] - e[1], 1e-9);
        assertTrue(at(c, 0, -20, 0)[1] > a[1], "deeper ground is lower on the page");
    }

    @Test
    @DisplayName("a fitted box lands inside its rect at every bearing and tilt")
    void fit() {
        for (double yaw = 0; yaw < 360; yaw += 22.5) {
            for (double pitch = 0; pitch <= 90; pitch += 15) {
                final ScanCamera c = ScanCamera.fit(yaw, pitch, 94, 5, -423, 222, 69, -295, 10, 20, 300, 180, 0.05, 1.0);
                for (int i = 0; i < 8; i++) {
                    final double[] p = at(c, (i & 1) == 0 ? 94 : 222, (i & 2) == 0 ? 5 : 69, (i & 4) == 0 ? -423 : -295);
                    assertTrue(p[0] >= 10 + 9 - 1e-6 && p[0] <= 310 - 9 + 1e-6, "x " + p[0] + " at " + yaw + "/" + pitch);
                    assertTrue(p[1] >= 20 + 9 - 1e-6 && p[1] <= 200 - 9 + 1e-6, "y " + p[1] + " at " + yaw + "/" + pitch);
                }
            }
        }
    }

    @Test
    @DisplayName("a drag wraps the bearing and keeps the tilt between a section and a plan")
    void orbit() {
        final ScanCamera c = new ScanCamera(350.0, 80.0, 1.0, 0, 0, 0, 0, 0).orbit(20.0, 30.0);
        assertEquals(10.0, c.yaw, 1e-9);
        assertEquals(90.0, c.pitch, 1e-9);
        assertEquals(0.0, c.orbit(0.0, -200.0).pitch, 1e-9);
    }

    @Test
    @DisplayName("the compass needle on the page points where north is")
    void north() {
        final ScanCamera plan = new ScanCamera(0.0, 90.0, 1.0, 0, 0, 0, 0, 0);
        final double[] n = plan.groundDirection(0.0);
        assertEquals(0.0, n[0], 1e-9);
        assertEquals(-1.0, n[1], 1e-9);
        final ScanCamera turned = new ScanCamera(90.0, 90.0, 1.0, 0, 0, 0, 0, 0);
        final double[] m = turned.groundDirection(0.0);
        assertEquals(-1.0, m[0], 1e-9, "facing east, north is to the left");
    }

    @Test
    @DisplayName("a screen point turned back into the world lands where it was drawn, at every bearing and height")
    void unprojectIsTheInverseOnTheViewPlane() {
        for (final double yaw : new double[] {0.0, 32.0, 90.0, 215.0, 359.0}) {
            for (final double pitch : new double[] {0.0, 18.0, 30.0, 61.0, 90.0}) {
                final ScanCamera c = new ScanCamera(yaw, pitch, 2.7, 160.5, -12.0, -358.0, 300.0, 200.0);
                for (final double[] sp : new double[][] {{300.0, 200.0}, {12.0, 380.0}, {577.0, 4.0}}) {
                    final double[] w = c.unproject(sp[0], sp[1]);
                    final double[] back = at(c, w[0], w[1], w[2]);
                    assertEquals(sp[0], back[0], 1e-6, "x at yaw " + yaw + ", pitch " + pitch);
                    assertEquals(sp[1], back[1], 1e-6, "y at yaw " + yaw + ", pitch " + pitch);
                    assertEquals(0.0, back[2], 1e-6, "on the plane through the centre");
                }
            }
        }
    }

    @Test
    @DisplayName("zooming toward the pointer keeps the ground under the pointer (the Survey page's wheel)")
    void zoomKeepsThePointUnderThePointer() {
        final ScanCamera before = new ScanCamera(32.0, 30.0, 2.0, 170.0, 10.0, -360.0, 288.0, 190.0);
        final double mx = 120.0;
        final double my = 260.0;
        final double[] w = before.unproject(mx, my);

        final ScanCamera at = new ScanCamera(before.yaw, before.pitch, before.scale * 1.18, w[0], w[1], w[2], mx, my);
        final double[] centre = at.unproject(288.0, 190.0);
        final ScanCamera after = new ScanCamera(before.yaw, before.pitch, before.scale * 1.18, centre[0], centre[1],
                centre[2], 288.0, 190.0);
        final double[] p = at(after, w[0], w[1], w[2]);
        assertEquals(mx, p[0], 1e-6);
        assertEquals(my, p[1], 1e-6);
    }
}
