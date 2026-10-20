package com.createstats.powertrack.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailMesherTest {
    private static final double BASE = 64.0;

    private static RailLayout straight(List<SectionGraph.Cut> cuts) {
        return RailLayout.build(List.of(new RailLayout.EdgeIn(1, 2, 10, true,
                new double[]{0.5, BASE, 0.5, 10.5, BASE, 0.5})), cuts);
    }

    private static List<RailMesher.Quad> mesh(RailLayout l, List<RailMesher.Tap> taps) {
        final RailLayout.Shape s = l.shapes().get(0);
        return RailMesher.meshEdge(s, l.sections().pieces(s.lo(), s.hi()), taps, new double[]{0, 0, 0});
    }

    private static boolean uses(List<RailMesher.Quad> quads, float[] uv) {
        return quads.stream().anyMatch(q -> q.uv() == uv);
    }

    private static double maxY(List<RailMesher.Quad> quads, double x0, double x1) {
        double best = -1e9;
        for (RailMesher.Quad q : quads) {
            for (int i = 0; i < 4; i++) {
                final double x = q.xyz()[3 * i];
                if (x >= x0 && x <= x1) {
                    best = Math.max(best, q.xyz()[3 * i + 1]);
                }
            }
        }
        return best;
    }

    @Test
    @DisplayName("the cap stands at its profile height on the run and its ramp's toe three pixels lower")
    void heights() {
        final List<RailMesher.Quad> quads = mesh(straight(List.of()), List.of());
        assertEquals(BASE + RailProfile.CAP_TOP / 16, maxY(quads, 5.0, 6.0), 1e-4);

        assertEquals(BASE + (RailProfile.CAP_TOP - RailProfile.RAMP_DROP) / 16, maxY(quads, 0.99, 1.01), 1e-3);
    }

    @Test
    @DisplayName("a chair on every other sleeper along the run, none under a ramp")
    void chairs() {
        final List<RailMesher.Quad> quads = mesh(straight(List.of()), List.of());
        final long chairTops = quads.stream().filter(q -> q.uv() == RailMesher.UV_PORCELAIN && q.ny() > 0.9).count();

        assertTrue(chairTops >= 7 && chairTops <= 9, "chairs: " + chairTops);
    }

    @Test
    @DisplayName("each kind of end looks like what it does: plain, insulated, jumpered")
    void ends() {
        final List<RailMesher.Quad> plain = mesh(straight(List.of()), List.of());
        assertFalse(uses(plain, RailMesher.UV_WHITE));
        assertFalse(uses(plain, RailMesher.UV_CABLE));
        assertTrue(uses(plain, RailMesher.UV_END));
        final List<RailMesher.Quad> insulated = mesh(straight(List.of(new SectionGraph.Cut(1, 2, 5))), List.of());
        assertTrue(uses(insulated, RailMesher.UV_WHITE));
        assertFalse(uses(insulated, RailMesher.UV_CABLE));

        final RailLayout junction = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 10, true, new double[]{0.5, BASE, 0.5, 10.5, BASE, 0.5}),
                new RailLayout.EdgeIn(2, 3, 10, false, new double[]{10.5, BASE, 0.5, 20.5, BASE, 0.5}),
                new RailLayout.EdgeIn(2, 4, 10, false, new double[]{10.5, BASE, 0.5, 10.5, BASE, 10.5})), List.of());
        final List<RailMesher.Quad> jumpered = mesh(junction, List.of());
        assertTrue(uses(jumpered, RailMesher.UV_CABLE));
        assertTrue(uses(jumpered, RailMesher.UV_POT));
    }

    @Test
    @DisplayName("a feeder's tap is a lug on the rail and a cable down to a pot on the feeder's side")
    void tap() {
        final RailLayout l = straight(List.of());
        final List<RailMesher.Quad> quads = mesh(l, List.of(new RailMesher.Tap(1, 2, 5.0, 1)));
        assertTrue(uses(quads, RailMesher.UV_LUG));
        assertTrue(uses(quads, RailMesher.UV_POT));

        final List<RailMesher.Quad> gap = mesh(l, List.of(new RailMesher.Tap(1, 2, 0.2, 1)));
        assertFalse(uses(gap, RailMesher.UV_LUG));
    }

    @Test
    @DisplayName("cable pots stand in the cribs between Create's sleepers, and nothing else dips below their tops")
    void cribsAndClearance() {
        final RailLayout junction = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 10, true, new double[]{0.5, BASE, 0.5, 10.5, BASE, 0.5}),
                new RailLayout.EdgeIn(2, 3, 10, false, new double[]{10.5, BASE, 0.5, 20.5, BASE, 0.5}),
                new RailLayout.EdgeIn(2, 4, 10, false, new double[]{10.5, BASE, 0.5, 10.5, BASE, 10.5})), List.of());
        final List<RailMesher.Quad> quads = mesh(junction, List.of(new RailMesher.Tap(1, 2, 5.3, 1)));
        final double sleeperTop = BASE + RailProfile.SLEEPER_TOP / 16;
        int pots = 0;
        for (RailMesher.Quad q : quads) {
            final float[] p = q.xyz();
            double minY = 1e9, sx = 0;
            for (int i = 0; i < 4; i++) {
                minY = Math.min(minY, p[3 * i + 1]);
                sx += p[3 * i];
            }
            if (q.uv() == RailMesher.UV_POT) {
                if (q.ny() > 0.9) {
                    pots++;

                    final double alongPx = sx / 4 * 16.0;
                    assertEquals(0.0, Math.abs(alongPx - 8.0 * Math.round(alongPx / 8.0)), 1e-3, "pot off its crib");
                }
                continue;
            }
            assertTrue(minY >= sleeperTop + 0.1 / 16 - 1e-6 || q.uv() == RailMesher.UV_END || q.uv() == RailMesher.UV_SIDE
                            || q.uv() == RailMesher.UV_TOP || q.uv() == RailMesher.UV_CAP_SIDE || q.uv() == RailMesher.UV_PORCELAIN,
                    "a part beside the rail dips into the sleepers: " + q.uv()[0] + "," + q.uv()[1] + " at " + minY);
        }
        assertEquals(2, pots, "one jumper pot and one tap pot");
    }

    @Test
    @DisplayName("on a curve every chair stands on one of Create's ties")
    void chairsOnTies() {
        final double r = 7.5;
        final int n = 41;
        final double[] arc = new double[3 * n];
        for (int i = 0; i < n; i++) {
            final double a = (Math.PI / 2) * i / (n - 1);
            arc[3 * i] = r * Math.sin(a);
            arc[3 * i + 1] = BASE;
            arc[3 * i + 2] = r - r * Math.cos(a);
        }
        final double length = Math.PI / 2 * r;
        final RailLayout l = RailLayout.build(List.of(new RailLayout.EdgeIn(1, 2, length, true, arc)), List.of());
        final RailLayout.Shape s = l.shapes().get(0);
        final int segments = (int) (length * 2);
        final double step = length * 16 / segments;
        final List<Double> chairs = RailMesher.chairPositions(s, 0.5, length - 0.5);
        assertTrue(chairs.size() >= segments / 2 - 2, "a curve this long carries " + chairs.size() + " chairs");
        for (double chair : chairs) {
            final double into = (chair * 16) % step;
            assertTrue(into > 2 - 1e-6 && into < 6 + 1e-6, "a chair " + into + " px into its segment misses the tie");
        }
    }

    @Test
    @DisplayName("no face lies flush on a sleeper, and every quad faces the way its normal says")
    void noFlushFacesAndWinding() {
        final List<RailMesher.Quad> quads = mesh(straight(List.of(new SectionGraph.Cut(1, 2, 5))), List.of());
        final double sleeper = BASE + RailProfile.SLEEPER_TOP / 16;
        for (RailMesher.Quad q : quads) {
            final float[] p = q.xyz();
            boolean flat = true;
            for (int i = 0; i < 4; i++) {
                flat &= Math.abs(p[3 * i + 1] - sleeper) < 1e-4;
            }
            assertFalse(flat && q.ny() < -0.9, "a downward face sits on the sleepers' top");
            final double ax = p[3] - p[0], ay = p[4] - p[1], az = p[5] - p[2];
            final double bx = p[6] - p[0], by = p[7] - p[1], bz = p[8] - p[2];
            final double cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
            final double dot = cx * q.nx() + cy * q.ny() + cz * q.nz();
            final double m = Math.sqrt(cx * cx + cy * cy + cz * cz);
            assertTrue(m < 1e-9 || dot > 0, "a quad wound against its normal");
        }
    }
}
