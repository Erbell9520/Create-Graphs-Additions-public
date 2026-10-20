package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialCostTest {
    @Test
    void theMeanIsTheLastSecondAndTheWorstTheLastFive() {
        AerialCost cost = new AerialCost();
        assertEquals("Cost: not measured yet", cost.summary());

        cost.add(AerialCost.Phase.READ, 9_000_000L);
        cost.endTick();
        for (int i = 0; i < AerialCost.MEAN_TICKS; i++) {
            cost.add(AerialCost.Phase.READ, 100_000L);
            cost.add(AerialCost.Phase.PILOT, 20_000L);
            cost.add(AerialCost.Phase.PILOT, 20_000L);
            cost.endTick();
        }
        assertEquals(100.0, cost.meanMicros(AerialCost.Phase.READ), 1.0e-9, "the slow tick has left the mean");
        assertEquals(40.0, cost.meanMicros(AerialCost.Phase.PILOT), 1.0e-9, "a phase run twice in a tick adds up");
        assertEquals(140.0, cost.meanMicros(), 1.0e-9);
        assertEquals(9000.0, cost.worstMicros(AerialCost.Phase.READ), 1.0e-9, "but not the worst");
        assertEquals(AerialCost.Phase.READ, cost.heaviest());

        for (int i = 0; i < AerialCost.WORST_TICKS; i++) cost.endTick();
        assertEquals(0.0, cost.worstMicros(AerialCost.Phase.READ), 1.0e-9);
        assertEquals(AerialCost.WORST_TICKS, cost.ticks());
    }

    @Test
    void governorChangesAreCountedOverASecond() {
        AerialCost cost = new AerialCost();
        for (int i = 0; i < 40; i++) {
            cost.changes(i % 4 == 0 ? 2 : 0);
            cost.endTick();
        }
        assertEquals(10, cost.changesPerSecond(), "two every fourth tick");
        assertTrue(cost.summary().contains("governors 10 changes a second"), cost.summary());
        assertTrue(cost.phases().startsWith("scan 0 read 0 pilot 0"), cost.phases());
    }
}
