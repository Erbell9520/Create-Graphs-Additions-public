package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeEdgeFlowTest {
    private static final long NOW = 1_000L;

    private static FeFlowGraph tree(Map<Long, Long> attachments, long... childParent) {
        final Map<Long, Long> parentOf = new LinkedHashMap<>();
        for (int i = 0; i < childParent.length; i += 2) {
            parentOf.put(childParent[i], childParent[i + 1]);
        }
        return FeFlowGraph.of(parentOf, attachments);
    }

    private static Map<Long, Integer> flows(Object... keyValue) {
        final Map<Long, Integer> out = new HashMap<>();
        for (int i = 0; i < keyValue.length; i += 2) {
            out.put(((Number) keyValue[i]).longValue(), ((Number) keyValue[i + 1]).intValue());
        }
        return out;
    }

    @Test
    @DisplayName("a pole between two connectors carries what its far side draws, either way round the tree")
    void anEdgeCarriesItsSubtree() {
        final FeFlowGraph graph = tree(Map.of(90L, 2L), 1, 1, 2, 1);
        graph.recompute(flows(1, -700, 90L, 700), Map.of(), NOW);
        assertEquals(700, graph.edgeFlow(1L, 2L, NOW));
        assertEquals(700, graph.edgeFlow(2L, 1L, NOW), "either end may be named first");
    }

    @Test
    @DisplayName("a pole on a busy trunk reads its own spur, not the trunk its supply side also feeds")
    void aSpurIsNotTheTrunk() {
        final FeFlowGraph graph = tree(Map.of(90L, 3L, 91L, 4L), 1, 1, 2, 1, 3, 2, 4, 2);
        graph.recompute(flows(1, -5_300, 90L, 5_000, 91L, 300), Map.of(), NOW);
        assertEquals(300, graph.edgeFlow(2L, 4L, NOW));
        assertEquals(5_300, graph.flowThrough(2L, NOW), "the line side's own node carries the whole trunk");
    }

    @Test
    @DisplayName("two nodes that are not the ends of one edge, or a stale reading, give UNKNOWN rather than a number")
    void unknown() {
        final FeFlowGraph graph = tree(Map.of(), 1, 1, 2, 1, 3, 2);
        assertEquals(FeFlowGraph.UNKNOWN, graph.edgeFlow(1L, 2L, NOW), "before any recompute");
        graph.recompute(flows(1, -50, 3, 50), Map.of(), NOW);
        assertEquals(FeFlowGraph.UNKNOWN, graph.edgeFlow(1L, 3L, NOW), "not adjacent");
        assertEquals(FeFlowGraph.UNKNOWN, graph.edgeFlow(1L, 99L, NOW), "not in the tree");
        assertEquals(FeFlowGraph.UNKNOWN, graph.edgeFlow(1L, 2L, NOW + 2), "stale");
        assertEquals(50, graph.edgeFlow(2L, 3L, NOW + 1), "a tick behind is still the reading");
    }
}
