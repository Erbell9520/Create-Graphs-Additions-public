package com.createstats.powertrack.core;

import com.createstats.powertrack.core.SectionGraph.Cut;
import com.createstats.powertrack.core.SectionGraph.Edge;
import com.createstats.powertrack.core.SectionGraph.Piece;
import com.createstats.powertrack.core.SectionGraph.RailEnd;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionGraphTest {
    private static final double D = 1e-9;

    @Test
    @DisplayName("a plain line of Power Track is one section, with the rail stopping half a block from each end")
    void aLineIsOneSection() {
        final SectionGraph g = SectionGraph.build(List.of(
                new Edge(1, 2, 10, true), new Edge(2, 3, 10, true)), List.of());
        assertEquals(1, g.sectionCount());
        assertEquals(g.sectionAt(1, 2, 5), g.sectionAt(2, 3, 5));

        assertTrue(g.contact(1, 2, 10));
        assertTrue(g.contact(2, 3, 0));
        final Piece first = g.pieceAt(1, 2, 5);
        assertEquals(RailEnd.RAMP_END, first.fromEnd());
        assertEquals(RailEnd.THROUGH, first.toEnd());
        assertEquals(SectionGraph.END_GAP, first.railFrom(), D);

        assertFalse(g.contact(1, 2, 0.2));
        assertFalse(g.contact(2, 3, 9.8));
        assertTrue(g.contact(1, 2, 0.5));
    }

    @Test
    @DisplayName("an insulator splits a run into two sections, with an insulated gap either side of it")
    void anInsulatorEndsASection() {
        final SectionGraph g = SectionGraph.build(List.of(new Edge(1, 2, 20, true)), List.of(new Cut(1, 2, 8)));
        assertEquals(2, g.sectionCount());
        assertNotEquals(g.sectionAt(1, 2, 4), g.sectionAt(1, 2, 12));
        final double half = SectionGraph.INSULATOR_GAP / 2;
        assertFalse(g.contact(1, 2, 8));
        assertFalse(g.contact(1, 2, 8 - half + 0.01));
        assertTrue(g.contact(1, 2, 8 - half));
        assertTrue(g.contact(1, 2, 8 + half));
        assertEquals(RailEnd.RAMP_INSULATED, g.pieceAt(1, 2, 4).toEnd());
        assertEquals(RailEnd.RAMP_INSULATED, g.pieceAt(1, 2, 12).fromEnd());
    }

    @Test
    @DisplayName("an edge read from either end answers the same, insulators included")
    void orientationDoesNotMatter() {
        final SectionGraph g = SectionGraph.build(List.of(new Edge(7, 3, 20, true)), List.of(new Cut(7, 3, 6)));
        for (double at = 0; at <= 20; at += 0.25) {
            assertEquals(g.sectionAt(7, 3, at), g.sectionAt(3, 7, 20 - at), "at " + at);
            assertEquals(g.contact(7, 3, at), g.contact(3, 7, 20 - at), "at " + at);
        }
        final List<Piece> forward = g.pieces(7, 3);
        final List<Piece> back = g.pieces(3, 7);
        assertEquals(forward.size(), back.size());
        assertEquals(forward.get(0).section(), back.get(back.size() - 1).section());
    }

    @Test
    @DisplayName("a junction keeps one section across its jumper cables, but the rail stops clear of the points")
    void aJunctionIsJumperedNotInsulated() {
        final SectionGraph g = SectionGraph.build(List.of(
                new Edge(1, 2, 20, true), new Edge(2, 3, 20, true, 0, 0),
                new Edge(2, 4, 20, true, 3.5, 0)), List.of());
        assertEquals(1, g.sectionCount(), "jumpers carry the section across the points");
        assertEquals(RailEnd.RAMP_JUMPERED, g.pieceAt(2, 4, 10).fromEnd());

        assertFalse(g.contact(2, 4, 3.4));
        assertTrue(g.contact(2, 4, 3.5));
        assertFalse(g.contact(1, 2, 20 - SectionGraph.JUNCTION_GAP_MIN + 0.01));
        assertTrue(g.contact(1, 2, 20 - SectionGraph.JUNCTION_GAP_MIN));
    }

    @Test
    @DisplayName("where Power Track meets ordinary track the rail ends, and ordinary track has no section")
    void electrificationEnds() {
        final SectionGraph g = SectionGraph.build(List.of(
                new Edge(1, 2, 10, true), new Edge(2, 3, 10, false)), List.of());
        assertEquals(RailEnd.RAMP_END, g.pieceAt(1, 2, 5).toEnd());
        assertFalse(g.contact(1, 2, 9.8));
        assertEquals(-1, g.sectionAt(2, 3, 5));
        assertFalse(g.contact(2, 3, 5));
        assertNull(g.pieceAt(2, 3, 5));
    }

    @Test
    @DisplayName("an insulator on ordinary track insulates nothing, and an edge given twice is one edge")
    void strayInputs() {
        final SectionGraph g = SectionGraph.build(List.of(
                new Edge(1, 2, 10, true), new Edge(2, 1, 10, true), new Edge(2, 3, 10, false)),
                List.of(new Cut(2, 3, 5)));
        assertEquals(1, g.sectionCount());
        assertEquals(1, g.pieces(1, 2).size());
    }

    @Test
    @DisplayName("a piece shorter than its own gaps has no rail at all")
    void aStubHasNoRail() {
        final SectionGraph g = SectionGraph.build(List.of(new Edge(1, 2, 0.8, true)), List.of());
        assertFalse(g.pieceAt(1, 2, 0.4).hasRail());
        assertFalse(g.contact(1, 2, 0.4));
    }

    @Test
    @DisplayName("two insulators on one edge leave a middle section of its own")
    void twoCutsThreeSections() {
        final SectionGraph g = SectionGraph.build(List.of(new Edge(1, 2, 30, true)),
                List.of(new Cut(1, 2, 20), new Cut(2, 1, 20)));
        assertEquals(3, g.sectionCount());
        assertEquals(3, g.pieces(1, 2).size());
        assertNotEquals(g.sectionAt(1, 2, 5), g.sectionAt(1, 2, 15));
        assertNotEquals(g.sectionAt(1, 2, 15), g.sectionAt(1, 2, 25));
    }

    @Test
    @DisplayName("a loop with one insulator is still one section: the rail meets itself the long way round")
    void aLoopNeedsTwoInsulators() {
        final List<Edge> loop = List.of(new Edge(1, 2, 10, true), new Edge(2, 3, 10, true),
                new Edge(3, 4, 10, true), new Edge(4, 1, 10, true));
        assertEquals(1, SectionGraph.build(loop, List.of(new Cut(1, 2, 5))).sectionCount());
        assertEquals(2, SectionGraph.build(loop, List.of(new Cut(1, 2, 5), new Cut(3, 4, 5))).sectionCount());
    }

    @Test
    @DisplayName("the same graph numbers its sections the same way every time it is built")
    void sectionIdsAreStable() {
        final List<Edge> edges = List.of(new Edge(5, 9, 10, true), new Edge(1, 5, 10, true),
                new Edge(9, 12, 10, true));
        final List<Cut> cuts = List.of(new Cut(5, 9, 5));
        final SectionGraph a = SectionGraph.build(edges, cuts);
        final SectionGraph b = SectionGraph.build(List.of(edges.get(2), edges.get(0), edges.get(1)), cuts);
        for (double at = 0; at <= 10; at += 1) {
            assertEquals(a.sectionAt(5, 9, at), b.sectionAt(5, 9, at));
            assertEquals(a.sectionAt(1, 5, at), b.sectionAt(1, 5, at));
        }
    }
}
