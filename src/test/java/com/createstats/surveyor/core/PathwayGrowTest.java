package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PathwayGrowTest {
    private static Pathway eastward() {
        return Pathway.create(new UUID(1, 1), "Road", 3, new UUID(2, 2)).append(0, 64, 0, false).append(10, 64, 0, false);
    }

    @Test
    void aStrokeFromNodeOneGrowsTheLineBackwardsInOrder() {
        List<double[]> outward = List.of(new double[]{-2, 64, 0}, new double[]{-4, 64, 0}, new double[]{-6, 64, 0});
        Pathway grown = eastward().prependOutward(outward);
        double[] xs = grown.nodes().stream().mapToDouble(PathNode::x).toArray();
        assertEquals(List.of(-6.0, -4.0, -2.0, 0.0, 10.0), java.util.Arrays.stream(xs).boxed().toList(),
                "west to east, no doubling back: the stroke's far end first, its near end next to the old node 1");
    }

    @Test
    void theOldNodesKeepTheirIds() {
        Pathway before = eastward();
        Pathway grown = before.prependOutward(List.of(new double[]{-3, 64, 0}));
        assertEquals(before.nodes().get(0).id(), grown.nodes().get(1).id());
        assertEquals(before.nodes().get(1).id(), grown.nodes().get(2).id());
        assertEquals(3, grown.nodes().stream().map(PathNode::id).distinct().count());
    }

    @Test
    void aStrokeFromTheLastNodeCarriesOn() {
        Pathway grown = eastward().appendAll(List.of(new double[]{12, 64, 0}, new double[]{14, 64, 0}));
        assertEquals(14.0, grown.nodes().get(grown.nodes().size() - 1).x(), 0.0);
        assertEquals(12.0, grown.nodes().get(2).x(), 0.0);
    }
}
