package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeLoadCostTest {
    @Test
    @DisplayName("a rated device costs its rating even while starved, so restoring it is priced honestly")
    void ratedIgnoresAStarvedMeasurement() {
        final FeLoadCost cost = FeLoadCost.rated(256, 0);
        assertEquals(256, cost.budgetCost());
        assertEquals(0, cost.reportedDraw(), "the panel must show what it took, which was nothing");
    }

    @Test
    @DisplayName("a switched off rated device is not load")
    void ratedZeroIsFree() {
        assertEquals(0, FeLoadCost.rated(0, 0).budgetCost());
    }

    @Test
    @DisplayName("a consumer prefers its measurement over its nameplate once it has run")
    void consumerPrefersTheMeasurement() {
        assertEquals(40, FeLoadCost.consumer(40, 100).budgetCost());
        assertEquals(40, FeLoadCost.consumer(40, 100).reportedDraw());
    }

    @Test
    @DisplayName("a consumer that has not run yet is costed at its nameplate, not at zero")
    void consumerFallsBackToTheNameplate() {
        assertEquals(100, FeLoadCost.consumer(0, 100).budgetCost());
        assertEquals(0, FeLoadCost.consumer(0, 100).reportedDraw());
    }

    @Test
    @DisplayName("an opaque block is headroom for both, because headroom is all there is to see")
    void opaqueUsesHeadroomForBoth() {
        assertEquals(512, FeLoadCost.opaque(512).budgetCost());
        assertEquals(512, FeLoadCost.opaque(512).reportedDraw());
    }

    @Test
    @DisplayName("negatives cannot reach the controller as a credit")
    void negativesClamp() {
        assertEquals(0, FeLoadCost.rated(-5, -5).budgetCost());
        assertEquals(0, FeLoadCost.opaque(-1).reportedDraw());
    }

    private static final int RATED = 256;

    private static final int HEADROOM_WHEN_EMPTY = 512;
    private static final int DEVICES = 8;

    private static final int HEALTHY_BUDGET = DEVICES * RATED;

    private static final int STARVED_BUDGET = RATED;

    @Test
    @DisplayName("headroom costing ratchets shed devices off permanently; rated costing recovers")
    void headroomCostingRatchetsShedDevicesOffPermanently() {
        final int recoveredWithRatedCost = runRecovery(true);
        final int recoveredWithHeadroomCost = runRecovery(false);

        assertEquals(DEVICES, recoveredWithRatedCost,
                "with honest per device costs the whole branch comes back once supply does");
        assertTrue(recoveredWithHeadroomCost < DEVICES,
                "the old headroom rule has to actually fail here, or this test proves nothing. "
                        + "Recovered " + recoveredWithHeadroomCost + " of " + DEVICES);
    }

    private static int runRecovery(boolean useRatedCost) {
        final PduAutoController controller = new PduAutoController();

        controller.setTuning(new PduAutoController.Tuning(1, 1));

        final Map<Long, Boolean> running = new HashMap<>();
        for (long key = 0; key < DEVICES; key++) {
            running.put(key, Boolean.TRUE);
        }

        long now = 0;

        for (int step = 0; step < 400; step++, now++) {
            final int budget = step < 100 ? STARVED_BUDGET : HEALTHY_BUDGET;
            final List<PduAutoController.Device> devices = new ArrayList<>(DEVICES);
            for (long key = 0; key < DEVICES; key++) {
                final boolean wasRunning = running.getOrDefault(key, Boolean.TRUE);
                final int demand = useRatedCost ? RATED
                        : wasRunning ? RATED : HEADROOM_WHEN_EMPTY;
                devices.add(new PduAutoController.Device(key, FePriority.DEFAULT.ordinal(), demand));
            }

            final PduAutoController.Supply supply = PduAutoController.Supply.flat(budget, true);
            final PduAutoController.Plan plan = controller.decide(devices, supply, now);
            for (long key = 0; key < DEVICES; key++) {
                running.put(key, plan.isRunning(key));
            }
        }

        int up = 0;
        for (long key = 0; key < DEVICES; key++) {
            if (running.get(key)) {
                up++;
            }
        }
        return up;
    }
}
