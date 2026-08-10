package com.createstats.power;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [1] */
class PduAutoControllerTest {

    private static final int CRITICAL = FePriority.CRITICAL.ordinal();
    private static final int NORMAL = FePriority.NORMAL.ordinal();
    private static final int OPTIONAL = FePriority.OPTIONAL.ordinal();

    /** A short dwell, so a test asserts about behaviour rather than simulating three seconds. */
    private static final PduAutoController.Tuning FAST = new PduAutoController.Tuning(4, 2);

    private static PduAutoController.Device device(long key, int tier, int demand) {
        return new PduAutoController.Device(key, tier, demand);
    }

    private static List<PduAutoController.Device> devices(PduAutoController.Device... ds) {
        return new ArrayList<>(Arrays.asList(ds));
    }

    /** Generation alone sustains everything, so nothing is ever shed for want of supply. */
    private static PduAutoController.Supply ample(int budget) {
        return new PduAutoController.Supply(budget, budget, 0, false, true);
    }

    private static PduAutoController controller() {
        final PduAutoController controller = new PduAutoController();
        controller.setTuning(FAST);
        return controller;
    }

    // budget: supply against demand

    @Test
    void devicesAreAdmittedDownTheListUntilTheBudgetRunsOut() {
        final PduAutoController c = controller();
        final PduAutoController.Plan plan = c.decide(devices(
                device(1L, CRITICAL, 400),
                device(2L, NORMAL, 400),
                device(3L, OPTIONAL, 400)), ample(900), 0L);

        assertEquals(2, plan.admitted().size(), "only two 400 FE/t devices fit in a 900 budget");
        assertTrue(plan.isRunning(1L));
        assertTrue(plan.isRunning(2L));
        assertFalse(plan.isRunning(3L), "the lowest tier is the one that does not fit");
        assertEquals(800, plan.admittedDemand());
    }

    @Test
    void tierOrderBeatsCostSoCriticalIsNeverShedForACheaperOptional() {
        final PduAutoController c = controller();
        // The OPTIONAL device is far cheaper, so a cost-only sort would admit it and shed CRITICAL.
        final PduAutoController.Plan plan = c.decide(devices(
                device(1L, OPTIONAL, 10),
                device(2L, CRITICAL, 500)), ample(500), 0L);

        assertTrue(plan.isRunning(2L), "CRITICAL takes the budget first");
        assertFalse(plan.isRunning(1L));
    }

    @Test
    void aMaxedTierShedsTheHungriestSoTheMostDevicesStayAlive() {
        final PduAutoController c = controller();
        // [2]
        final PduAutoController.Plan plan = c.decide(devices(
                device(1L, NORMAL, 90),
                device(2L, NORMAL, 30),
                device(3L, NORMAL, 30)), ample(100), 0L);

        assertEquals(2, plan.admitted().size());
        assertTrue(plan.isRunning(2L));
        assertTrue(plan.isRunning(3L));
        assertFalse(plan.isRunning(1L), "the hungriest device in a maxed tier is the one shed");
    }

    @Test
    void aNewDeviceStartsAdmittedRatherThanShed() {
        final PduAutoController c = controller();
        // A block just wired to the branch has no history. It should light up, not sit dark waiting
        // for a restore that needs surplus and a dwell.
        final PduAutoController.Plan plan = c.decide(devices(device(1L, NORMAL, 10)), ample(1000), 0L);
        assertTrue(plan.isRunning(1L));
    }

    // the reserve floor: the CRITICAL ride-through

    @Test
    void belowTheReserveFloorOnlyCriticalDraws() {
        final PduAutoController c = controller();
        final PduAutoController.Supply floorTripped =
                new PduAutoController.Supply(10_000, 10_000, 0, true, true);

        final PduAutoController.Plan plan = c.decide(devices(
                device(1L, CRITICAL, 100),
                device(2L, NORMAL, 10),
                device(3L, OPTIONAL, 10)), floorTripped, 0L);

        assertTrue(plan.isRunning(1L), "CRITICAL keeps drawing on the reserve");
        assertFalse(plan.isRunning(2L), "the floor sheds everything below CRITICAL, budget or not");
        assertFalse(plan.isRunning(3L));
    }

