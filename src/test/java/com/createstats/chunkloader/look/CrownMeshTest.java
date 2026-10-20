package com.createstats.chunkloader.look;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownMeshTest {
    private static CrownPose[] poses() {
        final CrownPose turned = CrownPose.settled(25);
        for (int t = 0; t < CrownGeometry.TIERS; t++) {
            turned.tierAngle[t] = 37.0 * t + 11.0;
        }
        final CrownPose growing = CrownPose.settled(16);
        growing.mount[16] = 1.08;
        growing.glow[16] = 0.4;
        growing.ink[16] = CrownPose.INK_ROSE;
        growing.tierUp[4] = 0.4;
        growing.hornTop = 38.0;
        final CrownPose half = CrownPose.settled(25);
        half.yokeSwing = 0.5;
        half.yokeExt = 0.3;
        half.capOpen = 0.5;
        return new CrownPose[] {CrownPose.item(), CrownPose.settled(1), CrownPose.settled(7), CrownPose.settled(13),
                CrownPose.settled(21), turned, growing, half};
    }

    @Test
    @DisplayName("every quad winds counter-clockwise round the normal it carries, so culling keeps the outside")
    void everyQuadFacesItsNormal() {
        for (CrownPose p : poses()) {
            final MeshProbe m = MeshProbe.of(p);
            for (MeshProbe.Quad q : m.quads) {
                final float[] c = q.p();
                final double ax = c[3] - c[0];
                final double ay = c[4] - c[1];
                final double az = c[5] - c[2];
                final double bx = c[9] - c[0];
                final double by = c[10] - c[1];
                final double bz = c[11] - c[2];
                final double nx = ay * bz - az * by;
                final double ny = az * bx - ax * bz;
                final double nz = ax * by - ay * bx;
                final double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                assertTrue(l > 1e-9, "a degenerate quad of " + q.material());
                final double dot = (nx * q.nx() + ny * q.ny() + nz * q.nz()) / l;
                assertTrue(dot > 0.999, "a " + q.material() + " quad winds against its normal: " + dot);
                final double nl = Math.sqrt(q.nx() * q.nx() + q.ny() * q.ny() + q.nz() * q.nz());
                assertEquals(1.0, nl, 1e-4, "a normal that is not unit length");
            }
        }
    }

    @Test
    @DisplayName("every texture coordinate stays on its own 16 texel sprite")
    void everyUvStaysOnItsSprite() {
        for (CrownPose p : poses()) {
            for (MeshProbe.Quad q : MeshProbe.of(p).quads) {
                for (float v : q.uv()) {
                    assertTrue(v >= -1e-4 && v <= 16 + 1e-4, "uv " + v + " on a " + q.material() + " quad");
                }
            }
        }
    }

    @Test
    @DisplayName("a full crown stays inside a landmark's budget, and far off it is lighter still")
    void theQuadBudget() {
        final CrownPose full = CrownPose.settled(25);
        final int near = MeshProbe.of(full, new CrownAnchors(), CrownMesh.LOD_FULL).quads.size();
        final int far = MeshProbe.of(full, new CrownAnchors(), CrownMesh.LOD_FAR).quads.size();
        assertTrue(near <= 3200, "too many quads for one block entity: " + near);
        assertTrue(far < near * 0.8, "far off it should drop the small parts: " + far + " of " + near);
    }

    @Test
    @DisplayName("the pods reported for the effects are the sectors latched out on the rims")
    void theAnchorsAreTheLatchedSectors() {
        for (int n : new int[] {0, 1, 5, 6, 12, 25}) {
            final CrownAnchors a = new CrownAnchors();
            MeshProbe.of(CrownPose.settled(n), a, CrownMesh.LOD_FULL);
            assertEquals(n, a.pods, "pods at " + n);
            for (int i = 0; i < a.pods; i++) {
                final double r = Math.hypot(a.x[i] - 8, a.z[i] - 8);
                assertTrue(r > CrownGeometry.RING_R[a.tier[i]], "a pod inside its rim");
            }
        }
        final CrownAnchors a = new CrownAnchors();
        MeshProbe.of(CrownPose.settled(25), a, CrownMesh.LOD_FULL);
        assertTrue(a.yokeEnd[0] && a.yokeEnd[1], "the locked yoke's two ends");
        assertTrue(a.yokeX[0] > 8 && a.yokeX[1] < 8, "east end east of the heart, west end west of it");
        for (int t = 0; t < CrownGeometry.TIERS; t++) {
            assertTrue(a.knob[t * 2] && a.knob[t * 2 + 1], "a knob beside every ring at 25: " + t);
        }
    }

    @Test
    @DisplayName("building a frame allocates nothing: the crown is drawn every frame from far off")
    void noAllocationPerFrame() {
        final java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean sun) || !sun.isThreadAllocatedMemorySupported()) {
            return;
        }
        final CrownMesh mesh = new CrownMesh();
        final CrownFx fx = new CrownFx();
        final CrownAnchors a = new CrownAnchors();
        final CrownPose p = CrownPose.settled(25);
        p.pulse = 0.5;
        p.flash = 1.0;
        final CrownMesh.Sink quads = (q, uv, nx, ny, nz, m, g) -> {
        };
        final CrownFx.Sink light = new NullFx();
        for (int i = 0; i < 2_000; i++) {
            p.arcSeed = i;
            mesh.build(p, a, quads, CrownMesh.LOD_FULL);
            fx.emit(p, a, light, CrownMesh.LOD_FULL);
        }
        final long id = Thread.currentThread().getId();
        final long before = sun.getThreadAllocatedBytes(id);
        for (int i = 0; i < 1_000; i++) {
            p.arcSeed = i;
            mesh.build(p, a, quads, CrownMesh.LOD_FULL);
            fx.emit(p, a, light, CrownMesh.LOD_FULL);
        }
        final long per = (sun.getThreadAllocatedBytes(id) - before) / 1_000;
        assertTrue(per < 256, "a frame allocated " + per + " bytes");
    }

    static final class NullFx implements CrownFx.Sink {
        @Override
        public void glow(double x, double y, double z, double radius, int rgb, double alpha, double core,
                         double nudge) {
        }

        @Override
        public void stroke(double ax, double ay, double az, double bx, double by, double bz, double core,
                           double sheath, int coreRgb, int sheathRgb, double alpha) {
        }

        @Override
        public void band(double y, double r0, double r1, double a0, double a1, int rgb, double alpha0,
                         double alpha1, boolean floor) {
        }

        @Override
        public void flare(double x, double y, double z, double halfH, double halfV, double width, int coreRgb,
                          int glowRgb, double alpha) {
        }

        @Override
        public void star(double x, double y, double z, double size, double alpha) {
        }

        @Override
        public void front(double x0, double y0, double x1, double y1, double z, int rgb, double alpha) {
        }

        @Override
        public void spark(double x, double y, double z) {
        }
    }
}
