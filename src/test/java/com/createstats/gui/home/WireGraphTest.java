package com.createstats.gui.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WireGraphTest {
    private static WireGraph tee() {
        final List<WireGraph.Wire> w = new ArrayList<>();
        final int[] trunk = {250, 250, 250, 240, 240, 240};
        for (int x = 0; x < 6; x++) {
            w.add(new WireGraph.Wire(x, 64, 0, trunk[x]));
        }
        for (int z = -1; z >= -3; z--) {
            w.add(new WireGraph.Wire(2, 64, z, 10));
        }
        return new WireGraph(w);
    }

    @Test
    @DisplayName("the busiest node is the entry, and hops count out from it")
    void entryIsTheBusiest() {
        final WireGraph g = tee();
        assertEquals(0, g.hops(0, 64, 0));
        assertEquals(5, g.hops(5, 64, 0));
        assertEquals(5, g.hops(2, 64, -3));
        assertEquals(-1, g.hops(9, 64, 9));
    }

    @Test
    @DisplayName("a length carries the lesser of its two ends: a branch off a busy trunk carries the branch")
    void lengthsCarryTheLesserEnd() {
        final WireGraph g = tee();
        boolean sawBranch = false;
        for (WireGraph.Segment s : g.segments(60, 70)) {
            if (s.ax() == 2 && s.bx() == 2) {
                assertEquals(10, s.flow());
                sawBranch = true;
            }
            if (s.az() == 0 && s.bz() == 0 && s.ax() == 2) {
                assertEquals(240, s.flow());
            }
        }
        assertTrue(sawBranch);
    }

    @Test
    @DisplayName("each length points away from the entry")
    void lengthsPointDownstream() {
        final WireGraph g = tee();
        for (WireGraph.Segment s : g.segments(60, 70)) {
            final int ha = g.hops(s.ax(), 64, s.az());
            final int hb = g.hops(s.bx(), 64, s.bz());
            assertEquals(Integer.compare(hb, ha), s.towards());
        }
    }

    @Test
    @DisplayName("the way back from a fixture beside the branch's tip runs down the branch and along the trunk")
    void pathBackToTheEntry() {
        final WireGraph g = tee();
        final List<long[]> path = g.pathToEntry(List.of(new int[] {3, 64, -3}));
        assertEquals(6, path.size());
        assertEquals(2, path.get(0)[0]);
        assertEquals(-3, path.get(0)[2]);
        assertEquals(0, path.get(path.size() - 1)[0]);
        assertEquals(0, path.get(path.size() - 1)[2]);
        assertTrue(g.pathToEntry(List.of(new int[] {40, 64, 40})).isEmpty(), "nothing touches: no path");
    }

    @Test
    @DisplayName("a second circuit is walked from its own entry, not left unreachable")
    void separatePiecesHaveTheirOwnEntry() {
        final List<WireGraph.Wire> w = new ArrayList<>();
        w.add(new WireGraph.Wire(0, 64, 0, 90));
        w.add(new WireGraph.Wire(1, 64, 0, 80));
        w.add(new WireGraph.Wire(10, 64, 10, 30));
        w.add(new WireGraph.Wire(11, 64, 10, 20));
        final WireGraph g = new WireGraph(w);
        assertEquals(0, g.hops(10, 64, 10));
        assertEquals(1, g.hops(11, 64, 10));
    }

    @Test
    @DisplayName("a cable that climbs to the next floor is a riser; only the band's own lengths are drawn")
    void risersAndBands() {
        final List<WireGraph.Wire> w = new ArrayList<>();
        w.add(new WireGraph.Wire(0, 64, 0, 50));
        w.add(new WireGraph.Wire(1, 64, 0, 50));
        w.add(new WireGraph.Wire(1, 65, 0, 50));
        w.add(new WireGraph.Wire(1, 66, 0, 50));
        w.add(new WireGraph.Wire(2, 70, 0, 50));
        w.add(new WireGraph.Wire(3, 70, 0, 50));
        final WireGraph g = new WireGraph(w);
        assertTrue(g.riser(1, 0, 60, 68));
        assertFalse(g.riser(0, 0, 60, 68));
        assertTrue(g.climbs(1, 64, 0), "the foot of the riser has a neighbour above");
        assertFalse(g.climbs(0, 64, 0));
        assertEquals(1, g.segments(60, 68).size(), "the upper floor's length is not this storey's");
    }

    @Test
    @DisplayName("downstream of a node is what its cable feeds: the tee feeds both its arms, a tip feeds only itself")
    void downstream() {
        final WireGraph g = tee();
        final java.util.Set<Long> fromTee = g.downstream(2, 64, 0);
        assertEquals(7, fromTee.size(), "itself, three down the branch, three along the trunk");
        assertTrue(fromTee.contains(WireGraph.key(2, 64, -3)) && fromTee.contains(WireGraph.key(5, 64, 0)));
        assertFalse(fromTee.contains(WireGraph.key(1, 64, 0)), "never upstream");
        assertEquals(9, g.downstream(0, 64, 0).size(), "the entry feeds everything");
        assertEquals(1, g.downstream(2, 64, -3).size(), "a tip feeds only what touches it");
        assertTrue(g.downstream(9, 64, 9).isEmpty(), "a block that is not wiring feeds nothing");

        assertTrue(WireGraph.touches(fromTee, 3, 64, -3));
        assertFalse(WireGraph.touches(g.downstream(4, 64, 0), 3, 64, -3));
    }

    @Test
    @DisplayName("each piece has one entry, the busiest node; a run ends where its last node has one neighbour")
    void entriesAndEnds() {
        final WireGraph g = tee();
        final List<WireGraph.Wire> entries = g.entries();
        assertEquals(1, entries.size());
        assertEquals(0, entries.get(0).x());
        assertTrue(g.end(5, 64, 0), "the trunk's far end feeds something");
        assertTrue(g.end(2, 64, -3), "so does the branch's tip");
        assertFalse(g.end(2, 64, 0), "the tee is a junction, not an end");
        assertFalse(g.end(3, 64, 0), "a node mid-run is not an end");
        assertFalse(g.end(9, 64, 9), "a block that is not wiring ends nothing");

        final List<WireGraph.Wire> w = new ArrayList<>();
        w.add(new WireGraph.Wire(0, 64, 0, 90));
        w.add(new WireGraph.Wire(1, 64, 0, 80));
        w.add(new WireGraph.Wire(10, 64, 10, 30));
        w.add(new WireGraph.Wire(11, 64, 10, 20));
        assertEquals(2, new WireGraph(w).entries().size(), "two pieces, two entries");

        final List<WireGraph.Wire> r = new ArrayList<>();
        r.add(new WireGraph.Wire(0, 64, 0, 50));
        r.add(new WireGraph.Wire(1, 64, 0, 50));
        r.add(new WireGraph.Wire(1, 65, 0, 50));
        assertFalse(new WireGraph(r).end(1, 64, 0));
    }
}
