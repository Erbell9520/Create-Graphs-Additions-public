package com.createstats.power;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduAutoControllerTest {
    private static final int CRITICAL = FePriority.CRITICAL.ordinal();
    private static final int NORMAL = FePriority.NORMAL.ordinal();
    private static final int OPTIONAL = FePriority.OPTIONAL.ordinal();

    private static final PduAutoController.Tuning FAST = new PduAutoController.Tuning(4, 2);

    private static PduAutoController.Device device(long key, int tier, int demand) {
        return new PduAutoController.Device(key, tier, demand);
    }

    private static List<PduAutoController.Device> devices(PduAutoController.Device... ds) {
        return new ArrayList<>(Arrays.asList(ds));
    }

    private static PduAutoController.Supply ample(int budget) {
        return new PduAutoController.Supply(budget, budget, 0, false, true);
    }

    private static PduAutoController controller() {
        final PduAutoController controller = new PduAutoController();
        controller.setTuning(FAST);
        return controller;
    }

    @Test
    void aDeviceHeldOnlyByTheForecastIsNotBlamedOnGeneration() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 500));

        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);

        final PduAutoController.Supply dusk = new PduAutoController.Supply(1000, 200, 0, false, true,
                false, 1000, 0, false, false, 800);
        final PduAutoController.Plan held = c.decide(branch, dusk, 10L);
        assertFalse(held.isRunning(1L), "the forecast must still hold it: naming changes no decision");
        assertEquals(PduReason.SUPPLY_FALLING, held.reasonFor(1L));
        assertTrue(PduReason.SUPPLY_FALLING.isShed());
    }

    @Test
    void aDeviceThatWouldNotFitAnywayIsStillOverBudget() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 1500));
        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);

        final PduAutoController.Supply dusk = new PduAutoController.Supply(1000, 200, 0, false, true,
                false, 1000, 0, false, false, 800);
        assertEquals(PduReason.OVER_BUDGET, c.decide(branch, dusk, 10L).reasonFor(1L));

        final PduAutoController c2 = controller();
        final List<PduAutoController.Device> small = devices(device(1L, NORMAL, 500));
        c2.decide(small, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertEquals(PduReason.OVER_BUDGET, c2.decide(small,
                new PduAutoController.Supply(1000, 200, 0, false, true, false, 1000, 0, false, false),
                10L).reasonFor(1L));
    }

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

        final PduAutoController.Plan plan = c.decide(devices(
                device(1L, OPTIONAL, 10),
                device(2L, CRITICAL, 500)), ample(500), 0L);

        assertTrue(plan.isRunning(2L), "CRITICAL takes the budget first");
        assertFalse(plan.isRunning(1L));
    }

    @Test
    void aMaxedTierShedsTheHungriestSoTheMostDevicesStayAlive() {
        final PduAutoController c = controller();

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

        final PduAutoController.Plan plan = c.decide(devices(device(1L, NORMAL, 10)), ample(1000), 0L);
        assertTrue(plan.isRunning(1L));
    }

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

        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertFalse(c.isRunning(1L));
        c.decide(branch, ample(1000), 10L);
        assertTrue(c.isRunning(1L), "restored once the dwell elapsed");

        final PduAutoController.Plan plan =
                c.decide(branch, new PduAutoController.Supply(1000, 1000, 0, true, true), 11L);
        assertFalse(plan.isRunning(1L), "a floor trip must not wait out the minimum switching time");
    }

    @Test
    void aDeviceSittingExactlyOnTheBoundaryDoesNotFlapBetweenSteps() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 100));

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

        for (long now = 1; now < 4; now++) {
            assertFalse(c.decide(branch, ample(1000), now).isRunning(1L),
                    "restored at step " + now + ", inside the dwell");
        }
        assertTrue(c.decide(branch, ample(1000), 4L).isRunning(1L), "restored once the dwell elapsed");
    }

    @Test
    void aCriticalDeviceComesBackOnGenerationAloneWithoutWaitingForASurplusSignal() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, CRITICAL, 100));

        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertFalse(c.isRunning(1L), "shed while there was genuinely nothing to give");

        final PduAutoController.Supply rationing =
                new PduAutoController.Supply(5_000, 5_000, 0, false, false);
        final PduAutoController.Plan plan = c.decide(branch, rationing, 10L);
        assertTrue(plan.isRunning(1L), "generation covers it, so critical must come back");
        assertEquals(PduReason.RUNNING, plan.reasonFor(1L));
    }

    @Test
    void anOrdinaryDeviceStillWaitsForSurplusWhileCriticalDoesNot() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(
                device(1L, CRITICAL, 100),
                device(2L, NORMAL, 100));

        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        final PduAutoController.Plan plan =
                c.decide(branch, new PduAutoController.Supply(5_000, 5_000, 0, false, false), 10L);

        assertTrue(plan.isRunning(1L), "critical returns");
        assertFalse(plan.isRunning(2L), "normal waits for a surplus");
        assertEquals(PduReason.AWAITING_SUPPLY, plan.reasonFor(2L));
    }

    @Test
    void aCriticalDeviceThatDoesNotFitGenerationIsStillHeld() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, CRITICAL, 9_000));

        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        final PduAutoController.Plan plan =
                c.decide(branch, new PduAutoController.Supply(1_000, 1_000, 0, false, false), 10L);

        assertFalse(plan.isRunning(1L));
        assertEquals(PduReason.OVER_BUDGET, plan.reasonFor(1L));
    }

    @Test
    void aRestoreNeedsSurplusEvenWhenTheBudgetWouldCoverIt() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 100));

        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertFalse(c.isRunning(1L));

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

        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertEquals(0, c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 1L)
                .admitted().size());

        final PduAutoController.Plan first = c.decide(branch, ample(1000), 4L);
        assertEquals(1, first.admitted().size(), "more than one device restored on the same step");

        assertEquals(1, c.decide(branch, ample(1000), 5L).admitted().size(),
                "a second device was restored inside the restore interval");

        PduAutoController.Plan plan = first;
        for (long now = 6; now < 60; now++) {
            plan = c.decide(branch, ample(1000), now);
        }
        assertEquals(3, plan.admitted().size(), "the whole branch should recover eventually");
    }

    @Test
    void aRefilledBufferDoesNotPullAShedDeviceBackIntoAPulse() {
        final PduAutoController c = controller();

        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 1000));

        assertTrue(c.decide(branch, new PduAutoController.Supply(1400, 600, 0, false, true), 0L)
                .isRunning(1L));

        assertFalse(c.decide(branch, new PduAutoController.Supply(700, 600, 0, false, true), 10L)
                .isRunning(1L));

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

        assertTrue(c.decide(branch, new PduAutoController.Supply(1600, 1600, 100, false, true), 10L)
                .isRunning(1L), "a genuine rise in generation must restore the device");
    }

    @Test
    void theBurstMarginIsRequiredToRestoreButNotToKeepRunning() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 100));

        final PduAutoController.Supply tight =
                new PduAutoController.Supply(100, 100, 10, false, true);
        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        for (long now = 4; now < 40; now++) {
            assertFalse(c.decide(branch, tight, now).isRunning(1L),
                    "restored without clearing the burst margin at step " + now);
        }

        final PduAutoController fresh = controller();
        assertTrue(fresh.decide(branch, tight, 0L).isRunning(1L),
                "a running device that fits the run budget exactly should stay running");
    }

    @Test
    void aLockedOffDeviceNeverRunsAndIsNotCountedAsDemand() {
        final PduAutoController c = controller();

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

        assertTrue(plan.isRunning(1L), "force on outranks the reserve floor");
        assertTrue(plan.isRunning(2L), "and Critical still draws below the floor");
        assertFalse(plan.isRunning(3L), "while an unlocked Optional is still shed");
    }

    @Test
    void lockedOnDemandIsChargedBeforeTheOptimizerSpendsTheBudget() {
        final PduAutoController c = controller();

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

        assertTrue(c.decide(devices(device(1L, NORMAL, 10)), supply, 1L).isRunning(1L),
                "clearing a lock should hand the device back to the controller, not strand it");
    }

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

        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        final PduAutoController.Plan held = c.decide(branch, ample(10_000), 1L);

        assertFalse(held.isRunning(1L));
        assertEquals(PduReason.AWAITING_DWELL, held.reasonFor(1L));
        assertFalse(PduReason.AWAITING_DWELL.isShed(), "holding is a transient, not a shed verdict");

        assertEquals(PduReason.RUNNING, c.decide(branch, ample(10_000), 4L).reasonFor(1L));
    }

    @Test
    void aDeviceThatFitsButHasNoSpareSupplyBlamesTheSupplyNotTheBudget() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 100));

        c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);

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

        final PduAutoController.Plan generationShort =
                c.decide(branch, new PduAutoController.Supply(100, 100, 0, false, true, false), 0L);
        assertEquals(PduReason.OVER_BUDGET, generationShort.reasonFor(2L));

        final PduAutoController other = controller();
        final PduAutoController.Plan wireShort =
                other.decide(branch, new PduAutoController.Supply(100, 100, 0, false, true, true), 0L);
        assertEquals(PduReason.OVER_CAPACITY, wireShort.reasonFor(2L));
        assertFalse(wireShort.isRunning(2L), "either way the device is still shed");
    }

    private static PduAutoController.Supply nearlySpent(int runBudget, int criticalBudget) {
        return new PduAutoController.Supply(runBudget, runBudget, 0, false, true, false,
                criticalBudget, 1, true, false);
    }

    private static PduAutoController.Supply healthyReserve(int budget, int depth) {
        return new PduAutoController.Supply(budget, budget, 0, false, true, false,
                budget, depth, false, false);
    }

    @Test
    void criticalSpendsTheLastOfTheReserveAndTheOtherTiersDoNot() {
        final PduAutoController c = controller();
        final PduAutoController.Plan plan = c.decide(devices(
                device(1L, CRITICAL, 500),
                device(2L, NORMAL, 150)), nearlySpent(200, 1_000), 0L);

        assertTrue(plan.isRunning(1L), "Critical is funded by what is left in the banks");
        assertFalse(plan.isRunning(2L), "Normal is held to what generation alone sustains");
        assertEquals(PduReason.CORE_RESERVE, plan.reasonFor(2L),
                "and is told the reserve is being held, not that generation is short");
    }

    @Test
    void aNearlySpentReserveBlamesTheReserveRatherThanTheGenerator() {
        final PduAutoController shortOfSupply = controller();
        assertEquals(PduReason.OVER_BUDGET, shortOfSupply.decide(devices(
                device(1L, NORMAL, 500)),
                new PduAutoController.Supply(100, 100, 0, false, true), 0L).reasonFor(1L));

        final PduAutoController holdingCore = controller();
        assertEquals(PduReason.CORE_RESERVE, holdingCore.decide(devices(
                device(1L, NORMAL, 500)), nearlySpent(100, 5_000), 0L).reasonFor(1L));
    }

    @Test
    void criticalThatStillDoesNotFitIsToldItIsAShortageNotAHold() {
        final PduAutoController c = controller();
        final PduAutoController.Plan plan = c.decide(devices(
                device(1L, CRITICAL, 9_000)), nearlySpent(100, 1_000), 0L);
        assertFalse(plan.isRunning(1L));
        assertEquals(PduReason.OVER_BUDGET, plan.reasonFor(1L));
    }

    @Test
    void nothingBelowCriticalIsRestoredWhileTheReserveIsNearlySpent() {
        final PduAutoController c = controller();

        c.decide(devices(device(1L, NORMAL, 100)),
                new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertFalse(c.isRunning(1L));

        final PduAutoController.Plan plan = c.decide(devices(device(1L, NORMAL, 100)),
                nearlySpent(10_000, 10_000), 20L);
        assertFalse(plan.isRunning(1L), "the core is not spent bringing an ordinary load back");
        assertEquals(PduReason.CORE_RESERVE, plan.reasonFor(1L));
    }

    @Test
    void criticalIsStillRestoredWhileTheReserveIsNearlySpent() {
        final PduAutoController c = controller();
        c.decide(devices(device(1L, CRITICAL, 100)),
                new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertFalse(c.isRunning(1L));

        assertTrue(c.decide(devices(device(1L, CRITICAL, 100)), nearlySpent(10_000, 10_000), 20L)
                .isRunning(1L), "Critical is the one tier the core is being held for");
    }

    @Test
    void aHealthyReserveWithholdsNothing() {
        final PduAutoController c = controller();
        final PduAutoController.Plan plan = c.decide(devices(
                device(1L, CRITICAL, 300),
                device(2L, NORMAL, 300),
                device(3L, OPTIONAL, 300)), healthyReserve(2_000, 4), 0L);
        assertEquals(3, plan.admitted().size(), "a healthy cascade funds the whole branch");
    }

    @Test
    void theCriticalBudgetCanNeverBeSmallerThanTheOrdinaryOne() {
        final PduAutoController.Supply supply =
                new PduAutoController.Supply(900, 900, 0, false, true, false, 100, 2, false, false);
        assertEquals(900, supply.criticalBudget());
        assertEquals(900, supply.budgetFor(CRITICAL));
        assertEquals(900, supply.budgetFor(NORMAL));
    }

    @Test
    void anOlderSupplyWithNoCascadeBehavesExactlyAsItDid() {
        final PduAutoController.Supply legacy =
                new PduAutoController.Supply(500, 300, 10, false, true);
        assertEquals(500, legacy.criticalBudget(), "one budget for everyone");
        assertEquals(0, legacy.reserveDepth());
        assertFalse(legacy.reserveLow());
        assertFalse(legacy.supplyShock());
    }

    private static PduAutoController.Supply shock(int budget, boolean surplus) {
        return new PduAutoController.Supply(budget, budget, 0, false, surplus, false,
                budget, 2, false, true);
    }

    private static PduAutoController shedAndStartDwell(PduAutoController c, long key) {
        c.decide(devices(device(key, NORMAL, 100)),
                new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertFalse(c.isRunning(key), "precondition: the device is shed, with a flip recorded at 0");
        return c;
    }

    @Test
    void aSupplyChangeIsActedOnWithoutWaitingOutTheDwell() {
        final PduAutoController c = shedAndStartDwell(controller(), 1L);
        assertTrue(c.decide(devices(device(1L, NORMAL, 100)), shock(10_000, true), 1L).isRunning(1L),
                "a real topology change is acted on at once");
    }

    @Test
    void withoutAShockTheDwellStillHolds() {
        final PduAutoController c = shedAndStartDwell(controller(), 1L);
        final PduAutoController.Plan held =
                c.decide(devices(device(1L, NORMAL, 100)), ample(10_000), 1L);
        assertFalse(held.isRunning(1L), "a mere measurement dip waits out the anti-flap timer");
        assertEquals(PduReason.AWAITING_DWELL, held.reasonFor(1L));
    }

    @Test
    void aShockShedsPromptlyOnceADeviceHasAFlipOnRecord() {
        final PduAutoController c = shedAndStartDwell(controller(), 1L);
        assertTrue(c.decide(devices(device(1L, NORMAL, 100)), shock(10_000, true), 1L).isRunning(1L));

        assertFalse(c.decide(devices(device(1L, NORMAL, 100)), shock(0, false), 2L).isRunning(1L),
                "the generator is gone and the panel says so this tick, not in three seconds");
    }

    @Test
    void aNewGeneratorDoesNotWaitOutTheRestoreInterval() {
        for (boolean withShock : List.of(false, true)) {
            final PduAutoController c = controller();
            final List<PduAutoController.Device> branch =
                    devices(device(1L, NORMAL, 100), device(2L, NORMAL, 100));
            c.decide(branch, new PduAutoController.Supply(0, 0, 0, false, false), 0L);

            final PduAutoController.Plan first = c.decide(branch, ample(10_000), 5L);
            assertEquals(1, first.admitted().size(), "staged restoration, one device per interval");
            assertEquals(PduReason.STAGED, first.reasonFor(2L));

            final PduAutoController.Plan second =
                    c.decide(branch, withShock ? shock(10_000, true) : ample(10_000), 6L);
            assertEquals(withShock ? 2 : 1, second.admitted().size(),
                    withShock ? "a real supply event resumes restoring at once"
                            : "otherwise the interval is still being served out");
        }
    }

    @Test
    void aShockStillRespectsTheBudgetAndTheLocks() {
        final PduAutoController c = controller();
        final PduAutoController.Plan plan = c.decide(devices(
                new PduAutoController.Device(1L, NORMAL, 100, FeLock.FORCE_OFF),
                device(2L, NORMAL, 5_000)), shock(200, true), 0L);

        assertFalse(plan.isRunning(1L), "a device the player switched off stays off");
        assertEquals(PduReason.LOCKED_OFF, plan.reasonFor(1L));
        assertFalse(plan.isRunning(2L), "and one that does not fit still does not fit");
    }

    @Test
    void aShockDoesNotOverrideTheReserveFloor() {
        final PduAutoController c = controller();
        final PduAutoController.Supply belowFloorShock = new PduAutoController.Supply(
                10_000, 10_000, 0, true, true, false, 10_000, 3, false, true);
        final PduAutoController.Plan plan = c.decide(devices(
                device(1L, CRITICAL, 100),
                device(2L, NORMAL, 100)), belowFloorShock, 0L);

        assertTrue(plan.isRunning(1L));
        assertFalse(plan.isRunning(2L), "the floor is a safety layer, not a budget");
        assertEquals(PduReason.BELOW_FLOOR, plan.reasonFor(2L));
    }

    @Test
    void switchingHistoryIsForgottenOnceADeviceHasBeenGoneLongerThanTheMemory() {
        final PduAutoController c = controller();
        final int memory = c.tuning().forgetTicks();

        c.decide(devices(device(1L, NORMAL, 100)),
                new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertFalse(c.isRunning(1L));

        c.decide(devices(device(2L, NORMAL, 10)), ample(1000), 1L);
        c.decide(devices(device(2L, NORMAL, 10)), ample(1000), 2L + memory);
        final PduAutoController.Plan back = c.decide(devices(device(1L, NORMAL, 10), device(2L, NORMAL, 10)),
                ample(1000), 3L + memory);
        assertTrue(back.isRunning(1L), "a forgotten device restored at once on ample supply: no dwell owed");
    }

    @Test
    void aBriefAbsenceKeepsADevicesState() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> both = devices(device(1L, NORMAL, 600), device(2L, NORMAL, 600));
        c.decide(both, new PduAutoController.Supply(700, 700, 0, false, false), 0L);
        assertTrue(c.isRunning(1L));
        assertFalse(c.isRunning(2L), "only one 600 fits 700");

        c.decide(devices(), new PduAutoController.Supply(700, 700, 0, false, false), 1L);
        final PduAutoController.Plan back = c.decide(both, new PduAutoController.Supply(700, 700, 0, false, false), 2L);
        assertTrue(back.isRunning(1L), "the running device is still running after a one-tick gap");
        assertFalse(back.isRunning(2L), "the shed device is still shed: the gap did not make it a newcomer");
    }

    @Test
    void anEmptyBranchDecidesToNothingAndForgetsOnlyOnceTheMemoryRunsOut() {
        final PduAutoController c = controller();
        c.decide(devices(device(1L, NORMAL, 100)),
                new PduAutoController.Supply(0, 0, 0, false, false), 0L);
        assertFalse(c.isRunning(1L));

        final PduAutoController.Plan plan = c.decide(devices(), ample(1000), 1L);
        assertEquals(0, plan.admitted().size());
        assertEquals(0, plan.admittedDemand());
        assertFalse(c.isRunning(1L), "inside the memory a device keeps its state across an empty branch");

        c.decide(devices(), ample(1000), 2L + c.tuning().forgetTicks());
        assertTrue(c.isRunning(1L), "past the memory the history is empty, and an empty history admits");
    }

    @Test
    void aNewcomerNeverPushesARunningDeviceOff() {
        for (boolean surplus : new boolean[] {false, true}) {
            final PduAutoController c = controller();
            final PduAutoController.Supply tight = new PduAutoController.Supply(1000, 1000, 100, false, surplus);
            final List<PduAutoController.Device> before = devices(device(1L, NORMAL, 400), device(2L, NORMAL, 500));
            for (long t = 0; t < 20; t++) {
                c.decide(before, tight, t);
            }
            final List<PduAutoController.Device> after = devices(device(1L, NORMAL, 400), device(2L, NORMAL, 500),
                    device(3L, NORMAL, 150));
            final PduAutoController.Plan plan = c.decide(after, tight, 20L);
            assertTrue(plan.isRunning(1L));
            assertTrue(plan.isRunning(2L), "the 500 that was running keeps running, surplus " + surplus);
            assertFalse(plan.isRunning(3L), "the newcomer is not restored into room it does not have");
            assertTrue(plan.reasonFor(3L).isShed());
        }
    }

    @Test
    void aShedDeviceIsNeverRestoredBySwappingOutARunningOneOfItsTier() {
        final PduAutoController c = controller();

        final List<PduAutoController.Device> pair = devices(device(2L, NORMAL, 500), device(3L, NORMAL, 400));
        c.decide(pair, new PduAutoController.Supply(1000, 1000, 100, false, false), 0L);
        final List<PduAutoController.Device> branch = devices(device(1L, NORMAL, 150), device(2L, NORMAL, 500),
                device(3L, NORMAL, 400));
        assertFalse(c.decide(branch, new PduAutoController.Supply(1000, 1000, 100, false, false), 1L).isRunning(1L));

        final PduAutoController.Supply room = new PduAutoController.Supply(1000, 1000, 100, false, true);
        for (long t = 2; t < 40; t++) {
            final PduAutoController.Plan plan = c.decide(branch, room, t);
            assertTrue(plan.isRunning(2L) && plan.isRunning(3L), "the running pair is never swapped, at step " + t);
            assertFalse(plan.isRunning(1L), "the 150 has no room beside them, at step " + t);
        }
    }

    @Test
    void aShedCriticalDeviceStillOutranksARunningNormalOne() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> branch = devices(device(1L, CRITICAL, 300), device(2L, NORMAL, 100));
        final PduAutoController.Plan lean = c.decide(branch, new PduAutoController.Supply(200, 200, 0, false, false), 0L);
        assertFalse(lean.isRunning(1L), "300 of Critical does not fit 200");
        assertTrue(lean.isRunning(2L), "the 100 does");
        final PduAutoController.Plan plan = c.decide(branch,
                new PduAutoController.Supply(350, 350, 0, false, true), 10L);
        assertTrue(plan.isRunning(1L), "the Critical device is restored");
        assertFalse(plan.isRunning(2L), "and the Normal one makes room for it");
    }

    @Test
    void aNewcomerThatFitsComesInThroughTheRestoreGate() {
        final PduAutoController c = controller();
        final List<PduAutoController.Device> one = devices(device(1L, NORMAL, 100));
        c.decide(one, ample(1000), 0L);
        final List<PduAutoController.Device> two = devices(device(1L, NORMAL, 100), device(2L, NORMAL, 100));
        assertTrue(c.decide(two, ample(1000), 1L).isRunning(2L), "room and surplus: restored on its first step");

        final PduAutoController d = controller();
        final PduAutoController.Supply lean = new PduAutoController.Supply(1000, 1000, 0, false, false);
        d.decide(one, lean, 0L);
        final PduAutoController.Plan waiting = d.decide(two, lean, 1L);
        assertFalse(waiting.isRunning(2L));
        assertEquals(PduReason.AWAITING_SUPPLY, waiting.reasonFor(2L));
    }

    @Test
    void aNewCriticalDeviceStillTakesItsPowerAtOnce() {
        final PduAutoController c = controller();
        final PduAutoController.Supply tight = new PduAutoController.Supply(1000, 1000, 100, false, false);
        final List<PduAutoController.Device> before = devices(device(1L, NORMAL, 900));
        for (long t = 0; t < 20; t++) {
            c.decide(before, tight, t);
        }
        final PduAutoController.Plan plan = c.decide(devices(device(1L, NORMAL, 900), device(2L, CRITICAL, 300)),
                tight, 20L);
        assertTrue(plan.isRunning(2L), "a new Critical machine runs at once");
        assertFalse(plan.isRunning(1L), "and the Normal one makes room for it");
    }

    @Test
    void theDecisionIsReproducibleWhenTwoDevicesTieOnTierAndCost() {
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
