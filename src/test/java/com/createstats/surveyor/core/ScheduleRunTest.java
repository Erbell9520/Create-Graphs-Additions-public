package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleRunTest {
    static final class Vehicle implements ScheduleRun.Host {
        final Map<String, Integer> stations;
        int waitingAt;
        int driving = -1;
        long arriveAt = Long.MAX_VALUE;
        long now;
        int travel = 60;
        final List<Long> picks = new ArrayList<>();
        boolean paused;

        Vehicle(Map<String, Integer> stations, int waitingAt) {
            this.stations = stations;
            this.waitingAt = waitingAt;
        }

        @Override
        public int pick(String glob) {
            picks.add(now);
            String regex = glob.replace("*", ".*");
            int best = ScheduleRun.NO_MATCH;
            for (Map.Entry<String, Integer> e : stations.entrySet()) if (e.getKey().matches(regex)) best = e.getValue();
            return best;
        }

        @Override
        public int waitingAt() {
            return waitingAt;
        }

        @Override
        public String name(int node) {
            for (Map.Entry<String, Integer> e : stations.entrySet()) if (e.getValue() == node) return e.getKey();
            return "?";
        }

        ScheduleRun.Step tick(ScheduleRun run, StopRules.Cargo cargo) {
            if (driving >= 0 && now >= arriveAt) {
                waitingAt = driving;
                driving = -1;
            }
            ScheduleRun.Step s = run.tick(now, this, cargo);
            if (s.sendTo() >= 0) {
                waitingAt = -1;
                driving = s.sendTo();
                arriveAt = now + travel;
            }
            paused = (paused || s.pause()) && !s.resume();
            now++;
            return s;
        }
    }

    static RouteSchedule.Entry travel(String station, List<List<StopCondition>> conditions) {
        return new RouteSchedule.Entry(new RouteSchedule.TravelTo(station), conditions, List.of());
    }

    static StopRules.Cargo items(long[] count) {
        return new StopRules.Cargo() {
            @Override
            public long items(String item, boolean stacks) {
                return count[0];
            }

            @Override
            public long fluidMb(String fluid) {
                return 0;
            }

            @Override
            public int idleTicks() {
                return 0;
            }

            @Override
            public boolean linkPowered(String first, String second) {
                return false;
            }
        };
    }

    @Test
    void aDelayCountsFromWhenItBecameCurrentNotFromTheArrival() {
        RouteSchedule s = new RouteSchedule(List.of(
                travel("Quarry", List.of(List.of(new StopCondition.Items(StopCondition.Op.GREATER, 10, false, ""), new StopCondition.Wait(5)))),
                travel("Yard", List.of(List.of(new StopCondition.Wait(1))))), true);
        Vehicle v = new Vehicle(Map.of("Quarry", 3, "Yard", 7), 3);
        ScheduleRun run = new ScheduleRun(s);
        long[] cargo = {0};
        long leftAt = -1;
        for (int t = 0; t < 400 && leftAt < 0; t++) {
            if (t == 100) cargo[0] = 50;
            ScheduleRun.Step step = v.tick(run, items(cargo));
            if (step.sendTo() == 7) leftAt = t;
        }
        assertTrue(leftAt >= 200 && leftAt <= 203, "five seconds after the cargo, not after the arrival: left at " + leftAt);
    }

    @Test
    void aMomentOfCargoDuringTheDelayDoesNotCount() {
        RouteSchedule s = new RouteSchedule(List.of(
                travel("Quarry", List.of(List.of(new StopCondition.Wait(5), new StopCondition.Items(StopCondition.Op.GREATER, 10, false, "")))),
                travel("Yard", List.of())), true);
        Vehicle v = new Vehicle(Map.of("Quarry", 3, "Yard", 7), 3);
        ScheduleRun run = new ScheduleRun(s);
        long[] cargo = {0};
        for (int t = 0; t < 400; t++) {
            cargo[0] = t >= 20 && t < 80 ? 50 : 0;
            ScheduleRun.Step step = v.tick(run, items(cargo));
            assertEquals(-1, step.sendTo(), "still at the quarry at " + t);
        }
        assertTrue(run.status().contains("0 > 10"), run.status());
    }

    @Test
    void theColumnDoneFirstSendsTheVehicle() {
        RouteSchedule s = new RouteSchedule(List.of(
                travel("Quarry", List.of(List.of(new StopCondition.Items(StopCondition.Op.GREATER, 999, false, "")),
                        List.of(new StopCondition.Wait(3)))),
                travel("Yard", List.of())), true);
        Vehicle v = new Vehicle(Map.of("Quarry", 3, "Yard", 7), 3);
        ScheduleRun run = new ScheduleRun(s);
        long[] cargo = {0};
        long leftAt = -1;
        for (int t = 0; t < 200 && leftAt < 0; t++) if (v.tick(run, items(cargo)).sendTo() == 7) leftAt = t;
        assertTrue(leftAt >= 60 && leftAt <= 63, "three seconds: " + leftAt);
    }

    @Test
    void aStationThatIsNotThereIsTriedAgainEvery40TicksWithoutAdvancing() {
        RouteSchedule s = new RouteSchedule(List.of(travel("Smeltery", List.of(List.of(new StopCondition.Wait(1)))),
                travel("Yard", List.of())), true);
        Vehicle v = new Vehicle(new java.util.HashMap<>(Map.of("Yard", 7)), -1);
        ScheduleRun run = new ScheduleRun(s);
        boolean held = false;
        for (int t = 0; t < 130; t++) held |= v.tick(run, StopRules.EMPTY).pause();
        assertEquals(List.of(0L, 41L, 82L, 123L), v.picks, "Create's cooldown: again 41 ticks later");
        assertEquals(0, run.entry(), "the entry does not advance");
        assertTrue(held && v.paused, "a vehicle with nowhere to go is held");
        assertTrue(run.status().contains("Smeltery"), run.status());
        v.stations.put("Smeltery", 5);
        ScheduleRun.Step go = null;
        for (int t = 0; t < 45 && (go == null || go.sendTo() < 0); t++) go = v.tick(run, StopRules.EMPTY);
        assertEquals(5, go.sendTo(), "found on the next try");
        assertTrue(go.resume() && !v.paused, "and let go");
    }

    @Test
    void titleAndSpeedLimitApplyAndAdvanceAtOnce() {
        RouteSchedule s = new RouteSchedule(List.of(new RouteSchedule.Entry(new RouteSchedule.Rename("Ore run"), List.of(), List.of()),
                new RouteSchedule.Entry(new RouteSchedule.Throttle(40), List.of(), List.of()),
                travel("Yard", List.of())), true);
        Vehicle v = new Vehicle(Map.of("Yard", 7), -1);
        ScheduleRun run = new ScheduleRun(s);
        ScheduleRun.Step last = null;
        for (int t = 0; t < 3; t++) last = v.tick(run, StopRules.EMPTY);
        assertEquals("Ore run", run.title());
        assertEquals(40, run.throttle());
        assertEquals(7, last.sendTo(), "a tick each, then away");
    }

    @Test
    void aScheduleThatIsNotCyclicEndsCompletedAndPausedAtItsLastStation() {
        RouteSchedule s = new RouteSchedule(List.of(travel("Quarry", List.of(List.of(new StopCondition.Wait(1)))),
                travel("Yard", List.of(List.of(new StopCondition.Wait(1))))), false);
        Vehicle v = new Vehicle(Map.of("Quarry", 3, "Yard", 7), 3);
        ScheduleRun run = new ScheduleRun(s);
        boolean paused = false;
        for (int t = 0; t < 400 && !paused; t++) paused = v.tick(run, StopRules.EMPTY).pause();
        assertTrue(paused && run.completed(), "completed and paused");
        assertEquals(7, v.waitingAt(), "still at the Yard");
        assertEquals("schedule completed", run.status());
    }

    @Test
    void aCyclicScheduleStartsOverAndAStationAlreadyReachedIsArrivedAtOnTheSpot() {
        RouteSchedule s = new RouteSchedule(List.of(travel("Quarry", List.of(List.of(new StopCondition.Wait(1)))),
                travel("Yard", List.of(List.of(new StopCondition.Wait(1))))), true);
        Vehicle v = new Vehicle(Map.of("Quarry", 3, "Yard", 7), 3);
        ScheduleRun run = new ScheduleRun(s);
        List<Integer> sent = new ArrayList<>();
        for (int t = 0; t < 600; t++) {
            int to = v.tick(run, StopRules.EMPTY).sendTo();
            if (to >= 0) sent.add(to);
        }
        assertTrue(sent.size() >= 4, "round and round: " + sent);
        assertEquals(7, sent.get(0), "at the Quarry already: it waits there first, then the Yard");
        for (int i = 1; i < sent.size(); i++) assertTrue(sent.get(i) != sent.get(i - 1).intValue(), "alternating: " + sent);
    }

    @Test
    void whatARoadVehicleCannotDoIsSkippedWithItsReason() {
        RouteSchedule s = new RouteSchedule(List.of(
                new RouteSchedule.Entry(new RouteSchedule.Unsupported("create:package_delivery", "a road stop has no postbox"), List.of(), List.of()),
                travel("Yard", List.of())), true);
        Vehicle v = new Vehicle(Map.of("Yard", 7), -1);
        ScheduleRun run = new ScheduleRun(s);
        ScheduleRun.Step first = v.tick(run, StopRules.EMPTY);
        assertTrue(first.status().contains("postbox"), first.status());
        assertEquals(7, v.tick(run, StopRules.EMPTY).sendTo());
    }

    @Test
    void theTimeOfDayIsCreatesOwnArithmetic() {
        StopCondition.TimeOfDay eight = new StopCondition.TimeOfDay(8, 0, 0);
        assertEquals(2000, eight.targetTicks());
        assertFalse(eight.doneAt(1999));
        assertTrue(eight.doneAt(2000) && eight.doneAt(2040) && eight.doneAt(24000 + 2020));
        assertFalse(eight.doneAt(2041));

        StopCondition.TimeOfDay every2 = new StopCondition.TimeOfDay(8, 0, 5);
        assertTrue(every2.doneAt(6000) && every2.doneAt(18030));
        assertEquals(1000, every2.ticksUntil(5000));

        assertEquals(6500, new StopCondition.TimeOfDay(12, 30, 0).targetTicks());
    }
}