    @Test
    void criticalSurvivesATotalOutageForAsLongAsTheReserveLasts() {
        final PduAutoController c = controller();
        final PduAutoController.Supply outage =
                new PduAutoController.Supply(0, 0, 0, true, false);

        // No generation at all, floor tripped, no surplus: the pessimal case. Over a hundred steps
        // CRITICAL must never be dropped, because the floor exists precisely to hold it up.
        for (long now = 0; now < 100; now++) {
            final PduAutoController.Plan plan = c.decide(devices(
                    device(1L, CRITICAL, 500),
                    device(2L, OPTIONAL, 500)), outage, now);
            assertTrue(plan.isRunning(1L), "CRITICAL dropped at step " + now);
            assertFalse(plan.isRunning(2L), "OPTIONAL admitted during an outage at step " + now);
        }
    }

    @Test
    void aFloorTripShedsImmediatelyWithoutWaitingOutTheDwell() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 100));

        // Flip it to running at step 10, so the dwell is live and would otherwise block a change.
        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertFalse(c.isRunning(1L));
        c.decide(branch, ample(1000), 10L);
        assertTrue(c.isRunning(1L), "restored once the dwell elapsed");

        // One tick later, well inside the dwell, the floor trips. Waiting here would spend the very
        // reserve the floor is protecting, so the safety layer is exempt from the dwell.
        final PduAutoController.Plan plan =
                c.decide(branch, new PduAutoController.Supply(1000, 1000, 0, true, true), 11L);
        assertFalse(plan.isRunning(1L), "a floor trip must not wait out the minimum switching time");
    }

    // hysteresis and the minimum switching time

    @Test
    void aDeviceSittingExactlyOnTheBoundaryDoesNotFlapBetweenSteps() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 100));
        // [3]
        final PduAutoController.Supply onTheLine =
                new PduAutoController.Supply(100, 100, 10, false, true);

        boolean previous = true;
        int flips = 0;
        for (long now = 0; now < 200; now++) {
            final boolean running = c.decide(branch, onTheLine, now).isRunning(1L);
            if (running != previous) {
                flips++;
            }
            previous = running;
        }
        assertEquals(0, flips, "a device on the admit boundary flapped " + flips + " times");
        assertTrue(previous, "and it should have settled running, since it fits the run budget");
    }

    @Test
    void aShedDeviceWaitsOutTheDwellBeforeItIsRestored() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 100));

        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertFalse(c.isRunning(1L), "shed with no supply");

        // Supply is back immediately, but the dwell is 4 ticks. Steps 1 to 3 must hold.
        for (long now = 1; now < 4; now++) {
            assertFalse(c.decide(branch, ample(1000), now).isRunning(1L),
                    "restored at step " + now + ", inside the dwell");
        }
        assertTrue(c.decide(branch, ample(1000), 4L).isRunning(1L), "restored once the dwell elapsed");
    }

    @Test
    void aRestoreNeedsSurplusEvenWhenTheBudgetWouldCoverIt() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 100));

        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertFalse(c.isRunning(1L));

        // [4]
        final PduAutoController.Supply noSurplus =
                new PduAutoController.Supply(1000, 1000, 0, false, false);
        for (long now = 4; now < 40; now++) {
            assertFalse(c.decide(branch, noSurplus, now).isRunning(1L),
                    "restored without surplus at step " + now);
        }
        assertTrue(c.decide(branch, ample(1000), 40L).isRunning(1L),
                "and it comes back as soon as there is surplus");
    }

    @Test
    void onlyOneDeviceIsRestoredPerIntervalSoTheBranchDoesNotPickUpAllAtOnce() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(
                device(1L, NORMAL, 10),
                device(2L, NORMAL, 10),
                device(3L, NORMAL, 10));

        // Shed all three.
        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertEquals(0, c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 1L)
                .admitted().size());

        // Supply returns in abundance. Cold load pickup is a real failure mode, so the controller
        // must stage the restoration rather than admitting all three on the first eligible step.
        final PduAutoController.Plan first = c.decide(branch, ample(1000), 4L);
        assertEquals(1, first.admitted().size(), "more than one device restored on the same step");

        // The restore interval is 2, so the very next step must not add another.
        assertEquals(1, c.decide(branch, ample(1000), 5L).admitted().size(),
                "a second device was restored inside the restore interval");

        // Given enough steps all three come back.
        PduAutoController.Plan plan = first;
        for (long now = 6; now < 60; now++) {
            plan = c.decide(branch, ample(1000), now);
        }
        assertEquals(3, plan.admitted().size(), "the whole branch should recover eventually");
    }

    // the pulse regression: why the two budgets are asymmetric

    @Test
    void aRefilledBufferDoesNotPullAShedDeviceBackIntoAPulse() {
        final PduAutoController c = controller();
        // One device that generation alone cannot sustain: it needs 1000, generation is 600.
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 1000));

        // While it runs, generation plus the reserve covers it, so it is allowed to ride the buffer.
        assertTrue(c.decide(branch, new PduAutoController.Supply(1400, 600, 0, false, true), 0L)
                .isRunning(1L));

        // The reserve drains, the run budget falls under its demand, and it is shed.
        assertFalse(c.decide(branch, new PduAutoController.Supply(700, 600, 0, false, true), 10L)
                .isRunning(1L));

        // [5]
        for (long now = 20; now < 200; now++) {
            assertFalse(c.decide(branch, new PduAutoController.Supply(1400, 600, 0, false, true), now)
                    .isRunning(1L), "the shed device was pulled back by the reserve at step " + now);
        }
    }

    @Test
    void realGenerationComingBackDoesRestoreTheDeviceSoItIsNotLatchedOff() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 1000));

        c.decide(branch, new PduAutoController.Supply(700, 600, 0, false, true), 0L);
        assertFalse(c.isRunning(1L), "shed because generation cannot sustain it");

        // Generation itself rises above the demand plus the burst margin. That is a real, durable
        // improvement rather than a full buffer, so the device is expected back.
        assertTrue(c.decide(branch, new PduAutoController.Supply(1600, 1600, 100, false, true), 10L)
                .isRunning(1L), "a genuine rise in generation must restore the device");
    }

    @Test
    void theBurstMarginIsRequiredToRestoreButNotToKeepRunning() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 100));

        // [6]
        final PduAutoController.Supply tight =
                new PduAutoController.Supply(100, 100, 10, false, true);
        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        for (long now = 4; now < 40; now++) {
            assertFalse(c.decide(branch, tight, now).isRunning(1L),
                    "restored without clearing the burst margin at step " + now);
        }

        // But a device already running is judged without the margin, so an existing load that fits
        // the run budget exactly is not shed just to manufacture headroom.
        final PduAutoController fresh = controller();
        assertTrue(fresh.decide(branch, tight, 0L).isRunning(1L),
                "a running device that fits the run budget exactly should stay running");
    }

    // player overrides

    @Test
    void aLockedOffDeviceNeverRunsAndIsNotCountedAsDemand() {
        final PduAutoController c = controller();
        // [7]
        final PduAutoController.Plan plan = c.decide(devices(
                new PduAutoController.Device(1L, NORMAL, 10_000, FeLock.FORCE_OFF),
                device(2L, NORMAL, 40),
                device(3L, NORMAL, 40)), ample(100), 0L);

        assertFalse(plan.isRunning(1L), "a locked-off device must never be admitted");
        assertTrue(plan.isRunning(2L));
        assertTrue(plan.isRunning(3L));
        assertEquals(80, plan.admittedDemand(), "a switched-off machine is not a load");
    }

    @Test
    void aLockedOffDeviceStaysOffEvenWithPowerToSpare() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(
                new PduAutoController.Device(1L, CRITICAL, 10, FeLock.FORCE_OFF));

        // Critical tier, trivial demand, endless supply, hundreds of steps. None of that matters:
        // the lock is a statement about intent, not about scarcity.
        for (long now = 0; now < 100; now++) {
            assertFalse(c.decide(branch, ample(100_000), now).isRunning(1L),
                    "locked-off device ran at step " + now);
        }
    }

    @Test
    void aLockedOnDeviceIsAdmittedEvenWhenItDoesNotFitTheBudget() {
        final PduAutoController c = controller();
        final PduAutoController.Plan plan = c.decide(devices(
                new PduAutoController.Device(1L, OPTIONAL, 900, FeLock.FORCE_ON)), ample(100), 0L);

        assertTrue(plan.isRunning(1L), "force on overrules the budget");
        assertEquals(900, plan.admittedDemand(), "and its cost is charged against the budget");
    }

    @Test
    void aLockedOnDeviceSurvivesTheReserveFloorEvenAtTheLowestTier() {
        final PduAutoController c = controller();
        final PduAutoController.Supply floorTripped =
                new PduAutoController.Supply(0, 0, 0, true, false);

        final PduAutoController.Plan plan = c.decide(devices(
                new PduAutoController.Device(1L, OPTIONAL, 100, FeLock.FORCE_ON),
                device(2L, CRITICAL, 100),
                device(3L, OPTIONAL, 100)), floorTripped, 0L);

        // An explicit instruction outranks the heuristic about what is worth protecting. The
        // unlocked Optional device is still shed, so the floor is doing its job around the lock.
        assertTrue(plan.isRunning(1L), "force on outranks the reserve floor");
        assertTrue(plan.isRunning(2L), "and Critical still draws below the floor");
        assertFalse(plan.isRunning(3L), "while an unlocked Optional is still shed");
    }

    @Test
    void lockedOnDemandIsChargedBeforeTheOptimizerSpendsTheBudget() {
        final PduAutoController c = controller();
        // [8]
        final PduAutoController.Plan plan = c.decide(devices(
                new PduAutoController.Device(1L, OPTIONAL, 80, FeLock.FORCE_ON),
                device(2L, NORMAL, 40)), ample(100), 0L);

        assertTrue(plan.isRunning(1L));
        assertFalse(plan.isRunning(2L), "the optimizer must see the budget the lock actually left it");
        assertEquals(80, plan.admittedDemand());
    }

    @Test
    void unlockingADeviceReturnsItToOrdinaryControl() {
        final PduAutoController c = controller();
        final PduAutoController.Supply supply = ample(1000);

        c.decide(devices(new PduAutoController.Device(1L, NORMAL, 10, FeLock.FORCE_OFF)), supply, 0L);
        assertFalse(c.isRunning(1L));

        // The lock is cleared. There is plenty of budget, so it should come straight back rather
        // than being stuck behind a dwell it never earned.
        assertTrue(c.decide(devices(device(1L, NORMAL, 10)), supply, 1L).isRunning(1L),
                "clearing a lock should hand the device back to the controller, not strand it");
    }

    // reasons: the panel explaining itself

    @Test
    void aDeviceThatDoesNotFitSaysSoRatherThanJustGoingDark() {
        final PduAutoController c = controller();
        final PduAutoController.Plan plan = c.decide(devices(
                device(1L, CRITICAL, 100),
                device(2L, OPTIONAL, 100)), ample(100), 0L);

        assertEquals(PduReason.RUNNING, plan.reasonFor(1L));
        assertEquals(PduReason.OVER_BUDGET, plan.reasonFor(2L));
    }

    @Test
    void theFloorAndTheLocksEachNameThemselves() {
        final PduAutoController c = controller();
        final PduAutoController.Plan plan = c.decide(devices(
                device(1L, CRITICAL, 10),
                device(2L, OPTIONAL, 10),
                new PduAutoController.Device(3L, OPTIONAL, 10, FeLock.FORCE_ON),
                new PduAutoController.Device(4L, OPTIONAL, 10, FeLock.FORCE_OFF)),
                new PduAutoController.Supply(10_000, 10_000, 0, true, true), 0L);

        assertEquals(PduReason.RUNNING, plan.reasonFor(1L));
        assertEquals(PduReason.BELOW_FLOOR, plan.reasonFor(2L), "the floor must name itself");
        assertEquals(PduReason.LOCKED_ON, plan.reasonFor(3L));
        assertEquals(PduReason.LOCKED_OFF, plan.reasonFor(4L));
    }

    @Test
    void aDeviceWaitingOutTheDwellReadsAsHoldingRatherThanShed() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 100));

        // [9]
        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        final PduAutoController.Plan held = c.decide(branch, ample(10_000), 1L);

        assertFalse(held.isRunning(1L));
        assertEquals(PduReason.AWAITING_DWELL, held.reasonFor(1L));
        assertFalse(PduReason.AWAITING_DWELL.isShed(), "holding is a transient, not a shed verdict");

        // Once the dwell elapses it comes back and says so.
        assertEquals(PduReason.RUNNING, c.decide(branch, ample(10_000), 4L).reasonFor(1L));
    }

    @Test
    void aDeviceThatFitsButHasNoSpareSupplyBlamesTheSupplyNotTheBudget() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 100));

        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        // Budget is ample, dwell has elapsed, but there is no surplus to restore on.
        final PduAutoController.Plan plan =
                c.decide(branch, new PduAutoController.Supply(10_000, 10_000, 0, false, false), 10L);

        assertFalse(plan.isRunning(1L));
        assertEquals(PduReason.AWAITING_SUPPLY, plan.reasonFor(1L));
    }

    @Test
    void aDeviceWaitingItsTurnInAStagedRestartSaysThatAndNotSomethingElse() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(
                device(1L, NORMAL, 10),
                device(2L, NORMAL, 10));

        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        // Both fit and there is surplus, but staged restoration only lets one back per interval.
        final PduAutoController.Plan plan = c.decide(branch, ample(10_000), 4L);

        assertEquals(1, plan.admitted().size());
        final long waiting = plan.isRunning(1L) ? 2L : 1L;
        assertEquals(PduReason.STAGED, plan.reasonFor(waiting));
    }

    @Test
    void aWireLimitIsBlamedOnTheWireAndNotOnTheGenerator() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(
                device(1L, CRITICAL, 100),
                device(2L, OPTIONAL, 100));

        // [10]
        final PduAutoController.Plan generationShort =
                c.decide(branch, new PduAutoController.Supply(100, 100, 0, false, true, false), 0L);
        assertEquals(PduReason.OVER_BUDGET, generationShort.reasonFor(2L));

        final PduAutoController other = controller();
        final PduAutoController.Plan wireShort =
                other.decide(branch, new PduAutoController.Supply(100, 100, 0, false, true, true), 0L);
        assertEquals(PduReason.OVER_CAPACITY, wireShort.reasonFor(2L));
        assertFalse(wireShort.isRunning(2L), "either way the device is still shed");
    }

    // housekeeping

    @Test
    void switchingHistoryIsForgottenWhenADeviceLeavesTheBranch() {
        final PduAutoController c = controller();

        // Shed device 1, so it carries a shed state and a flip time.
        c.decide(devices(device(1L, NORMAL, 100)),
                new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertFalse(c.isRunning(1L));

        // [11]
        c.decide(devices(device(2L, NORMAL, 10)), ample(1000), 1L);
        assertTrue(c.isRunning(1L), "history for an absent device should have been dropped");

        assertTrue(c.decide(devices(device(1L, NORMAL, 10)), ample(1000), 2L).isRunning(1L));
    }

    @Test
    void anEmptyBranchDecidesToNothingAndClearsItsHistory() {
        final PduAutoController c = controller();
        c.decide(devices(device(1L, NORMAL, 100)),
                new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertFalse(c.isRunning(1L));

        final PduAutoController.Plan plan = c.decide(devices(), ample(1000), 1L);
        assertEquals(0, plan.admitted().size());
        assertEquals(0, plan.admittedDemand());
        assertTrue(c.isRunning(1L), "an empty branch should not keep stale per-device state");
    }

    @Test
    void theDecisionIsReproducibleWhenTwoDevicesTieOnTierAndCost() {
        // [12]
        final PduAutoController a = controller();
        final PduAutoController b = controller();
        final PduAutoController.Supply supply = ample(100);

        final PduAutoController.Plan first = a.decide(devices(
                device(1L, NORMAL, 60),
                device(2L, NORMAL, 60)), supply, 0L);
        final PduAutoController.Plan reversed = b.decide(devices(
                device(2L, NORMAL, 60),
                device(1L, NORMAL, 60)), supply, 0L);

        assertEquals(first.admitted(), reversed.admitted(), "list order changed the decision");
        assertEquals(1, first.admitted().size(), "only one 60 FE/t device fits a 100 budget");
    }
}
