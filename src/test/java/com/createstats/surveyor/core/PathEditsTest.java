package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathEditsTest {
    private static final UUID OWNER = new UUID(1, 1);
    private static final UUID A = new UUID(7, 1);
    private static final UUID B = new UUID(7, 2);
    private static final Stop WAIT_5 = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Wait(5))));

    private static Pathway line(UUID id, double x0, int nodes, int stopAt) {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < nodes; i++) pts.add(new double[]{x0 + 4.0 * i, 64, 0});
        Pathway p = Pathway.create(id, "Line", 3, OWNER).appendAll(pts);
        if (stopAt >= 0) p = p.update(p.nodes().get(stopAt).id(), n -> n.withStop(WAIT_5).withName("Quarry"));
        return p;
    }

    private static Pathway loop() {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = 2 * Math.PI * i / 12;
            pts.add(new double[]{10 * Math.cos(a), 64, 10 * Math.sin(a)});
        }
        return Pathway.create(A, "Loop", 5, OWNER).appendAll(pts).withKind(PathKind.LOOP);
    }

    private static void idsUnique(Pathway p) {
        Set<Integer> ids = new HashSet<>();
        for (PathNode n : p.nodes()) {
            assertTrue(ids.add(n.id()), "node id " + n.id() + " twice in " + p.name());
            assertTrue(n.id() < p.nextNodeId(), "id " + n.id() + " not below nextNodeId " + p.nextNodeId());
        }
    }

    @Test
    void aLineCutInTwoKeepsItsStopOnTheSideAVehicleArrivesFrom() {
        Pathway p = line(A, 0, 9, 4);
        PathEdits.Split s = PathEdits.split(p, p.nodes().get(4).id(), B, "Line 2");
        assertNotNull(s);
        assertEquals(5, s.first().nodes().size());
        assertEquals(A, s.first().id());
        assertTrue(s.first().nodes().get(4).isStop(), "the first line ends at the stop, with its rules");
        assertEquals("Quarry", s.first().nodes().get(4).name());
        Pathway second = s.second();
        assertNotNull(second);
        assertEquals(B, second.id());
        assertEquals("Line 2", second.name());
        assertEquals(5, second.nodes().size(), "from a copy of the cut node to the end");
        assertTrue(!second.nodes().get(0).isStop() && second.nodes().get(0).name().isEmpty(), "the copy is plain");
        assertEquals(p.nodes().get(4).x(), second.nodes().get(0).x(), 1e-12, "the two meet at the cut");
        assertEquals(p.nodes().get(8).x(), second.nodes().get(4).x(), 1e-12);
        assertEquals(PathKind.ONE_WAY, second.kind());
        assertEquals(p.colour(), second.colour());
        idsUnique(s.first());
        idsUnique(second);
        assertTrue(s.first().version() > p.version(), "the first is an edit of the original");

        assertNull(PathEdits.split(p, p.nodes().get(0).id(), B, "x"), "an end has nothing to cut");
        assertNull(PathEdits.split(p, p.nodes().get(8).id(), B, "x"));
        assertNull(PathEdits.split(p, 999, B, "x"));
    }

    @Test
    void aLoopCutOpensIntoOneLineRoundFromTheNode() {
        Pathway p = loop().update(4, n -> n.withStop(WAIT_5));
        PathEdits.Split s = PathEdits.split(p, 4, B, "unused");
        assertNotNull(s);
        assertNull(s.second(), "a loop opens, it does not become two");
        Pathway open = s.first();
        assertEquals(PathKind.ONE_WAY, open.kind());
        assertEquals(13, open.nodes().size(), "all the way round and back to the cut");
        assertEquals(4, open.nodes().get(0).id(), "starting at the node, with its stop");
        assertTrue(open.nodes().get(0).isStop());
        PathNode last = open.nodes().get(12);
        assertEquals(open.nodes().get(0).x(), last.x(), 1e-12);
        assertEquals(open.nodes().get(0).z(), last.z(), 1e-12);
        assertTrue(!last.isStop());
        idsUnique(open);
        double before = PathSpline.of(p).length();
        double after = PathSpline.of(open).length();
        assertEquals(before, after, before * 0.03, "the same road, open at one point");
    }

    @Test
    void twoLinesJoinEndToEndWhicheverEndsAreNamed() {
        Pathway a = line(A, 0, 4, -1);
        Pathway b = line(B, 12.5, 4, 2);
        Pathway ab = PathEdits.join(a, PathEdits.End.LAST, b, PathEdits.End.FIRST);
        assertNotNull(ab);
        assertEquals(A, ab.id(), "a keeps its id");
        assertEquals(7, ab.nodes().size(), "the ends half a block apart become one node");
        assertEquals(24.5, ab.nodes().get(6).x(), 1e-12);
        assertTrue(ab.nodes().get(5).isStop(), "b's stop travels with its node");
        idsUnique(ab);

        Pathway b2 = line(B, -12.0, 3, -1);
        Pathway ba = PathEdits.join(a, PathEdits.End.FIRST, b2, PathEdits.End.LAST);
        assertNotNull(ba);
        assertEquals(7, ba.nodes().size(), "4 b apart: a straight joins them");
        assertEquals(-12.0, ba.nodes().get(0).x(), 1e-12);
        assertEquals(0.0, ba.nodes().get(3).x(), 1e-12, "then a, unchanged");
        idsUnique(ba);

        Pathway b3 = line(B, 16.0, 3, -1);
        Pathway turned = PathEdits.join(a, PathEdits.End.LAST, b3, PathEdits.End.FIRST);
        assertNotNull(turned);
        Pathway b4 = line(B, 16.0, 3, -1).reversed();
        Pathway turned2 = PathEdits.join(a, PathEdits.End.LAST, b4, PathEdits.End.LAST);
        assertNotNull(turned2);
        assertEquals(16.0, turned2.nodes().get(4).x(), 1e-12, "b reversed so its near end comes first");
        assertEquals(24.0, turned2.nodes().get(6).x(), 1e-12);

        assertNull(PathEdits.join(a, PathEdits.End.LAST, line(B, 40, 3, -1), PathEdits.End.FIRST), "too far apart");
        assertNull(PathEdits.join(a, PathEdits.End.LAST, a, PathEdits.End.FIRST), "a line is not joined to itself");
        assertNull(PathEdits.join(loop(), PathEdits.End.LAST, b, PathEdits.End.FIRST), "a loop has no ends");
    }

    @Test
    void aDuplicateIsANewLineBesideTheOld() {
        Pathway p = line(A, 0, 5, 2);
        UUID me = new UUID(3, 3);
        Pathway d = PathEdits.duplicate(p, B, "Line copy", me, 9, 3, 3);
        assertEquals(B, d.id());
        assertEquals(me, d.owner());
        assertEquals(1L, d.version());
        assertTrue(!d.shared());
        assertEquals(9, d.colour());
        for (int i = 0; i < 5; i++) {
            assertEquals(p.nodes().get(i).x() + 3, d.nodes().get(i).x(), 1e-12);
            assertEquals(p.nodes().get(i).z() + 3, d.nodes().get(i).z(), 1e-12);
            assertEquals(p.nodes().get(i).y(), d.nodes().get(i).y(), 1e-12);
        }
        assertTrue(d.nodes().get(2).isStop(), "stops come with it");
        idsUnique(d);
    }

    @Test
    void aStretchOrAWholeLineMoves() {
        Pathway p = line(A, 0, 6, -1);
        Pathway whole = PathEdits.translate(p, 0, 5, 2, 1, -3);
        for (int i = 0; i < 6; i++) {
            assertEquals(p.nodes().get(i).x() + 2, whole.nodes().get(i).x(), 1e-12);
            assertEquals(p.nodes().get(i).y() + 1, whole.nodes().get(i).y(), 1e-12);
        }
        Pathway stretch = PathEdits.translate(p, 2, 3, 0, 0, 5);
        assertEquals(0.0, stretch.nodes().get(1).z(), 0.0);
        assertEquals(5.0, stretch.nodes().get(2).z(), 0.0);
        assertEquals(5.0, stretch.nodes().get(3).z(), 0.0);
        assertEquals(0.0, stretch.nodes().get(4).z(), 0.0);

        Pathway l = loop();
        Pathway seam = PathEdits.translate(l, 11, 1, 0, 2, 0);
        assertEquals(66.0, seam.nodes().get(11).y(), 1e-12);
        assertEquals(66.0, seam.nodes().get(0).y(), 1e-12);
        assertEquals(66.0, seam.nodes().get(1).y(), 1e-12);
        assertEquals(64.0, seam.nodes().get(2).y(), 1e-12);
    }

    @Test
    void theBrushIronsOutWobbleAndLeavesAnchorsAndTheRestAlone() {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < 21; i++) pts.add(new double[]{2.0 * i, 64, (i % 2 == 0 ? 0.4 : -0.4)});
        Pathway p = Pathway.create(A, "Wobbly", 1, OWNER).appendAll(pts);
        int anchor = p.nodes().get(10).id();
        p = p.update(anchor, n -> n.withName("Middle"));
        List<double[]> stroke = List.of(new double[]{16, 64, 0}, new double[]{24, 64, 0});
        Pathway s = PathEdits.smooth(p, stroke, PathEdits.BRUSH_RADIUS);

        double before = 0, after = 0;
        for (int i = 8; i <= 12; i++) {
            before += Math.abs(p.nodes().get(i - 1).z() - 2 * p.nodes().get(i).z() + p.nodes().get(i + 1).z());
            after += Math.abs(s.nodes().get(i - 1).z() - 2 * s.nodes().get(i).z() + s.nodes().get(i + 1).z());
        }
        assertTrue(after < before * 0.5, "the wobble under the brush at least halves: " + before + " to " + after);
        assertEquals(p.node(anchor).z(), s.node(anchor).z(), 0.0, "an anchor never moves");
        for (int i : new int[]{0, 1, 2, 3, 17, 18, 19, 20}) {
            assertEquals(p.nodes().get(i).z(), s.nodes().get(i).z(), 0.0, "node " + i + " is beyond the brush");
        }
        assertSame(p, PathEdits.smooth(p, List.of(new double[]{0, 64, 50}), PathEdits.BRUSH_RADIUS), "nothing under the brush");
        idsUnique(s);
    }

    @Test
    void aJunctionIsSnappedOntoTheNearestNodeOfAnotherLine() {
        Pathway a = line(A, 0, 5, -1);
        Pathway b = Pathway.create(B, "Cross", 2, OWNER).appendAll(List.of(
                new double[]{9, 64, -8}, new double[]{9.5, 64, 2.5}, new double[]{10, 64, 12}));
        PathEdits.Meeting m = PathEdits.nearestMeeting(a, a.nodes().get(2).id(), List.of(a, b));
        assertNotNull(m);
        assertEquals(B, m.pathway());
        assertEquals(9.5, m.x(), 1e-12);
        assertNull(PathEdits.nearestMeeting(a, a.nodes().get(0).id(), List.of(a, b)), "nothing within reach of x 0");
    }
}
