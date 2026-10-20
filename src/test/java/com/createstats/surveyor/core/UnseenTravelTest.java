package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnseenTravelTest {
    private static PathSpline loop100() {
        return PathSpline.of(Pathway.create(UUID.randomUUID(), "t", 0, new UUID(0, 1))
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{25, 64, 0}, new double[]{25, 64, 25}, new double[]{0, 64, 25}))
                .withKind(PathKind.LOOP));
    }

    private static PathSpline line40() {
        return PathSpline.of(Pathway.create(UUID.randomUUID(), "t", 0, new UUID(0, 1))
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{40, 64, 0})));
    }

    @Test
    void aTurnMovesHeadingsTheWayThePathwaysCountThem() {
        RigidMove m = new RigidMove(0, 0, 0, Math.PI / 2, 0, 0, 0);
        double[] nose = m.point(0, 0, -1);
        assertEquals(1.0, nose[0], 1e-12);
        assertEquals(0.0, nose[2], 1e-12);
        assertEquals(PathSpline.heading(nose[0], nose[2]), m.heading(0.0), 1e-12);
    }

    @Test
    void theOrientationTurnsWithThePoints() {
        RigidMove m = RigidMove.between(3, 64, -7, 0.4, 120, 70, 55, -2.1);
        double[] q0 = {0.05, 0.31, -0.02, 0.949};
        double n = Math.sqrt(q0[0] * q0[0] + q0[1] * q0[1] + q0[2] * q0[2] + q0[3] * q0[3]);
        for (int i = 0; i < 4; i++) q0[i] /= n;
        double[] pos0 = {5, 65, -4};
        double[] local = {1.5, 0.25, -2.0};
        double[] world0 = add(pos0, rotate(q0, local));
        double[] moved = m.point(world0[0], world0[1], world0[2]);
        double[] pos1 = m.point(pos0[0], pos0[1], pos0[2]);
        double[] q1 = m.orientation(q0[0], q0[1], q0[2], q0[3]);
        double[] world1 = add(pos1, rotate(q1, local));
        assertArrayEquals(moved, world1, 1e-9);
    }

    @Test
    void relativePosesSurviveTheMove() {
        RigidMove m = RigidMove.between(10, 64, 10, 1.0, -300, 80, 900, 2.9);
        double[] a = m.point(10, 64, 10);
        double[] b = m.point(14.2, 64.3, 14.3);
        assertEquals(dist(new double[]{10, 64, 10}, new double[]{14.2, 64.3, 14.3}), dist(a, b), 1e-9);
        assertArrayEquals(new double[]{-300, 80, 900}, a, 1e-9);
    }

    @Test
    void theBoxHoldsEveryMovedCorner() {
        RigidMove m = RigidMove.between(0, 0, 0, 0, 50, 3, 50, Math.PI / 4);
        double[] box = m.box(-1, 0, -2, 1, 2, 2);
        for (int i = 0; i < 8; i++) {
            double[] p = m.point((i & 1) == 0 ? -1 : 1, (i & 2) == 0 ? 0 : 2, (i & 4) == 0 ? -2 : 2);
            for (int a = 0; a < 3; a++) {
                assertTrue(p[a] >= box[a] - 1e-9 && p[a] <= box[a + 3] + 1e-9);
            }
        }
    }

    @Test
    void aLoopWrapsAndAOneWayLineEnds() {
        PathSpline loop = loop100();
        double s = UnseenTravel.advance(loop, loop.length() - 1.0, 6.0, 1.0);
        assertEquals(5.0, s, 1e-9);
        assertFalse(UnseenTravel.atEnd(loop, s));
        PathSpline line = line40();
        double e = UnseenTravel.advance(line, 38.0, 6.0, 1.0);
        assertEquals(line.length(), e, 1e-9);
        assertTrue(UnseenTravel.atEnd(line, e));
    }

    @Test
    void standingStillGoesNowhere() {
        PathSpline loop = loop100();
        assertEquals(12.0, UnseenTravel.advance(loop, 12.0, 0.0, 1.0), 1e-12);
        assertEquals(12.0, UnseenTravel.advance(loop, 12.0, Double.NaN, 1.0), 1e-12);
    }

    @Test
    void comingBackStandsOnTheLineFacingAlongIt() {
        PathSpline line = line40();
        UnseenTravel.Place at = UnseenTravel.place(line, 20.0);
        assertEquals(20.0, at.x(), 1e-6);
        assertEquals(Math.PI / 2, at.heading(), 1e-6);

        RigidMove m = UnseenTravel.move(5.0, 65.1, 0.2, Math.PI / 2 - 0.1, at, 64.0, 1.1, 1.1);
        double[] ref = m.point(5.0, 65.1, 0.2);
        assertArrayEquals(new double[]{20.0, 65.1 + UnseenTravel.LIFT, 0.0}, ref, 1e-9);
        assertEquals(Math.PI / 2, m.heading(Math.PI / 2 - 0.1), 1e-9);

        RigidMove n = UnseenTravel.move(5.0, 65.1, 0.2, Math.PI / 2, at, Double.NaN, 1.1, 1.1);
        assertEquals(64.0 + 1.1 + UnseenTravel.LIFT, n.point(5.0, 65.1, 0.2)[1], 1e-9);
    }

    @Test
    void aLoadOnTheDeckRidesAndANeighbourDoesNot() {
        double[] trailer = {171.8, 61.4, -269.6, 195.3, 80.8, -249.5};
        double[] netherite = {182.8, 68.1, -261.0, 188.7, 72.8, -256.1};
        assertTrue(UnseenTravel.riding(netherite, trailer));

        double[] neighbour = {196.0, 61.4, -260.0, 205.0, 66.0, -250.0};
        assertFalse(UnseenTravel.riding(neighbour, trailer));

        assertFalse(UnseenTravel.riding(trailer, netherite));
    }

    @Test
    void theSettleNeedsAnUnbrokenRun() {
        UnseenTravel.Settle s = new UnseenTravel.Settle(3);
        assertFalse(s.feed(true));
        assertFalse(s.feed(true));
        assertFalse(s.feed(false));
        assertFalse(s.feed(true));
        assertFalse(s.feed(true));
        assertTrue(s.feed(true));
        assertTrue(s.feed(true));
    }

    @Test
    void theChunkSpanReachesTheMarginOnEverySide() {
        int[] span = UnseenTravel.chunkSpan(new double[]{-1, 60, -1, 1, 70, 1}, 16.0);
        assertArrayEquals(new int[]{-2, -2, 1, 1}, span);
    }

    private static double[] rotate(double[] q, double[] v) {
        double x = q[0], y = q[1], z = q[2], w = q[3];
        double ix = w * v[0] + y * v[2] - z * v[1];
        double iy = w * v[1] + z * v[0] - x * v[2];
        double iz = w * v[2] + x * v[1] - y * v[0];
        double iw = -x * v[0] - y * v[1] - z * v[2];
        return new double[]{ix * w + iw * -x + iy * -z - iz * -y, iy * w + iw * -y + iz * -x - ix * -z,
                iz * w + iw * -z + ix * -y - iy * -x};
    }

    private static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
