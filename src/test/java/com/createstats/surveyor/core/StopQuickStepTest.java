package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class StopQuickStepTest {
    private static int seconds(Stop s) {
        return ((StopCondition.Wait) s.rules().get(0).get(0)).seconds();
    }

    @Test
    void oneNotchUpFromNothingIsAFiveSecondStop() {
        Stop five = Stop.quickStep(null, 1);
        assertEquals(5, seconds(five));
        assertEquals(10, seconds(Stop.quickStep(five, 1)));
        assertEquals(30, seconds(Stop.quickStep(Stop.quickStep(five, 1), 1)));
        Stop minute = Stop.quickStep(Stop.quickStep(Stop.quickStep(five, 1), 1), 1);
        assertEquals(60, seconds(minute));
        assertEquals(60, seconds(Stop.quickStep(minute, 1)), "a minute is the top");
    }

    @Test
    void downStepsBackAndPastFiveSecondsIsNoStop() {
        Stop minute = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Wait(60))));
        assertEquals(30, seconds(Stop.quickStep(minute, -1)));
        assertNull(Stop.quickStep(Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Wait(5)))), -1));
        assertNull(Stop.quickStep(null, -1), "nothing below nothing");

        Stop twenty = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Wait(20))));
        assertEquals(30, seconds(Stop.quickStep(twenty, 1)));
        assertEquals(10, seconds(Stop.quickStep(twenty, -1)));
    }

    @Test
    void aStopWithRulesOfItsOwnIsNeverTouched() {
        Stop rules = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Items(StopCondition.Op.GREATER, 1000, false, "")),
                List.of(new StopCondition.Wait(120))));
        assertSame(Stop.QUICK_UNCHANGED, Stop.quickStep(rules, 1));
        assertSame(Stop.QUICK_UNCHANGED, Stop.quickStep(rules, -1));
    }

    @Test
    void theStopsOtherSettingsStay() {
        Stop dock = Stop.DEFAULT.withRules(List.of(List.of(new StopCondition.Wait(10)))).withLeaveAfter(300)
                .withDock("minecraft:redstone", "minecraft:lever").withIndicate(true).withHornAux(2);
        Stop up = Stop.quickStep(dock, 1);
        assertEquals(30, seconds(up));
        assertEquals(300, up.leaveAfter());
        assertEquals("minecraft:redstone", up.dockFirst());
        assertEquals(2, up.hornAux());
    }
}
