package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduRestoreMarginTest {
    private static final int DEVICES = 40;
    private static final int COST = 256;
    private static final int UPKEEP = 51;
    private static final int WIRE = 49_152;

    @Test
    @DisplayName("KI-102: the margin a restore still owes, once the supply above the admit budget is counted")
    void theMarginIsTakenAgainstTheSupplyNotTheCap() {
        assertEquals(927, PduAutoController.restoreMargin(927, 10_240, 10_240));

        assertEquals(0, PduAutoController.restoreMargin(927, 10_240, 10_240 + 927));
        assertEquals(0, PduAutoController.restoreMargin(927, 10_240, 32_717));

        assertEquals(427, PduAutoController.restoreMargin(927, 10_240, 10_740));

        assertEquals(927, PduAutoController.restoreMargin(927, 10_240, 0));
        assertEquals(0, PduAutoController.restoreMargin(0, 10_240, 0));
        assertEquals(0, PduAutoController.restoreMargin(-5, 10_240, 10_240));
    }

    @Test
    @DisplayName("KI-102: the 13:21 rig restores its four shed devices once the answer carries the reserve's spare")
    void theRigsFourShedDevicesComeBack() {
        assertEquals(4, replay(false), "as shipped: four stay shed for ever, the screenshot");
        assertEquals(0, replay(true), "with the spare: all forty run");
    }

    @Test
    @DisplayName("KI-102: a pool at the ask still needs the margin, so the deadband is kept where it matters")
    void aPoolAtTheAskStillHoldsTheLastDevices() {
        final List<PduAutoController.Device> branch = rig();
        final PduAutoController c = new PduAutoController();
        c.decide(branch, flat(DEVICES * COST - 4 * COST), 0L);

        final int ask = DEVICES * COST + UPKEEP;
        int shed = 0;
        for (long now = 1; now <= 2_400; now++) {
            shed = step(c, branch, ask, true, now);
        }
        assertEquals(4, shed, "no spare, so no restore: the margin is still a deadband");
    }

    @Test
    @DisplayName("KI-106: a device that fits the budget and not the margin is held for headroom, not blamed on generation")
    void aHeadroomHoldIsNamed() {
        final PduAutoController c = new PduAutoController();
        final List<PduAutoController.Device> branch = new ArrayList<>(List.of(
                new PduAutoController.Device(1L, FePriority.NORMAL.ordinal(), 100)));
        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        final PduAutoController.Plan plan = c.decide(branch,
                new PduAutoController.Supply(100, 100, 10, false, true), 200L);
        assertFalse(plan.isRunning(1L));
        assertEquals(PduReason.HEADROOM, plan.reasonFor(1L));
        assertTrue(PduReason.HEADROOM.isShed());

        final PduAutoController c2 = new PduAutoController();
        c2.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertEquals(PduReason.OVER_BUDGET, c2.decide(branch,
                new PduAutoController.Supply(50, 50, 10, false, true), 200L).reasonFor(1L));
    }

    @Test
    @DisplayName("KI-105: load near the wire comes back when generation clears the wire by the margin")
    void loadNearTheWireIsRestored() {
        final int wire = 16_384;
        final int margin = 2_000;
        assertEquals(0, PduAutoController.restoreMargin(margin, wire, 20_000),
                "generation 3,616 above the wire covers the whole margin");
        final PduAutoController c = new PduAutoController();
        final List<PduAutoController.Device> branch = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            branch.add(new PduAutoController.Device(100L + i, FePriority.NORMAL.ordinal(), 1_000));
        }
        c.decide(branch, flat(14_000), 0L);
        int running = 0;
        for (long now = 1; now <= 1_200; now++) {
            final int useMargin = PduAutoController.restoreMargin(margin, wire, 20_000);
            running = c.decide(branch, new PduAutoController.Supply(wire, wire, useMargin, false, true),
                    now).admitted().size();
        }
        assertEquals(16, running, "all sixteen come back under the wire");

        final PduAutoController old = new PduAutoController();
        old.decide(branch, flat(14_000), 0L);
        for (long now = 1; now <= 1_200; now++) {
            running = old.decide(branch, new PduAutoController.Supply(wire, wire, margin, false, true),
                    now).admitted().size();
        }
        assertEquals(14, running);
    }

    private static List<PduAutoController.Device> rig() {
        final List<PduAutoController.Device> devices = new ArrayList<>();
        for (int i = 0; i < DEVICES; i++) {
            devices.add(new PduAutoController.Device(1_000L + i, FePriority.HIGH.ordinal(), COST,
                    FeLock.NONE, FePriority.NORMAL.ordinal()));
        }
        return devices;
    }

    private static PduAutoController.Supply flat(int budget) {
        return new PduAutoController.Supply(budget, budget, 0, false, false, false, budget, 0, false, false, 0);
    }

    private static int replay(boolean withSpare) {
        final List<PduAutoController.Device> branch = rig();
        final PduAutoController c = new PduAutoController();
        c.decide(branch, flat(DEVICES * COST - 4 * COST), 0L);
        int shed = 0;
        for (long now = 1; now <= 2_400; now++) {
            shed = step(c, branch, 32_768, withSpare, now);
        }
        return shed;
    }

    private static int step(PduAutoController c, List<PduAutoController.Device> branch, int pool,
                            boolean withSpare, long now) {
        int running = 0;
        for (PduAutoController.Device d : branch) {
            if (c.isRunning(d.key())) {
                running += d.demand();
            }
        }
        final int total = DEVICES * COST;
        final FeBoardDeal.Request ask = new FeBoardDeal.Request(1L, 0, running + UPKEEP, total - running);
        final FeBoardDeal.Answer answer = FeBoardDeal.split(2L, false,
                new FeBoardDeal.Offer(pool, pool, 0, FeReserveReport.Stance.SURPLUS, 72, false),
                List.of(ask), new int[] {WIRE}, null, now + 10).get(0);
        final int dealSupply = answer.total();
        final int admit = Math.min(WIRE, Math.max(0, dealSupply - UPKEEP));
        final int margin = Math.round(dealSupply * 0.10f);
        final int useMargin = withSpare ? PduAutoController.restoreMargin(margin, admit, (long) admit + answer.spare())
                : margin;
        final PduAutoController.Plan plan = c.decide(branch, new PduAutoController.Supply(admit, admit, useMargin,
                false, answer.restore() > 0, false, admit, 0, false, false, 0), now);
        return DEVICES - plan.admitted().size();
    }
}
