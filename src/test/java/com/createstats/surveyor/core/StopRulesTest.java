package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StopRulesTest {
    private static StopRules.Cargo cargo(long items, long mb, int idle, boolean link) {
        return new StopRules.Cargo() {
            @Override
            public long items(String item, boolean stacks) {
                return stacks ? items / 64 : items;
            }

            @Override
            public long fluidMb(String fluid) {
                return mb;
            }

            @Override
            public int idleTicks() {
                return idle;
            }

            @Override
            public boolean linkPowered(String first, String second) {
                return link;
            }
        };
    }

    @Test
    void waitCountsTheSecondsAndShowsThem() {
        Stop stop = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Wait(60))));
        StopRules.Verdict v = StopRules.evaluate(stop, 34 * 20, StopRules.EMPTY);
        assertFalse(v.go());
        assertEquals("34 of 60 s", v.bars().get(0).get(0).now());
        assertEquals(34.0 / 60.0, v.bars().get(0).get(0).fraction(), 1.0e-9);
        assertTrue(StopRules.evaluate(stop, 60 * 20, StopRules.EMPTY).go());
    }

    @Test
    void alternativesAreOrAndConditionsAreAndLikeCreatesSchedule() {
        Stop stop = Stop.DEFAULT.withRules(List.of(
                List.of(new StopCondition.Items(StopCondition.Op.GREATER, 999, false, "")),
                List.of(new StopCondition.Wait(120))));
        assertFalse(StopRules.evaluate(stop, 20, cargo(500, 0, 0, false)).go());
        assertTrue(StopRules.evaluate(stop, 20, cargo(1000, 0, 0, false)).go(), "full");
        assertTrue(StopRules.evaluate(stop, 120 * 20, cargo(10, 0, 0, false)).go(), "or timed");

        Stop both = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Wait(10), new StopCondition.Link("a", "b", true))));
        assertFalse(StopRules.evaluate(both, 400, cargo(0, 0, 0, false)).go());
        assertFalse(StopRules.evaluate(both, 100, cargo(0, 0, 0, true)).go());
        assertTrue(StopRules.evaluate(both, 400, cargo(0, 0, 0, true)).go());
    }

    @Test
    void leaveAnywayEndsAnyWait() {
        Stop stop = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Link("a", "b", true)))).withLeaveAfter(30);
        assertFalse(StopRules.evaluate(stop, 29 * 20, StopRules.EMPTY).go());
        StopRules.Verdict v = StopRules.evaluate(stop, 30 * 20, StopRules.EMPTY);
        assertTrue(v.go());
        assertTrue(v.timedOut(), "and says it was the timeout");
    }

    @Test
    void cargoInactiveAndFluidAndOperators() {
        assertTrue(StopRules.evaluate(Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Idle(5)))), 0, cargo(0, 0, 100, false)).go());
        assertFalse(StopRules.evaluate(Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Idle(5)))), 0, cargo(0, 0, 99, false)).go());
        Stop empty = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Items(StopCondition.Op.LESS, 1, false, ""))));
        assertTrue(StopRules.evaluate(empty, 0, cargo(0, 0, 0, false)).go(), "emptied");
        Stop tank = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Fluid(StopCondition.Op.GREATER, 7, ""))));
        assertTrue(StopRules.evaluate(tank, 0, cargo(0, 8000, 0, false)).go());
        assertFalse(StopRules.evaluate(tank, 0, cargo(0, 7999, 0, false)).go(), "7 buckets and a bit is not over 7");
        Stop stacks = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Items(StopCondition.Op.EQUAL, 2, true, "minecraft:cobblestone"))));
        assertTrue(StopRules.evaluate(stacks, 0, cargo(128, 0, 0, false)).go());
    }

    @Test
    void aStopWithNoRulesIsATouchAndGo() {
        assertTrue(StopRules.evaluate(Stop.DEFAULT.withRules(List.of()), 0, StopRules.EMPTY).go());
    }

    @Test
    void craftedStopsAreCapped() {
        List<List<StopCondition>> many = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) many.add(List.of(new StopCondition.Wait(99999), new StopCondition.Wait(1), new StopCondition.Wait(2),
                new StopCondition.Wait(3), new StopCondition.Wait(4), new StopCondition.Wait(5)));
        Stop s = Stop.DEFAULT.withRules(many).withLeaveAfter(-5).withHornAux(9).sane();
        assertEquals(Stop.MAX_ALTERNATIVES, s.rules().size());
        assertEquals(Stop.MAX_CONDITIONS, s.rules().get(0).size());
        assertEquals(StopCondition.MAX_SECONDS, ((StopCondition.Wait) s.rules().get(0).get(0)).seconds());
        assertEquals(0, s.leaveAfter());
        assertEquals(-1, s.hornAux());
    }
}
