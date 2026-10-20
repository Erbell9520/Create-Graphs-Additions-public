package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathSplineTest {
    private static Pathway line(double[]... pts) {
        return Pathway.create(UUID.randomUUID(), "t", 0, new UUID(0, 1)).appendAll(List.of(pts));
    }

    @Test
    void passesThroughEveryNode() {
        Pathway p = line(new double[]{0, 64, 0}, new double[]{10, 64, 3}, new double[]{20, 65, -4}, new double[]{31, 64, 2});
        PathSpline sp = PathSpline.of(p);
        for (int k = 0; k < p.nodes().size(); k++) {
            double s = sp.nodeS(k);
            PathNode n = p.nodes().get(k);
            assertEquals(n.x(), sp.x(s), 0.05, "x at node " + k);
            assertEquals(n.z(), sp.z(s), 0.05, "z at node " + k);
            assertEquals(n.y(), sp.y(s), 0.05, "y at node " + k);
        }
    }

    @Test
    void samplesAreEvenlySpacedAndProgressIsDistance() {
        Pathway p = line(new double[]{0, 64, 0}, new double[]{40, 64, 0});
        PathSpline sp = PathSpline.of(p);
        assertEquals(40.0, sp.length(), 1.0e-6);
        assertEquals(PathSpline.STEP, sp.step(), 0.01);
        for (int i = 1; i < sp.sampleCount(); i++) {
            double d = Math.hypot(sp.sampleX(i) - sp.sampleX(i - 1), sp.sampleZ(i) - sp.sampleZ(i - 1));
            assertEquals(sp.step(), d, 1.0e-6, "equal steps along a straight");
        }
        assertEquals(17.0, sp.x(17.0), 1.0e-9);
    }

    @Test
    void unevenSpacingMakesNoLoopsOrCusps() {
        Pathway p = line(new double[]{0, 64, 0}, new double[]{50, 64, 0}, new double[]{52, 64, 1}, new double[]{52, 64, 40});
        PathSpline sp = PathSpline.of(p);
        double turned = 0.0;
        for (int i = 1; i < sp.sampleCount(); i++) {
            double a = PathSpline.heading(sp.tangentX(i - 1), sp.tangentZ(i - 1));
            double b = PathSpline.heading(sp.tangentX(i), sp.tangentZ(i));
            turned += Math.abs(PathSpline.wrapAngle(b - a));
        }
        assertTrue(turned < Math.toRadians(135), "no self-loop: total turning " + Math.toDegrees(turned));
    }

    @Test
    void aLoopWrapsAndItsNodeOneFollowsItsLast() {
        Pathway p = line(new double[]{0, 64, 0}, new double[]{20, 64, 0}, new double[]{20, 64, 20}, new double[]{0, 64, 20})
                .withKind(PathKind.LOOP);
        PathSpline sp = PathSpline.of(p);
        assertTrue(sp.closed());
        assertEquals(sp.x(0.0), sp.x(sp.length()), 1.0e-6, "wraps");
        assertEquals(sp.x(3.0), sp.x(3.0 + sp.length()), 1.0e-6);
        assertEquals(2.0, sp.ahead(sp.length() - 1.0, 1.0), 1.0e-9, "ahead across the seam");
        assertEquals(-2.0, sp.signedDelta(1.0, sp.length() - 1.0), 1.0e-9, "just behind across the seam");

        double a = PathSpline.heading(sp.tangentX(sp.sampleCount() - 1), sp.tangentZ(sp.sampleCount() - 1));
        double b = PathSpline.heading(sp.tangentX(0), sp.tangentZ(0));
        assertTrue(Math.abs(PathSpline.wrapAngle(b - a)) < Math.toRadians(10), "smooth seam");
    }

    @Test
    void curvatureIsPositiveTurningRightAndMatchesACircle() {
        double r = 10;
        double[][] pts = new double[24][];
        for (int i = 0; i < 24; i++) {
            double phi = 2 * Math.PI * i / 24;
            pts[i] = new double[]{r * Math.sin(phi), 64, -r * Math.cos(phi)};
        }
        PathSpline sp = PathSpline.of(line(pts).withKind(PathKind.LOOP));

        for (int i = 0; i < sp.sampleCount(); i += 17) {
            assertEquals(1.0 / r, sp.curvature(i), 0.01, "curvature at " + i);
        }
    }

    @Test
    void sharpNodesMakeCornersAndWideNodesWidenTheBend() {
        Pathway base = line(new double[]{0, 64, 0}, new double[]{20, 64, 0}, new double[]{20, 64, 20});
        int corner = base.nodes().get(1).id();
        double smooth = maxCurvature(PathSpline.of(base));
        double sharp = maxCurvature(PathSpline.of(base.update(corner, n -> n.withCorner(Corner.SHARP))));
        double wide = maxCurvature(PathSpline.of(base.update(corner, n -> n.withCorner(Corner.WIDE))));
        assertTrue(sharp > smooth * 2, "sharp is a corner: " + sharp + " vs " + smooth);
        assertTrue(wide < smooth, "wide opens the bend: " + wide + " vs " + smooth);
    }

    private static double maxCurvature(PathSpline sp) {
        double m = 0;
        for (int i = 0; i < sp.sampleCount(); i++) m = Math.max(m, Math.abs(sp.curvature(i)));
        return m;
    }

    @Test
    void segmentAtFindsTheNodeAStretchLeavesFrom() {
        Pathway p = line(new double[]{0, 64, 0}, new double[]{10, 64, 0}, new double[]{20, 64, 0});
        PathSpline sp = PathSpline.of(p);
        assertEquals(0, sp.segmentAt(5.0));
        assertEquals(1, sp.segmentAt(15.0));
        assertEquals(2, sp.segmentAt(20.0));
    }

    @Test
    void emptyAndSingleNodeLinesAreSafe() {
        assertEquals(0.0, PathSpline.of(line()).length());
        PathSpline one = PathSpline.of(line(new double[]{3, 64, 4}));
        assertEquals(0.0, one.length());
        assertEquals(3.0, one.x(10.0), 1.0e-9);
    }

    @Test
    void theClientAndTheServerBuildTheSameSamples() {
        Pathway p = line(new double[]{0, 64, 0}, new double[]{13, 66, 7}, new double[]{-4, 63, 22}, new double[]{-19, 64, 3}).withKind(PathKind.LOOP);
        PathSpline a = PathSpline.of(p);
        PathSpline b = PathSpline.of(p);
        assertEquals(a.sampleCount(), b.sampleCount());
        for (int i = 0; i < a.sampleCount(); i++) {
            assertEquals(Double.doubleToLongBits(a.sampleX(i)), Double.doubleToLongBits(b.sampleX(i)));
            assertEquals(Double.doubleToLongBits(a.sampleZ(i)), Double.doubleToLongBits(b.sampleZ(i)));
        }
    }
}
