package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathHealthTest {
    static PathSurvey surveyedLake(Pathway p) {
        PathSpline sp = PathSpline.of(p);
        PathSurvey sv = new PathSurvey(p.version(), sp);
        for (int i = 0; i < sp.sampleCount(); i++) {
            double x = sp.sampleX(i);
            double z = sp.sampleZ(i);
            boolean wet = OwnerPathways.water(x, z);
            sv.set(i, 62.0, 62.0, 62.0, wet ? Ground.WATER : Ground.ROAD, PathSurvey.HEADROOM_MAX, null, 1L);
        }
        return sv;
    }

    @Test
    void theLakeAtN3IsRedWithAFixThatMovesN3() {
        Pathway p = OwnerPathways.lake();
        List<PathHealth.Item> items = PathHealth.of(p, surveyedLake(p), false, 2.0, 0.0, "");
        System.out.println("Pathway 2: " + items);
        assertTrue(!items.isEmpty(), "the lake is found");
        PathHealth.Item first = items.get(0);
        assertEquals(SegmentCheck.Level.RED, first.level(), "red first");
        assertEquals(PathSurvey.Fix.MOVE_NODE, first.fix(), "its fix moves a node");
        assertEquals("N3", p.label(first.node()), "and the node is N3: " + first);
        assertTrue(first.why().contains("water") && first.why().contains("N3"), first.why());
        assertEquals(first, PathHealth.first(items), "the rail shows it");
    }

    @Test
    void amphibiousMakesTheLakeAmberAndLeavesNoFixToMove() {
        Pathway p = OwnerPathways.lake();
        List<PathHealth.Item> items = PathHealth.of(p, surveyedLake(p), true, 2.0, 0.0, "");
        assertEquals(SegmentCheck.Level.AMBER, PathHealth.worst(items), "water is amber for a vehicle that swims");
        for (PathHealth.Item i : items) assertTrue(i.fix() != PathSurvey.Fix.MOVE_NODE, "nothing to move: " + i);
    }

    @Test
    void aBendTheCarCannotFollowIsRedWithWidenAndFairClearsThem() {
        Pathway p = PathFairing.fair(OwnerPathways.painted()).pathway();
        double own = MkIvSim.carrier().minRadius();
        List<PathHealth.Item> items = PathHealth.of(p, null, false, 2.0, own, "Carrier 1");
        System.out.println("Pathway 1 faired: " + items.size() + " items, first " + PathHealth.first(items));
        PathHealth.Item bend = PathHealth.first(items);
        assertNotNull(bend, "Carrier 1 cannot follow the teardrop");
        assertEquals(PathSurvey.Fix.WIDEN, bend.fix());
        assertTrue(bend.why().contains("Carrier 1") && bend.why().contains("tighter than"), bend.why());

        PathFairing.Widened w = PathFairing.openBend(p, bend.at(), own, null);
        assertNotNull(w, "Widen at " + String.format(Locale.ROOT, "%.1f", bend.at()) + " opens it");
        Pathway drivable = PathFairing.fairFor(OwnerPathways.painted(), own, null).pathway();
        assertTrue(PathHealth.of(drivable, null, false, 2.0, own, "Carrier 1").isEmpty(), "after Fair, nothing to say");
    }

    @Test
    void noVehicleNoBendJudged() {
        Pathway p = PathFairing.fair(OwnerPathways.painted()).pathway();
        assertTrue(PathHealth.of(p, null, false, 2.0, 0.0, "").isEmpty(), "bends are judged for the vehicles on the line");
    }

    @Test
    void theListCrossesTheWireWhole() throws Exception {
        Pathway p = OwnerPathways.lake();
        List<PathHealth.Item> items = PathHealth.of(p, surveyedLake(p), false, 2.0, MkIvSim.carrier().minRadius(), "Carrier 1");
        List<PathHealth.Item> back = PathHealth.decode(PathHealth.encode(items));
        assertEquals(items.size(), back.size());
        for (int i = 0; i < items.size(); i++) {
            PathHealth.Item a = items.get(i);
            PathHealth.Item b = back.get(i);
            assertEquals(a.level(), b.level());
            assertEquals(a.fix(), b.fix());
            assertEquals(a.node(), b.node());
            assertEquals(a.why(), b.why());
            assertEquals(a.from(), b.from(), 1.0e-3);
            assertEquals(a.at(), b.at(), 1.0e-3);
        }
        assertTrue(PathHealth.decode(new byte[0]).isEmpty(), "nothing is nothing");
    }
}
