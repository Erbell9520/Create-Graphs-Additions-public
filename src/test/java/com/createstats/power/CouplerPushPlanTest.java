package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplerPushPlanTest {
    @Test
    @DisplayName("a lone connector on a motor: everything goes to its own block, nothing into a network that skips it")
    void loneConnectorFeedsItsOwnBlock() {
        final CouplerPushPlan plan = CouplerPushPlan.of(8192, 300, 0);
        assertEquals(300, plan.direct());
        assertEquals(0, plan.network());
    }

    @Test
    @DisplayName("own block first, then the rest of their network, never more than the offer")
    void ownFirstThenNetwork() {
        assertEquals(new CouplerPushPlan(1000, 2000), CouplerPushPlan.of(3000, 1000, 5000));
        assertEquals(new CouplerPushPlan(3000, 0), CouplerPushPlan.of(3000, 4000, 5000));
        assertEquals(new CouplerPushPlan(0, 2500), CouplerPushPlan.of(3000, 0, 2500));
        assertEquals(new CouplerPushPlan(0, 0), CouplerPushPlan.of(0, 100, 100));
    }

    @Test
    @DisplayName("over any figures the plan never exceeds the offer or either side's room")
    void neverOverpromises() {
        final Random r = new Random(20261021L);
        for (int i = 0; i < 10_000; i++) {
            final int offer = r.nextInt(20_000) - 1000;
            final int own = r.nextInt(20_000) - 1000;
            final int net = r.nextInt(20_000) - 1000;
            final CouplerPushPlan p = CouplerPushPlan.of(offer, own, net);
            assertTrue(p.direct() >= 0 && p.network() >= 0);
            assertTrue(p.total() <= Math.max(0, offer));
            assertTrue(p.direct() <= Math.max(0, own));
            assertTrue(p.network() <= Math.max(0, net), "the network was promised a block it skips");
        }
    }
}
