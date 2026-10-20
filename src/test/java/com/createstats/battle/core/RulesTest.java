package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {
    private static SystemRules.Descriptor d(String id, Set<String> tags, boolean prop, boolean source, boolean gov,
                                            boolean kinetic, boolean energy, boolean storage, boolean control) {
        return new SystemRules.Descriptor(id, tags, prop, source, gov, kinetic, energy, storage, control);
    }

    @Test
    void tagsComeFirstInThePlansOrder() {
        assertEquals(BattleSystem.LIFT, SystemRules.classify(d("mymod:gasbag", Set.of("create_stats:battle/lift"),
                false, false, false, false, true, false, false)), "a tagged block beats its FE capability");
        assertEquals(BattleSystem.GUNS, SystemRules.classify(d("x:y", Set.of("create_stats:battle/cargo", "create_stats:battle/guns"),
                false, false, false, false, false, false, false)), "guns outrank cargo");
    }

    @Test
    void fallbacksInOrder() {
        assertEquals(BattleSystem.GUNS, SystemRules.classify(d("createbigcannons:cannon_mount", Set.of(), false, false, false, true, false, true, false)));
        assertEquals(BattleSystem.LIFT, SystemRules.classify(SystemRules.Descriptor.plain("aeronautics:steam_vent")));
        assertEquals(BattleSystem.LIFT, SystemRules.classify(d("aeronautics:white_envelope", Set.of("aeronautics:envelope"), false, false, false, false, false, false, false)));
        assertEquals(BattleSystem.PROPULSION, SystemRules.classify(d("create_stats:rpm_governor", Set.of(), false, false, true, true, false, false, false)));
        assertEquals(BattleSystem.PROPULSION, SystemRules.classify(d("create:water_wheel", Set.of(), false, true, false, true, false, false, false)));
        assertEquals(BattleSystem.POWER, SystemRules.classify(d("create_stats:battery", Set.of(), false, false, false, false, true, true, false)));
        assertEquals(BattleSystem.CONTROL, SystemRules.classify(d("create:seat", Set.of(), false, false, false, false, false, false, true)));
        assertEquals(BattleSystem.CARGO, SystemRules.classify(d("minecraft:chest", Set.of(), false, false, false, false, false, true, false)));
        assertEquals(BattleSystem.HULL, SystemRules.classify(d("create:mechanical_press", Set.of(), false, false, false, true, false, true, false)),
                "kinetic machinery with an inventory is not cargo");
        assertEquals(BattleSystem.HULL, SystemRules.classify(SystemRules.Descriptor.plain("minecraft:oak_planks")));
    }

    @Test
    void aHandEditIsARefitUnlessHitInTheLastMinute() {
        assertTrue(RefitRules.isRefit(5000, -1), "never hit");
        assertTrue(RefitRules.isRefit(5000, 5000 - 1200));
        assertFalse(RefitRules.isRefit(5000, 5000 - 1199));
        assertTrue(RefitRules.banner(8, true));
        assertFalse(RefitRules.banner(7, true));
        assertFalse(RefitRules.banner(40, false), "nobody aboard: nobody to ask");
    }

    @Test
    void headerTakesTheFirstThatApplies() {
        assertEquals(HeaderStatus.Kind.NO_POWER, HeaderStatus.of(false, true, "S:0:0:1", 5, true, 100).kind());
        assertEquals(HeaderStatus.Kind.FIRE, HeaderStatus.of(true, true, "S:0:0:1", 5, true, 100).kind());
        HeaderStatus.Status hit = HeaderStatus.of(true, false, "", 85, true, 100);
        assertEquals(HeaderStatus.Kind.UNDER_FIRE, hit.kind());
        assertEquals(4, hit.number(), "85 ticks reads as 4 s");
        assertEquals(HeaderStatus.Kind.WEAPONS_FREE, HeaderStatus.of(true, false, "", 600, true, 100).kind(), "30 s later");
        HeaderStatus.Status survey = HeaderStatus.of(true, false, "", -1, false, 615);
        assertEquals(HeaderStatus.Kind.SURVEYING, survey.kind());
        assertEquals(61, survey.number());
        assertEquals(HeaderStatus.Kind.ALL_CLEAR, HeaderStatus.of(true, false, "", -1, false, -1).kind());
        assertTrue(HeaderStatus.Kind.FIRE.blinks());
    }

    @Test
    void aFirstHitRingsThreeTimesThenOneRingAtMostEveryFortyTicks() {
        BellRules bell = new BellRules();
        bell.hit(1000);
        int rings = 0;
        long[] at = new long[3];
        for (long t = 1000; t < 1040; t++) {
            if (bell.ringDue(t)) at[rings++] = t;
        }
        assertEquals(3, rings);
        assertEquals(1000, at[0]);
        assertEquals(1008, at[1]);
        assertEquals(1016, at[2]);
        bell.hit(1030);
        assertFalse(bell.ringDue(1030), "14 ticks after the last ring: dropped, not queued");
        bell.hit(1060);
        assertTrue(bell.ringDue(1060), "44 ticks after: one ring");
        assertFalse(bell.ringDue(1068), "one, not three");
        bell.hit(1400);
        int later = 0;
        for (long t = 1400; t < 1420; t++) if (bell.ringDue(t)) later++;
        assertEquals(3, later, "quiet for 10 s again: call to stations");
    }

    @Test
    void comparatorModes() {
        assertEquals(13, ComparatorScale.integrity(2, 100), "98% reads 13");
        assertEquals(0, ComparatorScale.integrity(15, 100), "85% reads 0");
        assertEquals(15, ComparatorScale.integrity(0, 1841));
        assertEquals(15, ComparatorScale.integrity(1, 1841), "one block of 1,841 is under 1%: a whole step not yet lost");
        assertEquals(14, ComparatorScale.integrity(19, 1841), "1.03% lost: one step");
        assertEquals(15, ComparatorScale.underFire(0));
        assertEquals(0, ComparatorScale.underFire(200));
        assertEquals(0, ComparatorScale.underFire(-1));
        assertEquals(15, ComparatorScale.gunsReady(20));
        assertEquals(8, ComparatorScale.marker(9, 17));
        assertEquals(0, ComparatorScale.marker(0, 17));
        assertEquals(0, ComparatorScale.marker(0, 0));
        assertEquals(15, ComparatorScale.bridge(ComparatorScale.MODE_UNDER_FIRE, 50, 100, 10, 0));
    }
}
