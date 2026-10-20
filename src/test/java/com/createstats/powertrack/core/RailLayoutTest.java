package com.createstats.powertrack.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailLayoutTest {
    private static final double D = 1e-6;

    private static final double SHOE = 1.5;
    private static final double REACH = CollectorPose.CONTACT_HALF_WIDTH_PX / 16.0;

    private static double[] line(double x0, double y, double z0, double x1, double z1) {
        return new double[]{x0, y, z0, x1, y, z1};
    }

    private static double[] arc(double cx, double y, double cz, double r, double a0, double a1, int n) {
        final double[] out = new double[3 * n];
        for (int i = 0; i < n; i++) {
            final double a = a0 + (a1 - a0) * i / (n - 1);
            out[3 * i] = cx + r * Math.cos(a);
            out[3 * i + 1] = y;
            out[3 * i + 2] = cz + r * Math.sin(a);
        }
        return out;
    }

    @Test
    @DisplayName("along a straight, a shoe touches exactly where the section graph says the rail is")
    void contactIsTheSectionGraphsRail() {
        final RailLayout l = RailLayout.build(List.of(new RailLayout.EdgeIn(1, 2, 20, true, line(0.5, 64, 0.5, 20.5, 0.5))),
                List.of(new SectionGraph.Cut(1, 2, 8)));
        int checked = 0;
        for (double s = 0; s <= 20; s += 1 / 16.0) {
            final RailLayout.Hit hit = l.nearest(0.5 + s, 64 + SHOE, 0.5, REACH, 1.0, 2.0);
            assertNotNull(hit, "no rail found at " + s);
            assertEquals(l.sections().contact(1, 2, hit.at()), hit.contact(), "at " + s);
            assertEquals(s, hit.at(), 1e-9);
            checked++;
        }
        assertTrue(checked > 300);

        assertFalse(l.nearest(0.7, 65.5, 0.5, REACH, 1, 2).contact());
        assertFalse(l.nearest(8.5, 65.5, 0.5, REACH, 1, 2).contact());
        assertTrue(l.nearest(4.5, 65.5, 0.5, REACH, 1, 2).contact());
        assertTrue(l.nearest(14.5, 65.5, 0.5, REACH, 1, 2).contact());
        assertNotEquals(l.nearest(4.5, 65.5, 0.5, REACH, 1, 2).section(), l.nearest(14.5, 65.5, 0.5, REACH, 1, 2).section());
    }

    @Test
    @DisplayName("a shoe off the centre line by more than its paddle touches nothing")
    void lateralReach() {
        final RailLayout l = RailLayout.build(List.of(new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5))),
                List.of());
        assertNotNull(l.nearest(5, 65.5, 0.5 + REACH - 0.01, REACH, 1, 2));
        assertNull(l.nearest(5, 65.5, 0.5 + REACH + 0.01, REACH, 1, 2));

        assertNull(l.nearest(5, 65.5, 0.5 + 0.33, REACH, 1, 2));
    }

    @Test
    @DisplayName("the index finds every line from every side, long diagonals and curves included, and nothing out of reach")
    void indexFindsEveryLine() {
        final RailLayout l = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, Math.sqrt(2) * 60, true, new double[]{0.5, 64, 0.5, 60.5, 64, 60.5}),
                new RailLayout.EdgeIn(3, 4, 70, true, line(-100.5, 64, -40.5, -30.5, -40.5)),
                new RailLayout.EdgeIn(5, 6, Math.PI * 12 / 2, true, arc(100.5, 64, 100.5, 12, 0, Math.PI / 2, 97))),
                List.of());
        final java.util.Random r = new java.util.Random(7);
        int found = 0;
        for (RailLayout.Shape s : l.shapes()) {
            for (int i = 0; i < 400; i++) {
                final double at = s.length() * r.nextDouble();
                final double[] p = s.point(at);
                final double[] t = s.tangent(at);

                final double side = (r.nextDouble() * 2 - 1) * (REACH - 0.01);
                final RailLayout.Hit hit = l.nearest(p[0] - t[1] * side, p[1] + SHOE, p[2] + t[0] * side, REACH, 1, 2);
                assertNotNull(hit, "lost the line " + s.lo() + "-" + s.hi() + " at " + at);
                assertEquals(s.lo(), hit.lo());
                found++;
                final double out = (REACH + 0.02) * (r.nextBoolean() ? 1 : -1);
                assertNull(l.nearest(p[0] - t[1] * out, p[1] + SHOE, p[2] + t[0] * out, REACH, 1, 2),
                        "found a line beyond the reach at " + at);
            }
        }
        assertEquals(1200, found);
    }

    @Test
    @DisplayName("a kept mesh is one whose line and rail are unchanged, however its sections were renumbered")
    void meshReuse() {
        final RailLayout a = RailLayout.build(List.of(new RailLayout.EdgeIn(1, 2, 20, true, line(0.5, 64, 0.5, 20.5, 0.5)),
                new RailLayout.EdgeIn(5, 6, 20, true, line(0.5, 64, 9.5, 20.5, 9.5))), List.of());

        final RailLayout b = RailLayout.build(List.of(new RailLayout.EdgeIn(5, 6, 20, true, line(0.5, 64, 9.5, 20.5, 9.5)),
                new RailLayout.EdgeIn(1, 2, 20, true, line(0.5, 64, 0.5, 20.5, 0.5))), List.of(new SectionGraph.Cut(1, 2, 5)));
        assertNotEquals(a.sections().pieces(5, 6).get(0).section(), b.sections().pieces(5, 6).get(0).section(),
                "the case under test: a section renumbered");
        assertTrue(a.shape(5, 6).sameLine(b.shape(5, 6)));
        assertTrue(RailMesher.sameRail(a.sections().pieces(5, 6), b.sections().pieces(5, 6)));
        assertFalse(RailMesher.sameRail(a.sections().pieces(1, 2), b.sections().pieces(1, 2)), "the cut edge changed");

        final RailLayout c = RailLayout.build(List.of(new RailLayout.EdgeIn(1, 2, 21, true, line(0.5, 64, 0.5, 21.5, 0.5))),
                List.of());
        assertFalse(a.shape(1, 2).sameLine(c.shape(1, 2)));
        final double[] box = a.shape(1, 2).bounds();
        assertEquals(0.5, box[0], 1e-9);
        assertEquals(20.5, box[2], 1e-9);
    }

    @Test
    @DisplayName("a shoe at the wrong height finds no rail, and of two stacked tracks the level one wins")
    void heightWindow() {
        final RailLayout l = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5)),
                new RailLayout.EdgeIn(3, 4, 10, true, line(0.5, 70, 0.5, 10.5, 0.5))), List.of());
        assertNull(l.nearest(5, 67.5, 0.5, REACH, 1, 2));
        final RailLayout.Hit upper = l.nearest(5, 71.5, 0.5, REACH, 1, 2);
        assertNotNull(upper);
        assertEquals(70, upper.floorY(), D);
        assertEquals(3, upper.lo());
        final RailLayout.Hit lower = l.nearest(5, 65.5, 0.5, REACH, 1, 2);
        assertEquals(1, lower.lo());
    }

    @Test
    @DisplayName("the rail's top is the cap on the run and falls three pixels down a ramp")
    void railTop() {
        final RailLayout l = RailLayout.build(List.of(new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5))),
                List.of());
        assertEquals(64 + RailProfile.CAP_TOP / 16, l.nearest(5.5, 65.5, 0.5, REACH, 1, 2).railTopY(), D);

        final RailLayout.Hit toe = l.nearest(1.0, 65.5, 0.5, REACH, 1, 2);
        assertTrue(toe.contact());
        assertEquals(64 + (RailProfile.CAP_TOP - RailProfile.RAMP_DROP) / 16, toe.railTopY(), D);
        assertTrue(Double.isNaN(l.nearest(0.7, 65.5, 0.5, REACH, 1, 2).railTopY()));
    }

    @Test
    @DisplayName("an edge given from either end lays the same rail")
    void orientationFree() {
        final RailLayout a = RailLayout.build(List.of(new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5))),
                List.of(new SectionGraph.Cut(1, 2, 3)));
        final RailLayout b = RailLayout.build(List.of(new RailLayout.EdgeIn(2, 1, 10, true, line(10.5, 64, 0.5, 0.5, 0.5))),
                List.of(new SectionGraph.Cut(2, 1, 7)));
        for (double x = 0.5; x <= 10.5; x += 0.1) {
            final RailLayout.Hit ha = a.nearest(x, 65.5, 0.5, REACH, 1, 2);
            final RailLayout.Hit hb = b.nearest(x, 65.5, 0.5, REACH, 1, 2);
            assertEquals(ha.at(), hb.at(), 1e-9);
            assertEquals(ha.contact(), hb.contact(), "at " + x);
        }
    }

    @Test
    @DisplayName("on a curve the shoe follows the curve: contact over the arc's middle, at its true radius")
    void curves() {
        final double r = 6;
        final double length = Math.PI / 2 * r;
        final List<RailLayout.EdgeIn> edges = new ArrayList<>();
        edges.add(new RailLayout.EdgeIn(1, 2, length, true, arc(0, 64, 0, r, 0, Math.PI / 2, 33)));
        final RailLayout l = RailLayout.build(edges, List.of());
        final double a = Math.PI / 4;
        assertTrue(l.nearest(r * Math.cos(a), 65.5, r * Math.sin(a), REACH, 1, 2).contact());

        assertNull(l.nearest((r - 0.34) * Math.cos(a), 65.5, (r - 0.34) * Math.sin(a), REACH, 1, 2));

        assertEquals(length / 2, l.nearest(r * Math.cos(a), 65.5, r * Math.sin(a), REACH, 1, 2).at(), 0.02);
    }

    @Test
    @DisplayName("a turnout's split gets a long measured gap, the trunk the floor, and a junction stays one section")
    void junctionGaps() {
        final List<RailLayout.EdgeIn> edges = new ArrayList<>();
        edges.add(new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5)));
        edges.add(new RailLayout.EdgeIn(2, 3, 12, true, line(10.5, 64, 0.5, 22.5, 0.5)));
        final double r = 16;
        final int n = 65;
        final double[] curve = new double[3 * n];
        for (int i = 0; i < n; i++) {
            final double t = (Math.PI / 4) * i / (n - 1);
            curve[3 * i] = 10.5 + r * Math.sin(t);
            curve[3 * i + 1] = 64;
            curve[3 * i + 2] = 0.5 + r - r * Math.cos(t);
        }
        edges.add(new RailLayout.EdgeIn(2, 4, Math.PI / 4 * r, true, curve));
        final RailLayout l = RailLayout.build(edges, List.of());
        final SectionGraph g = l.sections();
        assertEquals(1, g.sectionCount(), "a junction carries the section across on jumpers");
        final double trunkGap = 10 - g.pieceAt(1, 2, 5).railTo();
        final double splitGap = g.pieceAt(2, 3, 6).railFrom();
        assertEquals(SectionGraph.JUNCTION_GAP_MIN, trunkGap, 0.05);
        assertTrue(splitGap > 3.0, "the straight must stop until the curve has cleared it, gap was " + splitGap);
        assertEquals(SectionGraph.RailEnd.RAMP_JUMPERED, g.pieceAt(2, 3, 6).fromEnd());

        assertFalse(l.nearest(10.5 + splitGap - 0.2, 65.5, 0.5, REACH, 1, 2).contact());
        assertTrue(l.nearest(10.5 + splitGap + 0.2, 65.5, 0.5, REACH, 1, 2).contact());
    }

    @Test
    @DisplayName("where the grade changes the rail stops a block and a half either side, jumpered: one section, no contact")
    void gradeChange() {
        final RailLayout l = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5)),
                new RailLayout.EdgeIn(2, 3, Math.sqrt(2) * 4, true, new double[]{10.5, 64, 0.5, 14.5, 68, 0.5})),
                List.of());
        final SectionGraph g = l.sections();
        assertEquals(1, g.sectionCount(), "a change of grade is jumpered across, not insulated");
        assertEquals(SectionGraph.RailEnd.RAMP_JUMPERED, g.pieceAt(1, 2, 5).toEnd());
        assertEquals(SectionGraph.RailEnd.RAMP_JUMPERED, g.pieceAt(2, 3, 1).fromEnd());
        assertEquals(10 - RailLayout.GRADE_GAP, g.pieceAt(1, 2, 5).railTo(), 1e-9);
        assertEquals(RailLayout.GRADE_GAP, g.pieceAt(2, 3, 1).railFrom(), 1e-9);
        assertFalse(l.nearest(10.5 - 1.2, 65.5, 0.5, REACH, 1, 2).contact());
        assertTrue(l.nearest(10.5 - 1.7, 65.5, 0.5, REACH, 1, 2).contact());

        final RailLayout up = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, Math.sqrt(2) * 3, true, new double[]{0.5, 64, 0.5, 3.5, 67, 0.5}),
                new RailLayout.EdgeIn(2, 3, Math.sqrt(2) * 3, true, new double[]{3.5, 67, 0.5, 6.5, 70, 0.5})), List.of());
        assertEquals(SectionGraph.RailEnd.THROUGH, up.sections().pieceAt(1, 2, 1).toEnd());
    }

    @Test
    @DisplayName("a short slope between two changes of grade is left bare, not given a block-long stub of rail")
    void noStubs() {
        final RailLayout l = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 5, true, line(0.5, 64, 0.5, 5.5, 0.5)),
                new RailLayout.EdgeIn(2, 3, Math.sqrt(2) * 3, true, new double[]{5.5, 64, 0.5, 8.5, 67, 0.5}),
                new RailLayout.EdgeIn(3, 4, 5, true, line(8.5, 67, 0.5, 13.5, 0.5))), List.of());
        assertFalse(l.sections().pieceAt(2, 3, 2).hasRail(), "a stub of rail mid-slope");
        assertTrue(l.sections().pieceAt(1, 2, 2).hasRail());
        assertEquals(1, l.sections().sectionCount());
    }

    @Test
    @DisplayName("a curve that climbs carries no rail but keeps the section, and the rail stops before it")
    void climbingCurveIsBare() {
        final int n = 33;
        final double[] climb = new double[3 * n];
        for (int i = 0; i < n; i++) {
            final double t = i / (n - 1.0);
            climb[3 * i] = 10.5 + 6 * t;
            climb[3 * i + 1] = 64 + 3 * t * t * (3 - 2 * t);
            climb[3 * i + 2] = 0.5 + 2 * t * t;
        }
        final RailLayout l = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5)),
                new RailLayout.EdgeIn(2, 3, 7, true, climb),
                new RailLayout.EdgeIn(3, 4, 10, true, line(16.5, 67, 2.5, 26.5, 2.5))), List.of());
        final SectionGraph g = l.sections();
        assertEquals(1, g.sectionCount());
        assertFalse(g.pieceAt(2, 3, 3).hasRail(), "no rail on a climbing curve");
        assertEquals(SectionGraph.RailEnd.RAMP_JUMPERED, g.pieceAt(1, 2, 5).toEnd());
        assertEquals(SectionGraph.RailEnd.RAMP_JUMPERED, g.pieceAt(3, 4, 5).fromEnd());
        assertTrue(RailLayout.climbsUnevenly(l.shape(2, 3)));
    }

    @Test
    @DisplayName("every gap is named for what it is, so a dead stretch on a hill is never called a junction")
    void gapsAreNamed() {
        final RailLayout grade = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5)),
                new RailLayout.EdgeIn(2, 3, Math.sqrt(2) * 4, true, new double[]{10.5, 64, 0.5, 14.5, 68, 0.5})),
                List.of(new SectionGraph.Cut(1, 2, 4)));
        final SectionGraph g = grade.sections();
        assertEquals(SectionGraph.Gap.NONE, g.gapAt(1, 2, 7), "on rail");
        assertEquals(SectionGraph.Gap.GRADE, g.gapAt(1, 2, 9.8), "the foot of the slope");
        assertEquals(SectionGraph.Gap.GRADE, g.gapAt(2, 3, 0.4), "the slope's own end at the same kink");
        assertEquals(SectionGraph.Gap.INSULATED, g.gapAt(1, 2, 4.1));
        assertEquals(SectionGraph.Gap.END, g.gapAt(1, 2, 0.2), "where the line ends");
        final RailLayout junction = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5)),
                new RailLayout.EdgeIn(2, 3, 10, true, line(10.5, 64, 0.5, 20.5, 0.5)),
                new RailLayout.EdgeIn(2, 4, 10, true, line(10.5, 64, 0.5, 18.5, 6.5))), List.of());
        assertEquals(SectionGraph.Gap.JUNCTION, junction.sections().gapAt(1, 2, 9.8));
        assertEquals(SectionGraph.Gap.NONE, junction.sections().gapAt(9, 10, 1), "not Power Track at all");
    }

    @Test
    @DisplayName("a climbing curve and a slope too short to lay are named as such")
    void bareStretchesAreNamed() {
        final int n = 33;
        final double[] climb = new double[3 * n];
        for (int i = 0; i < n; i++) {
            final double t = i / (n - 1.0);
            climb[3 * i] = 10.5 + 6 * t;
            climb[3 * i + 1] = 64 + 3 * t * t * (3 - 2 * t);
            climb[3 * i + 2] = 0.5 + 2 * t * t;
        }
        final RailLayout curve = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5)),
                new RailLayout.EdgeIn(2, 3, 7, true, climb),
                new RailLayout.EdgeIn(3, 4, 10, true, line(16.5, 67, 2.5, 26.5, 2.5))), List.of());
        assertEquals(SectionGraph.Gap.CLIMBING_CURVE, curve.sections().gapAt(2, 3, 3));
        final RailLayout stub = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 5, true, line(0.5, 64, 0.5, 5.5, 0.5)),
                new RailLayout.EdgeIn(2, 3, Math.sqrt(2) * 3, true, new double[]{5.5, 64, 0.5, 8.5, 67, 0.5}),
                new RailLayout.EdgeIn(3, 4, 5, true, line(8.5, 67, 0.5, 13.5, 0.5))), List.of());
        assertEquals(SectionGraph.Gap.SHORT, stub.sections().gapAt(2, 3, 2));
    }

    @Test
    @DisplayName("a turnout at a slope's foot gaps the through route at least as far as a plain change of grade")
    void junctionAtAGradeChange() {
        final RailLayout l = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5)),
                new RailLayout.EdgeIn(2, 3, Math.sqrt(2) * 4, true, new double[]{10.5, 64, 0.5, 14.5, 68, 0.5}),
                new RailLayout.EdgeIn(2, 4, 10, true, line(10.5, 64, 0.5, 18.5, 6.5))), List.of());
        final SectionGraph g = l.sections();
        assertTrue(g.pieceAt(1, 2, 5).railTo() <= 10 - RailLayout.GRADE_GAP + 1e-9,
                "the through route pitches over the kink: " + g.pieceAt(1, 2, 5).railTo());
        assertTrue(g.pieceAt(2, 3, 1).railFrom() >= RailLayout.GRADE_GAP - 1e-9);
        final List<Long> here = List.of(SectionGraph.key(1, 2), SectionGraph.key(2, 3), SectionGraph.key(2, 4));
        assertTrue(RailLayout.throughKink(levelShapes(l), levelPower(l), here, 2), "the through route kinks");

        final RailLayout level = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5)),
                new RailLayout.EdgeIn(2, 3, 10, true, line(10.5, 64, 0.5, 20.5, 0.5)),
                new RailLayout.EdgeIn(2, 4, 10, true, line(10.5, 64, 0.5, 18.5, 6.5))), List.of());
        assertFalse(RailLayout.throughKink(levelShapes(level), levelPower(level), here, 2));

        final RailLayout branchClimbs = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5)),
                new RailLayout.EdgeIn(2, 3, 10, true, line(10.5, 64, 0.5, 20.5, 0.5)),
                new RailLayout.EdgeIn(2, 4, 10, true, new double[]{10.5, 64, 0.5, 13.5, 67, 4.5})), List.of());
        assertFalse(RailLayout.throughKink(levelShapes(branchClimbs), levelPower(branchClimbs), here, 2));
    }

    private static java.util.Map<Long, RailLayout.Shape> levelShapes(RailLayout l) {
        final java.util.Map<Long, RailLayout.Shape> out = new java.util.HashMap<>();
        for (RailLayout.Shape s : l.shapes()) {
            out.put(SectionGraph.key(s.lo(), s.hi()), s);
        }
        return out;
    }

    private static java.util.Map<Long, Boolean> levelPower(RailLayout l) {
        final java.util.Map<Long, Boolean> out = new java.util.HashMap<>();
        for (RailLayout.Shape s : l.shapes()) {
            out.put(SectionGraph.key(s.lo(), s.hi()), true);
        }
        return out;
    }

    @Test
    @DisplayName("ordinary track decides junctions and gaps but carries no rail")
    void ordinaryTrack() {
        final RailLayout l = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 10, true, line(0.5, 64, 0.5, 10.5, 0.5)),
                new RailLayout.EdgeIn(2, 3, 10, false, line(10.5, 64, 0.5, 20.5, 0.5))), List.of());
        assertEquals(1, l.shapes().size());
        assertNull(l.nearest(15, 65.5, 0.5, REACH, 1, 2));
        assertEquals(SectionGraph.RailEnd.RAMP_END, l.sections().pieceAt(1, 2, 5).toEnd());
        assertFalse(l.nearest(10.3, 65.5, 0.5, REACH, 1, 2).contact());
    }

    @Test
    @DisplayName("the fingerprint moves with a material, a length or an insulator, and not with order")
    void fingerprint() {
        final List<RailLayout.EdgeIn> a = List.of(new RailLayout.EdgeIn(1, 2, 10, true, line(0, 0, 0, 10, 0)),
                new RailLayout.EdgeIn(2, 3, 5, false, line(10, 0, 0, 15, 0)));
        final List<RailLayout.EdgeIn> reordered = List.of(a.get(1), a.get(0));
        final List<RailLayout.EdgeIn> material = List.of(new RailLayout.EdgeIn(1, 2, 10, false, line(0, 0, 0, 10, 0)), a.get(1));
        final int base = RailLayout.fingerprint(a, List.of());
        assertEquals(base, RailLayout.fingerprint(reordered, List.of()));
        assertNotEquals(base, RailLayout.fingerprint(material, List.of()));
        assertNotEquals(base, RailLayout.fingerprint(a, List.of(new SectionGraph.Cut(1, 2, 4))));
    }
}
