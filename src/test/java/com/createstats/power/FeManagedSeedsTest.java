package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeManagedSeedsTest {
    private static final long ANCHOR = 11L;
    private static final long CABLE = 22L;
    private static final long CONTROLLER_END = 33L;

    private static Map<Long, Integer> flows(long key, int fe) {
        final Map<Long, Integer> map = new LinkedHashMap<>();
        map.put(key, fe);
        return map;
    }

    @Test
    @DisplayName("a manager with nothing to say cannot wipe one that has")
    void anEmptyReportDoesNotWipeAFullOne() {
        final FeManagedSeeds seeds = new FeManagedSeeds();

        seeds.add(flows(CABLE, -5120), CONTROLLER_END, 0, 100L);

        seeds.add(Map.of(), ANCHOR, 0, 100L);

        assertEquals(-5120, seeds.seeds().get(CABLE),
                "the controller's report survived the panel's silence");
        assertTrue(seeds.boundaryFlow() >= 5120, "and so did the edge it declared");
    }

    @Test
    @DisplayName("the previous tick's contributions are gone, not added to")
    void aNewTickStartsEmpty() {
        final FeManagedSeeds seeds = new FeManagedSeeds();
        seeds.add(flows(CABLE, -5120), CONTROLLER_END, 0, 100L);
        seeds.add(flows(CABLE, -80), CONTROLLER_END, 0, 101L);

        assertEquals(-80, seeds.seeds().get(CABLE), "a running total would never fall back");
        assertEquals(80, seeds.boundaryFlow());
    }

    @Test
    @DisplayName("two managers on one grid compose instead of competing")
    void twoManagersCompose() {
        final FeManagedSeeds seeds = new FeManagedSeeds();
        seeds.add(flows(CABLE, 300), ANCHOR, 0, 7L);
        seeds.add(flows(CONTROLLER_END, 200), ANCHOR, 0, 7L);

        assertEquals(300, seeds.seeds().get(CABLE));
        assertEquals(200, seeds.seeds().get(CONTROLLER_END));

        assertEquals(-500, seeds.seeds().get(ANCHOR));
    }

    @Test
    @DisplayName("a back boss with nobody reporting the far end still seeds what left it")
    void anchorSupplyStandsInForAnUnseenSink() {
        final FeManagedSeeds seeds = new FeManagedSeeds();
        seeds.add(Map.of(), ANCHOR, 5120, 4L);

        assertEquals(-5120, seeds.seeds().get(ANCHOR), "the panel put it into the wiring");
        assertEquals(ANCHOR, seeds.boundaryKey());
        assertEquals(5120, seeds.boundaryFlow());
    }

    @Test
    @DisplayName("an anchorless report contributes its nodes and declares no edge")
    void anchorlessReportsStillCount() {
        final FeManagedSeeds seeds = new FeManagedSeeds();
        seeds.add(flows(CABLE, 42), FeFlowGraph.NO_BOUNDARY, 0, 1L);

        assertEquals(42, seeds.seeds().get(CABLE));
        assertEquals(FeFlowGraph.NO_BOUNDARY, seeds.boundaryKey());
        assertEquals(0, seeds.boundaryFlow());
    }

    @Test
    @DisplayName("the export and a real delivery on the same leg are both counted")
    void exportAndDeliveryAddUp() {
        final FeManagedSeeds seeds = new FeManagedSeeds();
        seeds.add(flows(CABLE, 700), ANCHOR, 5120, 9L);

        assertEquals(700, seeds.seeds().get(CABLE));
        assertEquals(-5820, seeds.seeds().get(ANCHOR));
        assertEquals(5820, seeds.boundaryFlow());
    }
}
