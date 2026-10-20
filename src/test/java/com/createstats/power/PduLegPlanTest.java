package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduLegPlanTest {
    private static final int CEILING = 24_576;
    private static final int LEGS = PduStuds.COUNT;

    private static PduLegPlan.Leg[] allOpen() {
        final PduLegPlan.Leg[] legs = new PduLegPlan.Leg[LEGS];
        for (int i = 0; i < LEGS; i++) {
            legs[i] = PduLegPlan.Leg.open(CEILING);
        }
        return legs;
    }

    private static PduLegPlan.Device dev(long key, int leg, FePriority tier, int demand) {
        return new PduLegPlan.Device(key, leg, tier.ordinal(), demand, FeLock.NONE);
    }

    @Test
    @DisplayName("a branch inside its leg's rating passes through untouched")
    void everythingFittingSurvives() {
        final List<PduLegPlan.Device> devices = List.of(
                dev(1, 0, FePriority.DEFAULT, 256),
                dev(2, 0, FePriority.DEFAULT, 256),
                dev(3, 1, FePriority.DEFAULT, 512));
        final PduLegPlan.Screened s = PduLegPlan.screen(devices, allOpen());
        assertEquals(3, s.survivors().size());
        assertTrue(s.blocked().isEmpty());
        assertFalse(s.isAtCapacity(0));
        assertEquals(1024, s.totalDemand());
    }

    @Test
    @DisplayName("a leg over its rating sheds for capacity, which generation cannot fix")
    void anOverloadedLegShedsForCapacity() {
        final List<PduLegPlan.Device> devices = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            devices.add(dev(i, 0, FePriority.DEFAULT, 5_000));
        }
        final PduLegPlan.Screened s = PduLegPlan.screen(devices, allOpen());
        assertEquals(4, s.survivors().size(), "four fit inside 24,576, the fifth does not");
        assertEquals(2, s.blocked().size());
        for (PduReason reason : s.blocked().values()) {
            assertEquals(PduReason.OVER_CAPACITY, reason,
                    "a wire limit must never be reported as a supply shortage");
        }
        assertTrue(s.isAtCapacity(0));
        assertEquals(30_000, s.legDemand()[0]);
        assertEquals(20_000, s.legAdmitted()[0]);
    }

    @Test
    @DisplayName("one full leg says nothing about the others")
    void legsAreIndependent() {
        final List<PduLegPlan.Device> devices = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            devices.add(dev(i, 0, FePriority.DEFAULT, 5_000));
        }
        devices.add(dev(90, 5, FePriority.DEFAULT, 5_000));
        final PduLegPlan.Screened s = PduLegPlan.screen(devices, allOpen());
        assertTrue(s.isAtCapacity(0));
        assertFalse(s.isAtCapacity(5), "a saturated leg 0 must not implicate leg 5");
        assertTrue(s.survivors().stream().anyMatch(d -> d.key() == 90));
    }

    @Test
    @DisplayName("capacity is judged inside a leg, so tier only competes against the same leg")
    void tierCompetesWithinALegNotAcross() {
        final List<PduLegPlan.Device> devices = List.of(

                dev(1, 0, FePriority.OPTIONAL, 20_000),
                dev(2, 0, FePriority.CRITICAL, 20_000),
                dev(3, 1, FePriority.OPTIONAL, 100));
        final PduLegPlan.Screened s = PduLegPlan.screen(devices, allOpen());

        assertTrue(s.survivors().stream().anyMatch(d -> d.key() == 2));
        assertEquals(PduReason.OVER_CAPACITY, s.blocked().get(1L));

        assertTrue(s.survivors().stream().anyMatch(d -> d.key() == 3));
    }

    @Test
    @DisplayName("an open breaker takes the leg out without calling it shed")
    void anOpenBreakerIsNotAShed() {
        final PduLegPlan.Leg[] legs = allOpen();
        legs[2] = new PduLegPlan.Leg(false, CEILING);
        final List<PduLegPlan.Device> devices = List.of(
                dev(1, 2, FePriority.DEFAULT, 256),
                dev(2, 3, FePriority.DEFAULT, 256));
        final PduLegPlan.Screened s = PduLegPlan.screen(devices, legs);
        assertEquals(PduReason.BREAKER_OPEN, s.blocked().get(1L));
        assertFalse(PduReason.BREAKER_OPEN.isShed(),
                "a switch the player opened is not the board running short");
        assertEquals(1, s.survivors().size());
        assertEquals(2, s.survivors().get(0).key());
    }

    @Test
    @DisplayName("force on is charged against the conductor like anything else")
    void forceOnCannotOutrankTheWire() {
        final List<PduLegPlan.Device> devices = List.of(
                new PduLegPlan.Device(1, 0, FePriority.DEFAULT.ordinal(), 20_000, FeLock.FORCE_ON),
                new PduLegPlan.Device(2, 0, FePriority.DEFAULT.ordinal(), 20_000, FeLock.FORCE_ON));
        final PduLegPlan.Screened s = PduLegPlan.screen(devices, allOpen());

        assertEquals(1, s.survivors().size());
        assertEquals(PduReason.OVER_CAPACITY, s.blocked().get(2L));
    }

    @Test
    @DisplayName("locked on devices take capacity before anything else on their leg")
    void forceOnIsChargedFirst() {
        final List<PduLegPlan.Device> devices = List.of(
                dev(1, 0, FePriority.CRITICAL, 20_000),
                new PduLegPlan.Device(2, 0, FePriority.OPTIONAL.ordinal(), 20_000, FeLock.FORCE_ON));
        final PduLegPlan.Screened s = PduLegPlan.screen(devices, allOpen());
        assertEquals(1, s.survivors().size());
        assertEquals(2, s.survivors().get(0).key(), "the pinned device wins the capacity");
        assertEquals(PduReason.OVER_CAPACITY, s.blocked().get(1L));
    }

    @Test
    @DisplayName("the outcome is stable across repeated ticks, so nothing takes turns being dropped")
    void screeningIsReproducible() {
        final List<PduLegPlan.Device> devices = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            devices.add(dev(i, 0, FePriority.DEFAULT, 5_000));
        }
        final PduLegPlan.Screened first = PduLegPlan.screen(devices, allOpen());
        for (int tick = 0; tick < 50; tick++) {
            final PduLegPlan.Screened again = PduLegPlan.screen(new ArrayList<>(devices), allOpen());
            assertEquals(first.blocked().keySet(), again.blocked().keySet(),
                    "a different device was dropped on tick " + tick);
        }
    }

    @Test
    @DisplayName("a device on a leg this panel does not have is ignored, not blamed")
    void unknownLegsAreDropped() {
        final PduLegPlan.Screened s = PduLegPlan.screen(
                List.of(dev(1, 99, FePriority.DEFAULT, 100), dev(2, -1, FePriority.DEFAULT, 100)),
                allOpen());
        assertTrue(s.survivors().isEmpty());
        assertTrue(s.blocked().isEmpty());
    }

    @Test
    @DisplayName("what survives the screen is what the supply controller then judges")
    void survivorsFeedTheControllerUntouched() {
        final List<PduLegPlan.Device> devices = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            devices.add(dev(i, 0, FePriority.DEFAULT, 5_000));
        }
        final PduLegPlan.Screened s = PduLegPlan.screen(devices, allOpen());

        final PduAutoController controller = new PduAutoController();
        final PduAutoController.Plan plan = controller.decide(
                s.survivors(), PduAutoController.Supply.flat(20_000, true), 0L);
        assertEquals(4, plan.admitted().size());
        assertEquals(20_000, plan.admittedDemand(),
                "the budget is spent only on machines that can actually be reached");
    }
}
