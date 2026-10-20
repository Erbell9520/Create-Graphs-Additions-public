package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UnseenDriverTest {
    private static Pathway line() {
        return Pathway.create(UUID.randomUUID(), "Unseen", 3, UUID.randomUUID())
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{40, 64, 0}, new double[]{100, 64, 0}));
    }

    private static RouteRunner.Sensors sensors(long tick, double s, double speed, boolean depart, double gap) {
        return new RouteRunner.Sensors(tick, s, 64, 0, 1, 0, speed, 0.15, false,
                Double.POSITIVE_INFINITY, false, "", gap, 0, depart, false, false, true, 0, false, 0, 0);
    }

    @Test void fullBrakeUsesTheLearnedBrakeTimeConstantEvenWithFullThrottle() {
        PlantModel p = new PlantModel(12, 2, 0.4, 100, 20, 0);
        assertEquals(10 * Math.exp(-0.05 / 0.4), UnseenTravel.speedAfter(10, 1, 1, p, 0.05), 1e-12);
        assertEquals(12 * (1 - Math.exp(-1)), UnseenTravel.speedAfter(0, 1, 0, p, 2), 1e-12);
        assertEquals(10 * Math.exp(-0.05 / 2), UnseenTravel.speedAfter(10, 0, 0, p, 0.05), 1e-12);
    }

    @Test void partialBrakeHasTheCorrectEquilibriumAndIsIndependentOfStepSize() {
        PlantModel p = new PlantModel(12, 2, 0.4, 100, 20, 0);
        double one = UnseenTravel.speedAfter(7, 0.8, 0.25, p, 1);
        double many = 7;
        for (int i = 0; i < 20; i++) many = UnseenTravel.speedAfter(many, 0.8, 0.25, p, 0.05);
        assertEquals(one, many, 1e-12);
        assertEquals(3.6, UnseenTravel.speedAfter(7, 0.8, 0.25, p, 100), 1e-10);
        assertEquals(0, UnseenTravel.speedAfter(-5, -1, 2, p, 0.05));
    }

    @Test void startupWaitIsBoundedAndAcceptsAReadyVehicleImmediately() {
        ReturnStartup startup = new ReturnStartup();
        for (int i = 0; i < 4; i++) assertTrue(startup.waiting(false));
        assertFalse(startup.waiting(true));
        startup = new ReturnStartup();
        for (int i = 0; i < ReturnStartup.LIMIT; i++) assertTrue(startup.waiting(false));
        assertFalse(startup.waiting(false));
    }

    @Test void frozenCargoKeepsCountsAndUsesGameTimeForIdle() {
        CargoSnapshot cargo = new CargoSnapshot(Map.of("iron", 128L, "coal", 33L), Map.of("iron", 2L),
                Map.of("water", 3500L), 17, 100, true);
        cargo.readings(200, 6000, true, (a, b) -> a.equals("iron"));
        assertEquals(161, cargo.items("", false)); assertEquals(2, cargo.items("", true));
        assertEquals(33, cargo.items("coal", false)); assertEquals(3500, cargo.fluidMb(""));
        assertEquals(117, cargo.idleTicks()); assertEquals(6000, cargo.dayTime());
        assertTrue(cargo.linkPowered("iron", "coal")); assertTrue(cargo.stopPowered());
        assertFalse(cargo.docked()); assertEquals(0, cargo.seated());
        cargo.readings(100, 6000, false, (a, b) -> false); assertEquals(17, cargo.idleTicks());
    }

    @Test void missingLegacyCargoCannotSatisfyLessThanOrIdleButTimedFallbackCan() {
        Stop stop = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Items(StopCondition.Op.LESS, 10, false, "iron")),
                List.of(new StopCondition.Idle(1))));
        assertFalse(StopRules.evaluate(stop, 1000, CargoSnapshot.unavailable(0)).go());
        assertTrue(StopRules.evaluate(Stop.DEFAULT, 200, CargoSnapshot.unavailable(0)).go());
    }

    @Test void unseenConditionsCombineFrozenCountsWithLiveClockLinksAndPowerAndPhysicalFallbacks() {
        CargoSnapshot cargo = new CargoSnapshot(Map.of("iron", 128L), Map.of("iron", 2L), Map.of("water", 8000L), 5, 100, true);
        Stop counts = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Wait(1),
                new StopCondition.Items(StopCondition.Op.EQUAL, 2, true, "iron"),
                new StopCondition.Fluid(StopCondition.Op.GREATER, 7, "water"), new StopCondition.Idle(1))));
        Stop signals = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Link("a", "b", true),
                new StopCondition.TimeOfDay(12, 0, 0), new StopCondition.Powered())));
        cargo.readings(120, 5999, false, (a, b) -> false);
        assertTrue(StopRules.evaluate(counts, 20, cargo).go());
        assertFalse(StopRules.evaluate(signals, 20, cargo).go());
        cargo.readings(120, 6000, true, (a, b) -> true);
        assertTrue(StopRules.evaluate(counts, 20, cargo).go());
        assertTrue(StopRules.evaluate(signals, 20, cargo).go());
        cargo.readings(140, 6000, false, (a, b) -> true);
        assertFalse(StopRules.evaluate(signals, 40, cargo).go(), "world power remains a live reading");
        assertTrue(StopRules.evaluate(Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Players(0, false)))), 0, cargo).go());
        Stop physical = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Players(1, true)), List.of(new StopCondition.Docked())))
                .withLeaveAfter(2);
        assertFalse(StopRules.evaluate(physical, 39, cargo).go());
        assertTrue(StopRules.evaluate(physical, 40, cargo).timedOut(), "a timed alternative can release an unseen docking wait");
    }

    @Test void latchedCargoAndRemainingSequentialWaitSurviveReconstruction() {
        Stop stop = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Items(StopCondition.Op.GREATER, 10, false, "iron"),
                new StopCondition.Wait(10))));
        CargoSnapshot full = new CargoSnapshot(Map.of("iron", 20L), Map.of(), Map.of(), 0, 0, true);
        StopRules.Progress progress = new StopRules.Progress(stop, 100);
        assertFalse(progress.tick(100, full).go());
        progress.tick(180, StopRules.EMPTY);
        progress = StopRules.Progress.restore(stop, progress.save());
        assertFalse(progress.tick(299, StopRules.EMPTY).go());
        assertTrue(progress.tick(300, StopRules.EMPTY).go(), "latched cargo must not be required again");
    }

    @Test void syntheticMotionNeverRelearnsItsOwnPlantOrRestartsAStuckVehicle() {
        Pathway p = line(); PlantModel plant = new PlantModel(8, 1, 0.2, 100, 10, 0);
        RouteRunner r = new RouteRunner(p, plant);
        r.restoreTravel(new RouteRunner.Saved(RouteRunner.State.STUCK, "no power", 12, -1, -1, 0, -1, false, 0, -1), null, 10);
        for (int i = 11; i < 500; i++) {
            var c = r.tickUnseen(sensors(i, 12, 0, false, Double.POSITIVE_INFINITY));
            assertEquals(0, c.throttle()); assertEquals(1, c.brakeLevel());
        }
        assertEquals(RouteRunner.State.STUCK, r.state()); assertEquals(100, plant.samples()); assertEquals(10, plant.brakeSamples());
    }

    @Test void returnPreservesWaitingPausedManualAndFinishedIntent() {
        Pathway p = line().update(2, n -> n.withStop(Stop.DEFAULT));
        for (RouteRunner.State state : List.of(RouteRunner.State.WAITING, RouteRunner.State.PAUSED,
                RouteRunner.State.MANUAL, RouteRunner.State.FINISHED, RouteRunner.State.LOST)) {
            RouteRunner r = new RouteRunner(p, new PlantModel(128));
            r.restoreTravel(new RouteRunner.Saved(state, "intent", 40, 0, 2, 100, -1, false, 2, 20), null, 190);
            assertEquals(state, r.state()); assertEquals(100, r.waitingSince());
            assertEquals(40, r.progress()); assertEquals(2, r.laps());
            if (state == RouteRunner.State.WAITING) {
                assertEquals(2, r.waitingAt()); assertEquals(1, r.tickUnseen(sensors(190, 40, 0, false, Double.POSITIVE_INFINITY)).brakeLevel());
            } else if (!state.drives()) assertFalse(r.tickUnseen(sensors(190, 40, 0, false, Double.POSITIVE_INFINITY)).drive());
        }
    }

    private static PathNode lineNode(int id, Stop stop) { return new PathNode(id, 40, 64, 0, "Quarry", 0, Corner.SMOOTH, stop); }

    @Test void driverBrakesAtAnUnseenStopWaitsAndLeavesItOnceAfterAHandBack() {
        Pathway p = line().update(2, n -> n.withStop(Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Wait(2))))));
        RouteRunner r = new RouteRunner(p, new PlantModel(8, 1, 0.2, 100, 10, 0));
        r.start(0, true); double s = 0, speed = 0; long arrival = -1; int departures = 0;
        StopRules.Progress wait = null; boolean returned = false;
        for (int tick = 1; tick < 4000; tick++) {
            boolean depart = false;
            if (r.state() == RouteRunner.State.WAITING) {
                if (wait == null) { wait = new StopRules.Progress(p.node(2).stop(), r.waitingSince()); arrival = tick; }
                if (!returned && tick - arrival == 20) {
                    RouteRunner copy = new RouteRunner(p, r.plant().copy()); copy.restoreTravel(r.save(), r.travel(), tick); r = copy;
                    wait = StopRules.Progress.restore(p.node(2).stop(), wait.save()); returned = true;
                }
                depart = wait.tick(tick, StopRules.EMPTY).go();
            }
            var c = r.tickUnseen(sensors(tick, s, speed, depart, Double.POSITIVE_INFINITY));
            speed = c.drive() ? UnseenTravel.speedAfter(speed, c.throttle(), c.brakeLevel(), r.plant(), 0.05) : 0;
            if (r.state() == RouteRunner.State.WAITING) speed = 0;
            s = UnseenTravel.advance(r.spline(), s, speed, 0.05);
            for (var e : r.drainEvents()) if (e.kind() == RouteRunner.Event.Kind.DEPARTED) departures++;
            if (r.state() == RouteRunner.State.FINISHED) break;
        }
        assertTrue(returned, "never arrived at the stop"); assertEquals(1, departures);
        assertEquals(RouteRunner.State.FINISHED, r.state()); assertTrue(s > 98);
    }

    @Test void trailerSideClearanceAndCentralRoadObstructionHaveDifferentRetryBehaviour() {
        assertTrue(UnseenTravel.onRoad(new double[]{4, 64, -.5, 5, 66, .5}, 0, 0, Math.PI / 2, 5, 15));
        assertFalse(UnseenTravel.onRoad(new double[]{-10, 64, 3, -9, 66, 4}, 0, 0, Math.PI / 2, 5, 15));
        assertTrue(UnseenTravel.intersects(new double[]{0, 0, 0, 3, 4, 5}, new double[]{2, 3, 4, 4, 5, 6}));
        assertFalse(UnseenTravel.intersects(new double[]{0, 0, 0, 3, 4, 5}, new double[]{3, 0, 0, 5, 4, 5}));
    }

    @Test void insertedStopDoesNotChangeTheSavedDestinationAndDeletedStopChoosesTheNext() {
        Pathway p = line().update(2, n -> n.withStop(Stop.DEFAULT)).update(3, n -> n.withStop(Stop.DEFAULT));
        RouteRunner original = new RouteRunner(p, new PlantModel(128));
        original.start(0, true); original.tickUnseen(sensors(1, 10, 0, false, Double.POSITIVE_INFINITY));
        int target = original.nextStopNode(); assertEquals(2, target);
        Pathway edited = p.insertAfter(0, 20, 64, 0).update(4, n -> n.withStop(Stop.DEFAULT));
        RouteRunner restored = new RouteRunner(edited, original.plant().copy());
        restored.restoreTravel(original.save(), original.travel(), 5); restored.restoreTarget(target);
        assertEquals(2, restored.nextStopNode(), "new array index points at the inserted stop");
        restored = new RouteRunner(edited.delete(2), original.plant().copy());
        restored.restoreTravel(original.save(), original.travel(), 5); restored.restoreTarget(target);
        assertEquals(4, restored.nextStopNode(), "deleted destination must choose the next current stop");
    }

    @Test void junctionHandoverDoesNotKeepBrakingForTheOldLinesEnd() {
        Pathway inbound = Pathway.create(UUID.randomUUID(), "Inbound", 3, UUID.randomUUID())
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{50, 64, 0}));
        Pathway outbound = Pathway.create(UUID.randomUUID(), "Outbound", 3, inbound.owner())
                .appendAll(List.of(new double[]{50, 64, 0}, new double[]{150, 64, 0}));
        RouteRunner r = new RouteRunner(inbound, new PlantModel(8, 1, .2, 100, 10, 0));
        r.restoreTravel(new RouteRunner.Saved(RouteRunner.State.APPROACHING, "", 49.5, -1, -1, 0, -1, false, 0, -1), null, 1);
        r.tickUnseen(sensors(2, 49.5, .5, false, Double.POSITIVE_INFINITY));
        assertEquals(RouteRunner.Mode.STOPPING, r.mode());
        r.handOver(outbound); r.sendTo(2);
        var command = r.tickUnseen(sensors(3, 50, .5, false, Double.POSITIVE_INFINITY));
        assertNotEquals(RouteRunner.Mode.STOPPING, r.mode(), "the next station is 100 blocks away");
        assertTrue(r.goalSpeed() > 1 && command.throttle() > .5 / 8, "handover should build speed toward the new plan");
    }
}
